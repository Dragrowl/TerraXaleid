// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package io.terramap.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.terramap.android.R
import io.terramap.android.data.Backups
import io.terramap.android.data.ItemResult
import io.terramap.android.data.ItemStatus
import io.terramap.android.data.MapBackupRow

/**
 * Restore entry: every map on the current root with the backups kept for it.
 *
 * Rows are multi-selectable so one pass can restore several characters, or wipe several maps back to
 * "completely unexplored" at once.
 */
@Composable
fun RestoreScreen(
    rows: List<MapBackupRow>,
    selected: Set<String>,
    loading: Boolean,
    busy: String?,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onRestore: () -> Unit,
    onDeleteBackups: () -> Unit,
    onDeleteMapsConfirmed: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDeleteMaps by remember { mutableStateOf(false) }
    val restoreEnabled = busy == null && rows.any { it.mapPath in selected && it.hasBackup }
    val deleteBackupEnabled = busy == null && rows.any { it.mapPath in selected && it.hasBackup }
    val deleteMapEnabled = busy == null && selected.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = busy == null) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.worlds_reload))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.restore_selected, selected.size),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSelectAll, enabled = busy == null) {
                        Text(stringResource(R.string.worlds_select_all), maxLines = 1)
                    }
                    TextButton(onClick = onSelectNone, enabled = busy == null) {
                        Text(stringResource(R.string.worlds_select_none), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRestore,
                    enabled = restoreEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_action), maxLines = 1)
                }
                // Side by side only with the smaller label style: at the default button text size
                // 「删除地图（全黑）」 was clipped to 「删除地图（全」 in a half-width slot.
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onDeleteBackups,
                        enabled = deleteBackupEnabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.restore_delete_backups),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                    TextButton(
                        onClick = { confirmDeleteMaps = true },
                        enabled = deleteMapEnabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.restore_delete_map),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.restore_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
            if (loading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            items(rows, key = { it.mapPath }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = row.worldName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = row.characterName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = stringResource(
                                                R.string.restore_chip_backups,
                                                row.backups.size,
                                            ),
                                            maxLines = 1,
                                        )
                                    },
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text(Backups.sizeLabel(row.mapSize), maxLines = 1) },
                                )
                            }
                            row.newest?.let { newest ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.restore_newest,
                                        Backups.timeLabel(newest.modified),
                                        Backups.sizeLabel(newest.size),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                )
                            }
                        }
                        Checkbox(
                            checked = row.mapPath in selected,
                            onCheckedChange = { onToggle(row.mapPath) },
                            enabled = busy == null,
                        )
                    }
                }
            }
            if (!loading && rows.isEmpty()) {
                item { EmptyState(stringResource(R.string.restore_empty)) }
            }
        }
    }

    // Deleting the map is the only action here that cannot be undone by copying a file back, so it gets
    // its own confirmation even though the button is already labelled with the consequence.
    if (confirmDeleteMaps) {
        AlertDialog(
            onDismissRequest = { confirmDeleteMaps = false },
            title = { Text(stringResource(R.string.restore_delete_map_title)) },
            text = { Text(stringResource(R.string.restore_delete_map_body, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteMaps = false
                    onDeleteMapsConfirmed()
                }) {
                    Text(stringResource(R.string.restore_delete_map), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMaps = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * Shown before an overwrite that would replace an existing backup.
 *
 * The checkbox starts checked because keeping only the newest pre-state is what most players want, and
 * the red box states the consequence while it is checked.
 */
@Composable
fun OverwriteConfirmDialog(
    existing: List<String>,
    onConfirm: (keepPrevious: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deletePrevious by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.overwrite_title)) },
        text = {
            Column {
                Text(stringResource(R.string.overwrite_body, existing.joinToString("、")))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deletePrevious, onCheckedChange = { deletePrevious = it })
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.overwrite_checkbox), style = MaterialTheme.typography.bodyMedium)
                }
                if (deletePrevious) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.overwrite_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(!deletePrevious) }) {
                Text(stringResource(R.string.overwrite_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Plain result list for restore / delete runs, which have no undo. */
@Composable
fun ItemsDialog(title: String, items: List<ItemResult>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(items) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = if (item.status == ItemStatus.SUCCESS) Icons.Filled.CheckCircle
                            else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (item.status == ItemStatus.SUCCESS) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (item.message.isNotEmpty()) {
                                Text(item.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}
