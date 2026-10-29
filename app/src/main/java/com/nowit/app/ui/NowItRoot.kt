@file:OptIn(ExperimentalMaterial3Api::class)

package com.nowit.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nowit.app.data.Reminder
import com.nowit.app.data.ReminderStatus

@Composable
fun NowItRoot(vm: MainViewModel, requestNotifications: () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            when (val s = vm.screen) {
                Screen.Home -> HomeScreen(vm, requestNotifications)
                Screen.Log -> {
                    BackHandler { vm.go(Screen.Home) }
                    LogScreen(vm)
                }
                is Screen.Editor -> {
                    BackHandler { vm.go(Screen.Home) }
                    EditorScreen(vm, s.state)
                }
            }
            if (vm.analyzing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Reading screenshot…")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, requestNotifications: () -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NowIt") },
                actions = {
                    IconButton(onClick = { vm.go(Screen.Log) }) {
                        Icon(Icons.Default.List, contentDescription = "Activity log")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::newReminder) {
                Icon(Icons.Default.Add, contentDescription = "New reminder")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.error?.let { msg ->
                item { Notice(msg, "OK") { vm.error = null } }
            }
            if (!vm.notificationsAllowed) {
                item {
                    Notice("Notifications are off, so reminders can't alert you.", "Allow") {
                        requestNotifications()
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            }
            if (!vm.usageAccess) {
                item {
                    Notice(
                        "Grant Usage access so NowIt can tell which app a screenshot came from " +
                            "when the file name doesn't say.",
                        "Open settings",
                    ) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            }
            if (reminders.isEmpty()) {
                item {
                    Text(
                        "No reminders yet. Take a screenshot, tap Share and choose NowIt — " +
                            "or tap + to add one by hand.",
                        Modifier.padding(top = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(reminders, key = { it.id }) { r -> ReminderCard(r, vm) }
        }
    }
}

@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(text)
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun ReminderCard(r: Reminder, vm: MainViewModel) {
    val pending = r.status == ReminderStatus.PENDING
    val overdue = pending && r.dueAt < System.currentTimeMillis()
    Card(
        Modifier.fillMaxWidth().clickable { vm.openReminder(r.id) },
        colors = if (pending) CardDefaults.cardColors() else
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val status = when (r.status) {
                ReminderStatus.PENDING -> if (overdue) "Overdue" else null
                ReminderStatus.COMPLETED -> "Done"
                ReminderStatus.DISMISSED -> "Dismissed"
            }
            Text(
                listOfNotNull(formatDateTime(r.dueAt), status, r.sourceAppName?.let { "from $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            r.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (pending) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.complete(r.id) }) { Text("Done") }
                    TextButton(onClick = { vm.snooze(r.id) }) { Text("Snooze") }
                    TextButton(onClick = { vm.dismiss(r.id) }) { Text("Dismiss") }
                }
            }
        }
    }
}
