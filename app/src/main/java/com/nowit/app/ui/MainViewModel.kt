package com.nowit.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nowit.app.data.Reminder
import com.nowit.app.data.ReminderRepository
import com.nowit.app.data.SourceMethod
import com.nowit.app.notify.Notifications
import com.nowit.app.ocr.DateOption
import com.nowit.app.ocr.ScreenshotAnalyzer
import com.nowit.app.source.SourceAppDetector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything the editor shows: the reminder being edited plus what was detected. */
data class EditorState(
    val reminder: Reminder,
    val dateOptions: List<DateOption> = emptyList(),
    val addressOptions: List<String> = emptyList(),
    val phoneOptions: List<String> = emptyList(),
    val warning: String? = null,
)

sealed interface Screen {
    data object Home : Screen
    data object Log : Screen
    data class Editor(val state: EditorState) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReminderRepository(app)

    val reminders = repo.observeReminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = repo.observeEvents().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notificationsAllowed by mutableStateOf(true)
        private set
    var usageAccess by mutableStateOf(true)
        private set

    fun refreshPermissions() {
        notificationsAllowed = Notifications.canPost(getApplication())
        usageAccess = SourceAppDetector.hasUsageAccess(getApplication())
    }

    fun go(to: Screen) {
        screen = to
    }

    fun newReminder() {
        screen = Screen.Editor(EditorState(Reminder(title = "", dueAt = defaultDue())))
    }

    fun openReminder(id: Long) = viewModelScope.launch {
        repo.get(id)?.let { screen = Screen.Editor(EditorState(it)) }
    }

    fun analyzeScreenshot(uri: Uri) {
        analyzing = true
        error = null
        viewModelScope.launch {
            try {
                val d = ScreenshotAnalyzer(getApplication()).analyze(uri)
                val due = d.dates.firstOrNull { it.millis > System.currentTimeMillis() }?.millis
                    ?: d.dates.firstOrNull()?.millis
                val reminder = Reminder(
                    title = d.title,
                    dueAt = due ?: defaultDue(),
                    address = d.addresses.firstOrNull(),
                    phone = d.phones.firstOrNull(),
                    sourceAppName = d.source.name,
                    sourcePackage = d.source.packageName,
                    sourceMethod = d.source.method,
                    screenshotPath = d.screenshotPath,
                    screenshotFileName = d.fileName,
                    screenshotTakenAt = d.takenAt,
                    ocrText = d.text,
                    suggestedDueAt = due,
                )
                screen = Screen.Editor(
                    EditorState(
                        reminder = reminder,
                        dateOptions = d.dates,
                        addressOptions = d.addresses,
                        phoneOptions = d.phones,
                        warning = d.warning ?: if (due == null) "No date found — pick one." else null,
                    ),
                )
            } catch (e: Exception) {
                error = "Couldn't read the screenshot: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                analyzing = false
            }
        }
    }

    fun updateEditor(transform: (Reminder) -> Reminder) {
        val current = screen as? Screen.Editor ?: return
        screen = current.copy(state = current.state.copy(reminder = transform(current.state.reminder)))
    }

    fun setSourceApp(name: String) = updateEditor {
        it.copy(sourceAppName = name.ifBlank { null }, sourcePackage = null, sourceMethod = SourceMethod.MANUAL)
    }

    fun save() {
        val current = screen as? Screen.Editor ?: return
        val r = current.state.reminder
        viewModelScope.launch {
            repo.save(r.copy(title = r.title.ifBlank { "Reminder" }))
            screen = Screen.Home
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.delete(id)
        screen = Screen.Home
    }

    fun complete(id: Long) = viewModelScope.launch { repo.complete(id, origin = "app") }
    fun dismiss(id: Long) = viewModelScope.launch { repo.dismiss(id, origin = "app") }
    fun snooze(id: Long) = viewModelScope.launch { repo.snooze(id, Notifications.SNOOZE_MS, origin = "app") }

    /** Tomorrow at 9:00. */
    private fun defaultDue(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
    }
}
