@file:OptIn(ExperimentalMaterial3Api::class)

package com.nowit.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nowit.app.data.SourceMethod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun EditorScreen(vm: MainViewModel, state: EditorState) {
    val r = state.reminder
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val due = Instant.ofEpochMilli(r.dueAt).atZone(zone)

    fun setDue(date: LocalDate, time: LocalTime) =
        vm.updateEditor { it.copy(dueAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (r.id == 0L) "New reminder" else "Edit reminder") },
                navigationIcon = {
                    IconButton(onClick = { vm.go(Screen.Home) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (r.id != 0L) {
                        IconButton(onClick = { vm.delete(r.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.warning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = r.title,
                onValueChange = { v -> vm.updateEditor { it.copy(title = v) } },
                label = { Text("Reminder") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("When", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> setDue(LocalDate.of(y, m + 1, d), due.toLocalTime()) },
                        due.year, due.monthValue - 1, due.dayOfMonth,
                    ).show()
                }) { Text(formatDate(r.dueAt)) }
                OutlinedButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, min -> setDue(due.toLocalDate(), LocalTime.of(h, min)) },
                        due.hour, due.minute, DateFormat.is24HourFormat(context),
                    ).show()
                }) { Text(formatTime(r.dueAt)) }
            }
            if (state.dateOptions.isNotEmpty()) {
                ChipRow(state.dateOptions, selected = { it.millis == r.dueAt }, label = { "${it.text} → ${formatDateTime(it.millis)}" }) { opt ->
                    vm.updateEditor { it.copy(dueAt = opt.millis) }
                }
            }

            OutlinedTextField(
                value = r.address.orEmpty(),
                onValueChange = { v -> vm.updateEditor { it.copy(address = v.ifBlank { null }) } },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.addressOptions.size > 1) {
                ChipRow(state.addressOptions, selected = { it == r.address }, label = { it }) { a ->
                    vm.updateEditor { it.copy(address = a) }
                }
            }

            OutlinedTextField(
                value = r.phone.orEmpty(),
                onValueChange = { v -> vm.updateEditor { it.copy(phone = v.ifBlank { null }) } },
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.phoneOptions.size > 1) {
                ChipRow(state.phoneOptions, selected = { it == r.phone }, label = { it }) { p ->
                    vm.updateEditor { it.copy(phone = p) }
                }
            }

            OutlinedTextField(
                value = r.notes,
                onValueChange = { v -> vm.updateEditor { it.copy(notes = v) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            OutlinedTextField(
                value = r.sourceAppName.orEmpty(),
                onValueChange = vm::setSourceApp,
                label = { Text("Source app") },
                supportingText = {
                    Text(
                        when (r.sourceMethod) {
                            SourceMethod.FILE_NAME -> "Detected from the file name"
                            SourceMethod.USAGE_STATS -> "Detected from the app in use at the screenshot time"
                            SourceMethod.UNKNOWN -> "Couldn't detect the app"
                            SourceMethod.MANUAL -> "Entered by hand"
                        } + (r.sourcePackage?.let { " · $it" } ?: ""),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (r.id == 0L) "Save reminder" else "Save changes")
            }

            r.screenshotPath?.let { path -> ScreenshotPreview(path) }
            r.ocrText?.takeIf { it.isNotBlank() }?.let { text ->
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide recognised text" else "Show recognised text")
                }
                if (expanded) {
                    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            FilterChip(selected = selected(opt), onClick = { onPick(opt) }, label = { Text(label(opt)) })
        }
    }
}

@Composable
private fun ScreenshotPreview(path: String) {
    val bitmap = remember(path) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outHeight / sample > 1200) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Screenshot",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
    )
}
