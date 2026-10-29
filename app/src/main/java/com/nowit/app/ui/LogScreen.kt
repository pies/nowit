@file:OptIn(ExperimentalMaterial3Api::class)

package com.nowit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The raw event log that habit learning will use later. */
@Composable
fun LogScreen(vm: MainViewModel) {
    val events by vm.events.collectAsStateWithLifecycle()
    val titles by vm.reminders.collectAsStateWithLifecycle()
    val titleById = titles.associate { it.id to it.title }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity log") },
                navigationIcon = {
                    IconButton(onClick = { vm.go(Screen.Home) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (events.isEmpty()) item { Text("Nothing logged yet.") }
            items(events, key = { it.id }) { e ->
                Column {
                    Text(
                        "${e.type.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                            (titleById[e.reminderId] ?: "reminder #${e.reminderId}"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        listOfNotNull(
                            formatDateTime(e.at),
                            e.offsetFromDueMs?.let { "${formatDuration(it)} vs due" },
                            e.snoozeMs?.let { "snoozed ${it / 60_000} min" },
                            e.origin,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
