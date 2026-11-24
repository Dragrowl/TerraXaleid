// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package io.terramap.android.ui

import android.app.Activity
import android.os.Process
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.terramap.core.Difficulty
import io.terramap.core.WorldEvil
import io.terramap.core.WorldSize
import io.terramap.android.data.BackExit
import io.terramap.android.data.Backups
import io.terramap.android.data.CharacterEntry
import io.terramap.android.data.ItemResult
import io.terramap.android.data.ItemStatus
import io.terramap.android.data.MapBackupRow
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
import java.io.File

private sealed interface Stage {
    data object Worlds : Stage
    data class Characters(val entry: WorldEntry) : Stage
    data object Restore : Stage
    data object Settings : Stage
}

/**
 * Material "shared axis X" between stages: the incoming screen slides in by an eighth of the width
 * while fading in, the outgoing one slides the other way. The curves and the duration live in
 * `Motion.kt` and are shared with the theme cross-fade.
 */

/** Worlds is the root, everything else sits one level down. */
private fun stageDepth(stage: Stage): Int = when (stage) {
    is Stage.Worlds -> 0
    is Stage.Characters, is Stage.Restore, is Stage.Settings -> 1
}

private fun stageTransition(from: Stage, to: Stage): ContentTransform {
    val forward = stageDepth(to) >= stageDepth(from)
    val direction = if (forward) 1 else -1
    val enter = slideInHorizontally(
        animationSpec = tween(StageTransitionMs, easing = EmphasizedDecelerate),
        initialOffsetX = { width -> direction * (width / 8) },
    ) + fadeIn(tween(StageTransitionMs, easing = EmphasizedDecelerate))
    val exit = slideOutHorizontally(
        animationSpec = tween(StageTransitionMs, easing = EmphasizedAccelerate),
        targetOffsetX = { width -> -direction * (width / 8) },
    ) + fadeOut(tween(StageTransitionMs, easing = EmphasizedAccelerate))
    return enter togetherWith exit
}
/** A reveal that has to ask about an existing backup before it touches anything. */
private data class PendingReveal(
    val entry: WorldEntry,
    val characters: List<CharacterEntry>,
    val template: TemplateResult.Ready,
    val existing: List<String>,
)

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(colors = terramapTopBarColors(),
                modifier = terramapTopBarBackground(),title = { Text(stringResource(R.string.app_name)) }) },
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
    var pendingReveal by remember { mutableStateOf<PendingReveal?>(null) }
    var restoreRows by remember { mutableStateOf<List<MapBackupRow>>(emptyList()) }
    var restoreSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var restoreLoading by remember { mutableStateOf(false) }
    var restoreBusy by remember { mutableStateOf<String?>(null) }
    var restoreResults by remember { mutableStateOf<List<ItemResult>?>(null) }
    var restoreResultsTitle by remember { mutableStateOf("") }

    val root: SaveRoot? = roots.getOrNull(rootIndex)

    // Without this the system back gesture finishes the activity from any screen and drops the user on
    // the launcher mid-task. Inside a sub-screen back simply returns to the world list.
    BackHandler(enabled = stage !is Stage.Worlds) { stage = Stage.Worlds }

    // On the world list the press is ours too: whether the process survives is a setting, and the
    // platform default (finish) is only one of the two answers.
    val activity = context as? Activity
    BackHandler(enabled = stage is Stage.Worlds) {
        when (ThemeController.config.backExit) {
            // finishAndRemoveTask only drops the task; the process itself stays cached with the parsed
            // world and the staged mirror in memory, which is the opposite of what the switch promises.
            // Kill it explicitly - but never while a reveal is writing, that would leave a half-written
            // map behind.
            BackExit.Kill -> {
                if (running == null) {
                    activity?.finishAndRemoveTask()
                    Process.killProcess(Process.myPid())
                } else {
                    activity?.moveTaskToBack(true)
                }
            }
            // moveTaskToBack keeps the world list, the staged mirror and the parsed header warm, so the
            // next launch does not have to re-stage 12 MB of save files.
            BackExit.Background -> activity?.moveTaskToBack(true)
        }
    }

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

    fun launchReveal(pending: PendingReveal, keepPrevious: Boolean) {
        running = pending.entry.name
        scope.launch {
            val result = RevealJob.reveal(
                entry = pending.entry,
                characters = pending.characters,
                header = pending.template.header,
                root = root,
                keepPreviousBackup = keepPrevious,
                res = context.resources,
            )
            running = null
            outcome = result
        }
    }

    LaunchedEffect(stage, roots.getOrNull(rootIndex)?.playersDir?.absolutePath, reloadKey) {
        if (stage !is Stage.Restore) return@LaunchedEffect
        val currentRoot = roots.getOrNull(rootIndex)
        restoreLoading = true
        restoreSelected = emptySet()
        restoreRows = if (currentRoot == null) emptyList() else withContext(Dispatchers.IO) {
            Backups.scan(currentRoot, scan?.worlds ?: emptyList())
        }
        restoreLoading = false
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { stageTransition(initialState, targetState) },
        label = "stage",
    ) { animated ->
    when (val current = animated) {
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
            onOpenRestore = { stage = Stage.Restore },
            onOpenSettings = { stage = Stage.Settings },
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
                // Ask the device, not the mirror: a restore or a delete made the staged copy stale, and the
                // prompt has to describe the files the game will actually see.
                scope.launch {
                    val existing = root?.let { Backups.existingBackups(it, characters) } ?: emptySet()
                    val pending = PendingReveal(current.entry, characters, template, existing.toList())
                    if (existing.isEmpty()) launchReveal(pending, keepPrevious = false) else pendingReveal = pending
                }
            },
        )

        is Stage.Settings -> SettingsScreen(
            onBack = { stage = Stage.Worlds },
        )

        is Stage.Restore -> RestoreScreen(
            rows = restoreRows,
            selected = restoreSelected,
            loading = restoreLoading,
            busy = restoreBusy,
            onToggle = { path ->
                restoreSelected = if (path in restoreSelected) restoreSelected - path else restoreSelected + path
            },
            onSelectAll = { restoreSelected = restoreRows.map { it.mapPath }.toSet() },
            onSelectNone = { restoreSelected = emptySet() },
            onRestore = {
                val chosen = restoreRows.filter { it.mapPath in restoreSelected && it.hasBackup }
                if (chosen.isNotEmpty()) {
                    scope.launch {
                        restoreBusy = context.getString(R.string.restore_running)
                        val items = Backups.restore(chosen)
                        restoreBusy = null
                        restoreResultsTitle = context.getString(R.string.restore_result)
                        restoreResults = items
                        reloadKey++
                    }
                }
            },
            onDeleteBackups = {
                val chosen = restoreRows.filter { it.mapPath in restoreSelected && it.hasBackup }
                if (chosen.isNotEmpty()) {
                    scope.launch {
                        restoreBusy = context.getString(R.string.restore_deleting)
                        val items = Backups.deleteBackups(chosen)
                        restoreBusy = null
                        restoreResultsTitle = context.getString(R.string.restore_result_delete)
                        restoreResults = items
                        reloadKey++
                    }
                }
            },
            onDeleteMapsConfirmed = {
                val chosen = restoreRows.filter { it.mapPath in restoreSelected }
                if (chosen.isNotEmpty()) {
                    scope.launch {
                        restoreBusy = context.getString(R.string.restore_deleting)
                        val items = Backups.deleteMaps(chosen)
                        restoreBusy = null
                        restoreResultsTitle = context.getString(R.string.restore_result_delete)
                        restoreResults = items
                        reloadKey++
                    }
                }
            },
            onRefresh = { reloadKey++ },
            onBack = { stage = Stage.Worlds },
        )
    }
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

    pendingReveal?.let { pending ->
        OverwriteConfirmDialog(
            existing = pending.existing,
            onConfirm = { keepPrevious ->
                pendingReveal = null
                launchReveal(pending, keepPrevious)
            },
            onDismiss = { pendingReveal = null },
        )
    }

    restoreBusy?.let { text -> ProgressDialog(title = text, subtitle = "") }

    restoreResults?.let { items ->
        ItemsDialog(title = restoreResultsTitle, items = items, onClose = { restoreResults = null })
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
    onOpenRestore: () -> Unit,
    onOpenSettings: () -> Unit,
    shizukuAlive: Boolean,
    shizukuGranted: Boolean,
    onShizuku: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Survives rotation: the filter is part of what the user is looking at, not transient UI state.
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = terramapTopBarColors(),
                modifier = terramapTopBarBackground(),
                title = { Text(stringResource(R.string.worlds_title)) },
                actions = {
                    // One overflow entry instead of a growing row of icons: settings, the backup browser
                    // and a refresh do not deserve equal billing in the bar.
                    // The three labels are resolved out here: a DropdownMenu body is composed in a popup
                    // subcomposition that does not reliably inherit the locale wrapper, so resolving the
                    // strings inside it can leave the menu in the language the process started with.
                    val settingsLabel = stringResource(R.string.menu_settings)
                    val restoreLabel = stringResource(R.string.menu_restore)
                    val refreshLabel = stringResource(R.string.menu_refresh)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(settingsLabel) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(restoreLabel) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Restore, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenRestore()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(refreshLabel) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onReload()
                                },
                            )
                        }
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
                                    text = rootText(r.label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            // Search banner: sits above the list, never scrolls away, and only narrows the cards
            // below it. Matching is a plain case-insensitive substring test on the world name.
            SearchBanner(
                query = query,
                onQueryChange = { query = it },
                hint = stringResource(R.string.worlds_search_hint),
            )

            // Everything else lives in the list so the header cards can never starve it of height:
            // in portrait the old fixed Column left the worlds area with zero pixels.
            BoxWithConstraints(Modifier.weight(1f)) {
            val columns = gridColumns(maxWidth)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val currentRoot = roots.getOrNull(rootIndex)
                if (currentRoot != null) {
                    item(key = "root-info", span = { GridItemSpan(maxLineSpan) }) {
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
                    item(key = "shizuku-banner", span = { GridItemSpan(maxLineSpan) }) {
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
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
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
                    item(key = "no-root", span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.worlds_empty_no_root)) }
                } else if (scan == null || scan.worlds.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.worlds_empty)) }
                } else {
                    val visible = scan.worlds.filter {
                        query.isBlank() || it.name.contains(query, ignoreCase = true)
                    }
                    if (visible.isEmpty()) {
                        item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(stringResource(R.string.worlds_search_none))
                        }
                    }
                    gridItems(visible, key = { it.file.path }) { entry ->
                        WorldCard(
                            entry = entry,
                            checked = entry.file.path in selected,
                            onToggle = { onToggle(entry.file.path) },
                            onOpen = { onOpen(entry) },
                            highlight = query,
                        )
                    }
                    if (scan.issues.isNotEmpty()) {
                        item(key = "issues", span = { GridItemSpan(maxLineSpan) }) {
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
}

@Composable
private fun WorldCard(
    entry: WorldEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    highlight: String = "",
) {
    val context = LocalContext.current
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
                Text(
                    text = highlighted(entry.name, highlight),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                // Scrollable so the third chip keeps its pill shape on a narrow screen instead of
                // collapsing into a two-line block.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val labels = buildList {
                        add(difficultyText(entry.meta.difficultyKind))
                        add(sizeText(entry.meta.sizeKind))
                        add(evilText(entry.meta.evilKind))
                        // Progression as chips in the same scrollable row: pre-hardmode /
                        // hardmode / journey's end are mutually informative, not a separate line.
                        // Hardmode is a necessary but not sufficient condition for the Moon Lord, so
                        // the three states are mutually exclusive: only the furthest one is shown.
                        add(
                            when {
                                entry.meta.defeatedMoonLord -> context.getString(R.string.label_journeys_end)
                                entry.meta.hardMode -> context.getString(R.string.label_hardmode_short)
                                else -> context.getString(R.string.label_prehard_short)
                            }
                        )
                        if (entry.meta.seedFlags.any { it == "getGood" }) {
                            add("FTW")
                        }
                    }.filter { it.isNotBlank() }
                    labels.forEach { label ->
                        AssistChip(
                            onClick = onOpen,
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
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
    val context = LocalContext.current
    val characters = remember(entry, root) {
        if (root == null) emptyList() else WorldScanner.characters(root, entry)
    }
    val template = remember(entry, root, roots, context.resources.configuration.locales[0]) {
        if (root == null) TemplateResult.Missing(context.getString(R.string.template_missing))
        else Templates.resolve(entry, root, roots, context.resources)
    }
    var selected by remember(entry) { mutableStateOf(characters.map { it.name }.toSet()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = terramapTopBarColors(),
                modifier = terramapTopBarBackground(),
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
                        text = stringResource(R.string.template_label, template.source),
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
        BoxWithConstraints(Modifier.padding(padding)) {
        val columns = gridColumns(maxWidth)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // A lazy item's slot is a single box, not a column: without this wrapper the title and
                // the hint paint on top of each other.
                Column {
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
            }
            gridItems(characters, key = { it.name }) { character ->
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
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.worlds_empty)) }
            }
        }
        }
    }
}

private fun characterEnabled(template: TemplateResult, selected: Set<String>): Boolean =
    template is TemplateResult.Ready && selected.isNotEmpty()

@Composable
internal fun EmptyState(text: String) {
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

@Composable
private fun difficultyText(kind: Difficulty): String = when (kind) {
    Difficulty.Classic -> stringResource(R.string.difficulty_classic)
    Difficulty.Expert -> stringResource(R.string.difficulty_expert)
    Difficulty.Master -> stringResource(R.string.difficulty_master)
    Difficulty.Legendary -> stringResource(R.string.difficulty_legendary)
    Difficulty.Journey -> stringResource(R.string.difficulty_journey)
}

@Composable
private fun sizeText(kind: WorldSize): String = when (kind) {
    WorldSize.Small -> stringResource(R.string.size_small)
    WorldSize.Medium -> stringResource(R.string.size_medium)
    WorldSize.Large -> stringResource(R.string.size_large)
    WorldSize.Huge -> stringResource(R.string.size_huge)
}

@Composable
private fun evilText(kind: WorldEvil): String = when (kind) {
    WorldEvil.Corruption -> stringResource(R.string.evil_corruption)
    WorldEvil.Crimson -> stringResource(R.string.evil_crimson)
}

/** Save-root names are produced by discovery code; they are translated here, at the UI edge. */
@Composable
private fun rootText(raw: String): String = when (raw) {
    "游戏目录 (Shizuku)" -> stringResource(R.string.root_game_shizuku)
    "Shizuku 暂存" -> stringResource(R.string.root_shizuku_cache)
    "游戏目录 (files)" -> stringResource(R.string.root_game_files)
    "游戏目录" -> stringResource(R.string.root_game)
    else -> raw
}


/** Column count for the card grids: landscape phones and tablets get 2–4 columns, portrait 1. */
private fun gridColumns(width: androidx.compose.ui.unit.Dp): Int = when {
    width >= 1240.dp -> 4
    width >= 820.dp -> 3
    width >= 560.dp -> 2
    else -> 1
}

/** Case-insensitive highlight of the search term inside a world name. */
private fun highlighted(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val start = text.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(background = androidx.compose.ui.graphics.Color(0x66FFC107)),
            start,
            start + query.length,
        )
    }
}

/** The search banner above the world list: one line, clear button once something is typed. */
@Composable
private fun SearchBanner(query: String, onQueryChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = androidx.compose.foundation.shape.CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
