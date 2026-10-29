package com.nowit.app.source

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.nowit.app.data.SourceMethod
import java.time.LocalDateTime
import java.time.ZoneId

data class SourceApp(
    val name: String?,
    val packageName: String?,
    val method: SourceMethod,
)

/**
 * Works out which app a screenshot was taken in: first from the file name
 * (Samsung, Xiaomi and others append the app name or package), then from
 * UsageStatsManager by finding the app that was in the foreground at the
 * screenshot's timestamp.
 */
class SourceAppDetector(private val context: Context) {
    private val pm = context.packageManager

    fun detect(fileName: String?, takenAt: Long): SourceApp {
        fileName?.let { fromFileName(it) }?.let { return it }
        if (hasUsageAccess(context)) fromUsageStats(takenAt)?.let { return it }
        return SourceApp(null, null, SourceMethod.UNKNOWN)
    }

    /** e.g. "Screenshot_20241029-123456_WhatsApp.jpg" or "Screenshot_2024-10-29-12-34-56-123_com.whatsapp.jpg". */
    fun fromFileName(fileName: String): SourceApp? {
        val base = fileName.substringBeforeLast('.')
        if (!base.contains("screenshot", ignoreCase = true)) return null
        val tokens = base
            .replace(Regex("(?i)screenshots?"), "")
            .split('_', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.all { c -> c.isDigit() || c == '-' || c == '.' } }
        if (tokens.isEmpty()) return null
        val candidate = tokens.joinToString(" ")

        // A package name, as some phones write it.
        if (candidate.contains('.') && !candidate.contains(' ')) {
            appLabel(candidate)?.let { return SourceApp(it, candidate, SourceMethod.FILE_NAME) }
        }
        // An app label: match it against installed apps.
        val wanted = normalise(candidate)
        val match = launchableApps().firstOrNull { (label, _) -> normalise(label) == wanted }
            ?: launchableApps().firstOrNull { (label, _) -> normalise(label).startsWith(wanted) || wanted.startsWith(normalise(label)) }
        if (match == null && wanted.length < 3) return null
        return SourceApp(match?.first ?: candidate, match?.second, SourceMethod.FILE_NAME)
    }

    fun fromUsageStats(takenAt: Long): SourceApp? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val ignored = ignoredPackages()
        val events = usm.queryEvents(takenAt - 30 * 60 * 1000L, takenAt + 2_000L)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > takenAt + 2_000L) break
            @Suppress("DEPRECATION")
            val resumed = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (resumed && event.packageName !in ignored) lastPackage = event.packageName
        }
        val pkg = lastPackage ?: return null
        return SourceApp(appLabel(pkg) ?: pkg, pkg, SourceMethod.USAGE_STATS)
    }

    private fun ignoredPackages(): Set<String> {
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        ).map { it.activityInfo.packageName }
        return (launchers + context.packageName + "com.android.systemui" + "android").toSet()
    }

    private fun launchableApps(): List<Pair<String, String>> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }

    private fun appLabel(pkg: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun normalise(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        private val FILE_TIMESTAMP =
            Regex("""(\d{4})-?(\d{2})-?(\d{2})[-_ ]?(\d{2})[-.]?(\d{2})[-.]?(\d{2})""")

        /** Reads the capture time out of names like "Screenshot_20241029-123456". */
        fun timestampFromFileName(fileName: String): Long? {
            val m = FILE_TIMESTAMP.find(fileName) ?: return null
            val (y, mo, d, h, mi, s) = m.destructured
            return try {
                LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }

        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
