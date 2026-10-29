package com.nowit.app.ocr

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nowit.app.source.SourceApp
import com.nowit.app.source.SourceAppDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

data class DateOption(
    val millis: Long,
    /** The text it was read from, e.g. "Friday at 3pm". */
    val text: String,
)

data class ScreenshotDraft(
    val title: String,
    val text: String,
    val dates: List<DateOption>,
    val addresses: List<String>,
    val phones: List<String>,
    val source: SourceApp,
    val screenshotPath: String,
    val fileName: String?,
    val takenAt: Long,
    /** Set when entity extraction could not run (e.g. its model failed to download). */
    val warning: String? = null,
)

/** Shared screenshot → OCR text → dates, times, addresses and phone numbers, all on the device. */
class ScreenshotAnalyzer(private val context: Context) {

    suspend fun analyze(uri: Uri): ScreenshotDraft = withContext(Dispatchers.IO) {
        val sharedAt = System.currentTimeMillis()
        val meta = readMetadata(uri)
        val takenAt = meta.fileName?.let { SourceAppDetector.timestampFromFileName(it) }
            ?: meta.dateTaken
            ?: sharedAt
        val copy = copyToAppStorage(uri, meta.fileName)
        val source = SourceAppDetector(context).detect(meta.fileName, takenAt)

        val text = recognizeText(copy)
        var warning: String? = null
        val annotations = try {
            extractEntities(text, takenAt)
        } catch (e: Exception) {
            warning = "Couldn't look for dates and places: ${e.message ?: e.javaClass.simpleName}"
            Extracted(emptyList(), emptyList(), emptyList())
        }

        ScreenshotDraft(
            title = suggestTitle(text, annotations.dates.firstOrNull()?.text),
            text = text,
            dates = annotations.dates,
            addresses = annotations.addresses,
            phones = annotations.phones,
            source = source,
            screenshotPath = copy.absolutePath,
            fileName = meta.fileName,
            takenAt = takenAt,
            warning = warning,
        )
    }

    private data class Metadata(val fileName: String?, val dateTaken: Long?)

    private fun readMetadata(uri: Uri): Metadata {
        var name: String? = null
        var taken: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        // Only MediaStore URIs have these columns; other providers may throw.
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    taken = c.getLong(0).takeIf { it > 0 } ?: c.getLong(1).takeIf { it > 0 }?.times(1000)
                }
            }
        }
        if (name == null) name = uri.lastPathSegment?.substringAfterLast('/')
        return Metadata(name, taken)
    }

    private fun copyToAppStorage(uri: Uri, fileName: String?): File {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val ext = fileName?.substringAfterLast('.', "")?.takeIf { it.length in 1..4 } ?: "png"
        val file = File(dir, "${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    private suspend fun recognizeText(file: File): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(image).await()
            // Skip the status bar (clock, battery) at the very top of the screenshot,
            // otherwise its clock reads as a reminder time.
            val statusBarBottom = image.height * 0.035f
            return result.textBlocks
                .filter { (it.boundingBox?.bottom ?: Int.MAX_VALUE) > statusBarBottom }
                .joinToString("\n") { it.text }
        } finally {
            recognizer.close()
        }
    }

    private data class Extracted(
        val dates: List<DateOption>,
        val addresses: List<String>,
        val phones: List<String>,
    )

    private suspend fun extractEntities(text: String, referenceTime: Long): Extracted {
        if (text.isBlank()) return Extracted(emptyList(), emptyList(), emptyList())
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build(),
        )
        try {
            // The model (a few MB) is downloaded once, then everything runs offline.
            extractor.downloadModelIfNeeded().await()
            val params = EntityExtractionParams.Builder(text)
                .setReferenceTime(referenceTime)
                .setReferenceTimeZone(TimeZone.getDefault())
                .setPreferredLocale(Locale.getDefault())
                .build()
            val annotations = extractor.annotate(params).await()

            val dates = mutableListOf<DateOption>()
            val addresses = mutableListOf<String>()
            val phones = mutableListOf<String>()
            for (a in annotations) {
                for (e in a.entities) {
                    when (e.type) {
                        Entity.TYPE_DATE_TIME -> e.asDateTimeEntity()?.let { dt ->
                            dates += DateOption(withDefaultTime(dt), a.annotatedText)
                        }
                        Entity.TYPE_ADDRESS -> addresses += a.annotatedText.replace('\n', ' ')
                        Entity.TYPE_PHONE -> phones += a.annotatedText
                    }
                }
            }
            // Future dates first, soonest first; then past ones.
            val now = System.currentTimeMillis()
            val sorted = dates.distinctBy { it.millis }
                .sortedWith(compareBy<DateOption> { it.millis < now }.thenBy { it.millis })
            return Extracted(sorted, addresses.distinct(), phones.distinct())
        } finally {
            extractor.close()
        }
    }

    /** A date without a time ("Friday") becomes 9:00 that day. */
    private fun withDefaultTime(dt: DateTimeEntity): Long {
        val dayOnly = when (dt.dateTimeGranularity) {
            DateTimeEntity.GRANULARITY_DAY,
            DateTimeEntity.GRANULARITY_WEEK,
            DateTimeEntity.GRANULARITY_MONTH,
            DateTimeEntity.GRANULARITY_YEAR -> true
            else -> false
        }
        if (!dayOnly) return dt.timestampMillis
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(dt.timestampMillis).atZone(zone).toLocalDate()
            .atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
    }

    private fun suggestTitle(text: String, dateText: String?): String {
        val lines = text.lines().map { it.trim() }.filter { line -> line.count { it.isLetter() } >= 3 }
        val withDate = dateText?.let { d -> lines.firstOrNull { it.contains(d) } }
            ?.replace(dateText, "")?.trim(' ', ',', '-', ':', '.')
            ?.takeIf { line -> line.count { it.isLetter() } >= 4 }
        val title = withDate
            ?: lines.take(6).maxByOrNull { it.length }
            ?: "Reminder"
        return title.take(80)
    }
}
