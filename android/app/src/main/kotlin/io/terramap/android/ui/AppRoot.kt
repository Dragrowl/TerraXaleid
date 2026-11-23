// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package io.terramap.android.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.terramap.android.R
import io.terramap.android.data.CharacterEntry
import io.terramap.android.data.ItemResult
import io.terramap.android.data.ItemStatus
import io.terramap.android.data.RevealJob
import io.terramap.android.data.RevealOutcome
import io.terramap.android.data.SaveRoot
import io.terramap.android.data.ScanResult
import io.terramap.android.data.TemplateResult
import io.terramap.android.data.Templates
import io.terramap.android.data.TerraMirror
import io.terramap.android.data.TerraPaths
import io.terramap.android.data.WorldEntry
import io.terramap.android.data.WorldScanner
import io.terramap.android.data.ShizukuShell
import io.terramap.android.perms.StorageAccess
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Stage {
    data object Worlds : Stage
    data class Characters(val entry: WorldEntry) : Stage
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var access by remember { mutableStateOf(StorageAccess.hasAccess(context)) }
    var shizukuAlive by remember { mutableStateOf(ShizukuShell.isBinderAlive()) }
    var shizukuGranted by remember { mutableStateOf(ShizukuShell.hasPermission()) }
    val owner = LocalLifecycleOwner.current

    // Shizuku can arrive after the UI is up (service started by adb / root), so listen for it.
    DisposableEffect(Unit) {
        val onBinder = Shizuku.OnBinderReceivedListener {
            shizukuAlive = true
            shizukuGranted = ShizukuShell.hasPermission()
        }
        val onDead = Shizuku.OnBinderDeadListener {
            shizukuAlive = false
            shizukuGranted = false
        }
        val onResult = Shizuku.OnRequestPermissionResultListener { _, grant ->
            shizukuGranted = grant == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addBinderReceivedListener(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onResult)
        onDispose {
            Shizuku.removeBinderReceivedListener(onBinder)
            Shizuku.removeBinderDeadListener(onDead)
            Shizuku.removeRequestPermissionResultListener(onResult)
        }
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = StorageAccess.hasAccess(context)
                shizukuAlive = ShizukuShell.isBinderAlive()
                shizukuGranted = ShizukuShell.hasPermission()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (access || shizukuGranted) {
        MainFlow(shizukuAlive = shizukuAlive, shizukuGranted = shizukuGranted)
    } else {
        PermissionScreen(
            shizukuAlive = shizukuAlive,
            onOpenSettings = {
                runCatching { context.startActivity(StorageAccess.settingsIntent(context)) }
            },
            onRecheck = {
                access = StorageAccess.hasAccess(context)
                shizukuAlive = ShizukuShell.isBinderAlive()
                shizukuGranted = ShizukuShell.hasPermission()
            },
            onShizuku = { ShizukuShell.requestPermission(context) },
        )
    }
}

@Composable
private fun PermissionScreen(
    shizukuAlive: Boolean,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
    onShizuku: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onRecheck() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.perm_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.perm_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = {
                    val legacy = StorageAccess.legacyRequest(context)
                    if (legacy.isEmpty()) onOpenSettings() else launcher.launch(legacy)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.perm_button)) }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.shizuku_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.shizuku_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    if (shizukuAlive) R.string.shizuku_state_running else R.string.shizuku_state_absent,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Button(
                onClick = onShizuku,
                enabled = shizukuAlive,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.shizuku_button)) }

            TextButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_check_again))
            }
        }
    }
}

@Composable
private fun MainFlow(shizukuAlive: Boolean, shizukuGranted: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mirror by remember { mutableStateOf<SaveRoot?>(null) }
    var roots by remember { mutableStateOf(TerraPaths.existingRoots()) }
    var rootIndex by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var scan by remember { mutableStateOf<ScanResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedWorlds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var stage by remember { mutableStateOf<Stage>(Stage.Worlds) }
    var running by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<RevealOutcome?>(null) }

    val root: SaveRoot? = roots.getOrNull(rootIndex)

    // Stage the game's own save tree through Shizuku; the mirror is the first root when available.
    LaunchedEffect(shizukuGranted) {
        // NonCancellable: the grant flag can flip while the copy is running, and cancelling halfway
        // would leave a half-staged tree behind (and an empty world list).
        if (shizukuGranted && mirror == null) {
            val staged = withContext(Dispatchers.IO + NonCancellable) { TerraMirror.prepare() ?: TerraMirror.adoptStaging() }
            mirror = staged
            roots = listOfNotNull(staged) + TerraPaths.existingRoots()
            if (staged != null) rootIndex = 0
        }
    }

    // Keyed on the resolved path, not the index: when the Shizuku mirror lands at index 0 the path
    // changes and the list has to be rescanned even though the index stayed the same.
    LaunchedEffect(roots.getOrNull(rootIndex)?.worldsDir?.absolutePath, reloadKey) {
        val current = roots.getOrNull(rootIndex)
        loading = true
        scan = if (current == null) null else withContext(Dispatchers.IO) {
            WorldScanner.scan(TerraPaths.worldFiles(current))
        }
        selectedWorlds = emptySet()
        loading = false
    }

    when (val current = stage) {
        is Stage.Worlds -> WorldsScreen(
            roots = roots,
            rootIndex = rootIndex,
            onPickRoot = { rootIndex = it },
            loading = loading,
            scan = scan,
            selected = selectedWorlds,
            onToggle = { key ->
                selectedWorlds = if (key in selectedWorlds) selectedWorlds - key else selectedWorlds + key
            },
            onSelectAll = { scan?.worlds?.map { it.file.path }?.toSet()?.let { selectedWorlds = it } },
            onSelectNone = { selectedWorlds = emptySet() },
            onReload = { reloadKey++ },
            onOpen = { entry -> stage = Stage.Characters(entry) },
            shizukuAlive = shizukuAlive,
            shizukuGranted = shizukuGranted,
            onShizuku = { ShizukuShell.requestPermission(context) },
        )

        is Stage.Characters -> CharactersScreen(
            entry = current.entry,
            root = root,
            roots = roots,
            onBack = { stage = Stage.Worlds },
            onReveal = { characters, template ->
                running = current.entry.name
                scope.launch {
                    val result = RevealJob.reveal(
                        entry = current.entry,
                        characters = characters,
                        header = template.header,
                        root = root,
                    )
                    running = null
                    outcome = result
                }
            },
        )
    }

    running?.let { name ->
        ProgressDialog(title = stringResource(R.string.run_title), subtitle = name)
    }

    outcome?.let { result ->
        ResultDialog(
            outcome = result,
            onUndo = {
                scope.launch {
                    RevealJob.undo(result.targets, root)
                    outcome = null
                    reloadKey++
                }
            },
            onClose = {
                outcome = null
                stage = Stage.Worlds
                reloadKey++
            },
        )
    }
}

@Composable
private fun WorldsScreen(
    roots: List<SaveRoot>,
    rootIndex: Int,
    onPickRoot: (Int) -> Unit,
    loading: Boolean,
    scan: ScanResult?,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onReload: () -> Unit,
    onOpen: (WorldEntry) -> Unit,
    shizukuAlive: Boolean,
    shizukuGranted: Boolean,
    onShizuku: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worlds_title)) },
                actions = {
                    IconButton(onClick = onReload) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.worlds_reload))
                    }
                },
            )
        },
        bottomBar = {
            if (!selected.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    // Stacked in two rows: in portrait the single row squeezed "点亮所选" into a
                    // two-line pill.
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selected.size} / ${scan?.worlds?.size ?: 0}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onSelectAll) {
                                Text(stringResource(R.string.worlds_select_all), maxLines = 1)
                            }
                            TextButton(onClick = onSelectNone) {
                                Text(stringResource(R.string.worlds_select_none), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scan?.worlds?.firstOrNull { it.file.path in selected }?.let(onOpen)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_reveal), maxLines = 1)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // One scrolling row: three long labels do not fit across a phone in portrait, and a fixed
            // Row would clip them instead of letting the user scroll.
            if (roots.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    roots.forEachIndexed { index, r ->
                        FilterChip(
                            selected = index == rootIndex,
                            onClick = { onPickRoot(index) },
                            label = {
                                Text(
                                    text = r.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            // Everything else lives in the list so the header cards can never starve it of height:
            // in portrait the old fixed Column left the worlds area with zero pixels.
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val currentRoot = roots.getOrNull(rootIndex)
                if (currentRoot != null) {
                    item(key = "root-info") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.close_game_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = currentRoot.worldsDir.absolutePath,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                if (!shizukuGranted) {
                    item(key = "shizuku-banner") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.shizuku_banner_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.shizuku_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onShizuku) {
                                    Text(stringResource(R.string.shizuku_button))
                                }
                            }
                        }
                    }
                }

                if (loading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (roots.isEmpty()) {
                    item(key = "no-root") { EmptyState(stringResource(R.string.worlds_empty_no_root)) }
                } else if (scan == null || scan.worlds.isEmpty()) {
                    item(key = "empty") { EmptyState(stringResource(R.string.worlds_empty)) }
                } else {
                    items(scan.worlds, key = { it.file.path }) { entry ->
                        WorldCard(
                            entry = entry,
                            checked = entry.file.path in selected,
                            onToggle = { onToggle(entry.file.path) },
                            onOpen = { onOpen(entry) },
                        )
                    }
                    if (scan.issues.isNotEmpty()) {
                        item(key = "issues") {
                            Column {
                                Text(
                                    text = stringResource(R.string.worlds_issues, scan.issues.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                scan.issues.forEach { issue ->
                                    Text(
                                        text = "${issue.fileName}: ${issue.reason}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldCard(
    entry: WorldEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = null,
                tint = if (entry.isMaster) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                // Scrollable so the third chip keeps its pill shape on a narrow screen instead of
                // collapsing into a two-line block.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val labels = listOf(entry.meta.difficulty, entry.meta.size, entry.meta.evil)
                        .filter { it.isNotBlank() }
                    labels.forEach { label ->
                        AssistChip(
                            onClick = onOpen,
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(if (entry.meta.hardMode) "肉山后" else "未肉山")
                        append(" · ")
                        append(if (entry.meta.defeatedMoonLord) "月总后" else "未月总")
                        entry.meta.seedFlags.firstOrNull { it == "getGood" }?.let { append(" · FTW") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun CharactersScreen(
    entry: WorldEntry,
    root: SaveRoot?,
    roots: List<SaveRoot>,
    onBack: () -> Unit,
    onReveal: (List<CharacterEntry>, TemplateResult.Ready) -> Unit,
) {
    val characters = remember(entry, root) {
        if (root == null) emptyList() else WorldScanner.characters(root, entry)
    }
    val template = remember(entry, root, roots) {
        if (root == null) TemplateResult.Missing("找不到存档目录")
        else Templates.resolve(entry, root, roots)
    }
    var selected by remember(entry) { mutableStateOf(characters.map { it.name }.toSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chars_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                when (template) {
                    is TemplateResult.Missing -> Text(
                        text = template.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is TemplateResult.Ready -> Text(
                        text = "模板：${template.source}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = characterEnabled(template, selected),
                    onClick = {
                        val ready = template as? TemplateResult.Ready ?: return@Button
                        onReveal(characters.filter { it.name in selected }, ready)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.chars_confirm, selected.size))
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
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.chars_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
            items(characters, key = { it.name }) { character ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(character.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (character.hasMap) character.mapFile.name
                                else stringResource(R.string.chars_new_map),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Checkbox(
                            checked = character.name in selected,
                            onCheckedChange = { isChecked ->
                                selected = if (isChecked) selected + character.name
                                else selected - character.name
                            },
                        )
                    }
                }
            }
            if (characters.isEmpty()) {
                item { EmptyState(stringResource(R.string.worlds_empty)) }
            }
        }
    }
}

private fun characterEnabled(template: TemplateResult, selected: Set<String>): Boolean =
    template is TemplateResult.Ready && selected.isNotEmpty()

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProgressDialog(title: String, subtitle: String) {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResultDialog(outcome: RevealOutcome, onUndo: () -> Unit, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Card {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.done_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                outcome.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (item.status == ItemStatus.SUCCESS) Icons.Filled.Check
                            else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (item.status == ItemStatus.SUCCESS) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium)
                            if (item.message.isNotEmpty()) {
                                Text(item.message, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onUndo, enabled = outcome.targets.isNotEmpty()) {
                        Text(stringResource(R.string.done_undo))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onClose) { Text(stringResource(R.string.done_close)) }
                }
            }
        }
    }
}
