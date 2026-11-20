package com.assayflux.pockethost

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assayflux.pockethost.sites.SiteRegistry
import com.assayflux.pockethost.workbench.WorkbenchWorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PocketHostWorkbenchScreen(context: Context) {
    val palette = MaterialTheme.colorScheme
    var selectedPage by remember { mutableStateOf("Explorer") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Explorer", "Editor", "Changes", "History").forEach { page ->
            FilterChip(selected = selectedPage == page, onClick = { selectedPage = page },
                label = { Text(page, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
        }
    }
    val scope = rememberCoroutineScope()
    val availableSites = remember {
        val registered = SiteRegistry.list(context)
        val fallback = SiteRegistry.Site("static-default", "Default website", "static",
            File(context.filesDir, "site").absolutePath, emptyList(), true)
        (listOf(registered.firstOrNull { it.id == "static-default" } ?: fallback) +
            registered.filterNot { it.id == "static-default" }).distinctBy { it.id }
    }
    var selectedSiteId by remember { mutableStateOf(availableSites.first().id) }
    val selectedSite = availableSites.firstOrNull { it.id == selectedSiteId } ?: availableSites.first()
    var entries by remember { mutableStateOf(emptyList<WorkbenchWorkspaceManager.Entry>()) }
    var changes by remember { mutableStateOf(emptyList<WorkbenchWorkspaceManager.Change>()) }
    var commits by remember { mutableStateOf(emptyList<WorkbenchWorkspaceManager.Commit>()) }
    var currentFolder by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorDirty by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Select a website to open its isolated workspace.") }
    var newPath by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var showCreateFile by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showCommit by remember { mutableStateOf(false) }
    var showDiscardChanges by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }
    var renameSource by remember { mutableStateOf<String?>(null) }
    var renameDestination by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var siteMenuExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    WorkbenchWorkspaceManager.open(context, selectedSite.id, File(selectedSite.root))
                    Triple(
                        WorkbenchWorkspaceManager.list(context, selectedSite.id),
                        WorkbenchWorkspaceManager.changes(context, selectedSite.id, File(selectedSite.root)),
                        WorkbenchWorkspaceManager.commits(context, selectedSite.id)
                    )
                }
            }.onSuccess {
                entries = it.first
                changes = it.second
                commits = it.third
                status = "Workspace ready · ${it.first.size} entries · ${it.second.size} uncommitted changes"
            }.onFailure {
                entries = emptyList()
                changes = emptyList()
                commits = emptyList()
                status = it.message ?: "Could not open this website workspace."
            }
            loading = false
        }
    }

    LaunchedEffect(selectedSiteId) {
        currentFolder = ""
        selectedFile = null
        editorText = ""
        editorDirty = false
        refresh()
    }

    val folderPrefix = currentFolder.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    val visibleEntries = entries.filter { it.path.substringBeforeLast('/', "") == currentFolder.trim('/') }
    val selectedSiteLabel = selectedSite.name + if (selectedSite.runtime == "static") " · Static" else " · ${selectedSite.runtime}"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("POCKETHOST WORKBENCH", color = palette.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Edit a private workspace. Your live website stays unchanged until a reviewed deployment is available.", color = palette.onSurfaceVariant, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WEBSITE PROJECT", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(selectedSiteLabel, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { siteMenuExpanded = true }, enabled = !editorDirty) { Text("Switch") }
                    DropdownMenu(expanded = siteMenuExpanded, onDismissRequest = { siteMenuExpanded = false }) {
                        availableSites.forEach { site ->
                            DropdownMenuItem(text = { Text(site.name) }, onClick = { selectedSiteId = site.id; siteMenuExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("WORKSPACE / DRAFT", color = palette.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("≠", color = palette.onSurfaceVariant)
                    Text("LIVE / PRODUCTION", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(status, color = palette.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        if (selectedPage == "Explorer") {
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EXPLORER", color = palette.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { refresh() }, enabled = !loading) { Text("Refresh") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { if (currentFolder.isNotEmpty()) currentFolder = currentFolder.substringBeforeLast('/', "") },
                        enabled = currentFolder.isNotEmpty() && !editorDirty) { Text("Up") }
                    Text(if (currentFolder.isEmpty()) "/" else "/$currentFolder/", color = palette.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { newPath = ""; showCreateFile = true }, enabled = !loading) { Text("New file") }
                    OutlinedButton(onClick = { newPath = ""; showCreateFolder = true }, enabled = !loading) { Text("New folder") }
                }
                if (loading) Text("Opening workspace…", color = palette.onSurfaceVariant)
                else if (visibleEntries.isEmpty()) Text("This folder is empty.", color = palette.onSurfaceVariant)
                else visibleEntries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !editorDirty && (entry.directory || entry.editable)) {
                            if (entry.directory) {
                                currentFolder = if (currentFolder.isEmpty()) entry.path else "$currentFolder/${entry.path.substringAfterLast('/')}"
                            } else {
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.readText(context, selectedSite.id, entry.path) } }
                                        .onSuccess { selectedFile = entry.path; editorText = it; editorDirty = false; selectedPage = "Editor" }
                                        .onFailure { status = it.message ?: "Could not open file." }
                                }
                            }
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (entry.directory) "▣" else "▤", color = palette.primary, modifier = Modifier.padding(end = 9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.path.substringAfterLast('/'), color = palette.onSurface, fontSize = 13.sp,
                                fontWeight = if (entry.directory) FontWeight.SemiBold else FontWeight.Normal)
                            if (entry.directory) Text("Folder", color = palette.onSurfaceVariant, fontSize = 10.sp)
                            else Text("${entry.size} bytes", color = palette.onSurfaceVariant, fontSize = 10.sp)
                        }
                        if (entry.directory) Text("Open  ›", color = palette.primary, fontSize = 11.sp)
                        else {
                            TextButton(onClick = { renameSource = entry.path; renameDestination = entry.path }) { Text("Rename") }
                            TextButton(onClick = { deleteTarget = entry.path }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        }

        if (selectedPage == "Editor") {
            if (selectedFile == null) {
                Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No file selected", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Choose a text file in Explorer to open it in the editor.", color = palette.onSurfaceVariant)
                        OutlinedButton(onClick = { selectedPage = "Explorer" }) { Text("Open Explorer") }
                    }
                }
            }
            selectedFile?.let { path ->
            Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EDITOR", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(path, color = palette.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (editorDirty) Text("UNSAVED", color = palette.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = editorText,
                        onValueChange = { editorText = it; editorDirty = true },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 10,
                        maxLines = 24,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.writeText(context, selectedSite.id, path, editorText) } }
                                    .onSuccess { editorDirty = false; refresh(); status = "Saved to Workbench workspace. Live site unchanged." }
                                    .onFailure { status = it.message ?: "Could not save workspace file." }
                            }
                        }, enabled = editorDirty && !loading) { Text("Save draft") }
                        OutlinedButton(onClick = { if (editorDirty) showDiscardChanges = true else selectedFile = null }) { Text("Close") }
                    }
                }
            }
        }

        }
        if (selectedPage == "Changes") {
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CHANGES", color = palette.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${changes.size}", color = palette.primary, fontWeight = FontWeight.Bold)
                }
                if (changes.isEmpty()) Text("No uncommitted changes against the current live website.", color = palette.onSurfaceVariant, fontSize = 12.sp)
                else changes.forEach { change ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(change.status.uppercase(), color = palette.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        Text(change.path, color = palette.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(onClick = { commitMessage = ""; showCommit = true }, enabled = changes.isNotEmpty() && !loading,
                    modifier = Modifier.fillMaxWidth()) { Text("Create local commit") }
                Text("Commits save immutable local snapshots only. Service coming soon: production deployment and rollback controls are not enabled in this release.", color = palette.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        }

        if (selectedPage == "History") {
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("COMMIT HISTORY", color = palette.onSurface, fontWeight = FontWeight.Bold)
                if (commits.isEmpty()) Text("No commits yet.", color = palette.onSurfaceVariant, fontSize = 12.sp)
                commits.forEach { commit ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(commit.message, color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${commit.id} · ${commit.fileCount} files · ${commit.totalBytes} bytes",
                            color = palette.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
        }

    }

    if (showDiscardChanges) AlertDialog(
        onDismissRequest = { showDiscardChanges = false },
        title = { Text("Discard unsaved edits?") },
        text = { Text("Your editor contains changes that have not been saved to the Workbench draft.") },
        confirmButton = { TextButton(onClick = {
            showDiscardChanges = false
            selectedFile = null
            editorDirty = false
        }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { showDiscardChanges = false }) { Text("Keep editing") } }
    )

    if (showCreateFile) AlertDialog(
        onDismissRequest = { showCreateFile = false },
        title = { Text("Create workspace file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newPath, onValueChange = { newPath = it }, label = { Text("Relative path") }, singleLine = true)
                OutlinedTextField(value = newContent, onValueChange = { newContent = it }, label = { Text("Initial contents") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = {
            val path = if (currentFolder.isEmpty()) newPath else "$folderPrefix$newPath"
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.createText(context, selectedSite.id, path, newContent) } }
                    .onSuccess { showCreateFile = false; newContent = ""; refresh(); status = "File created in the Workbench workspace." }
                    .onFailure { status = it.message ?: "Could not create workspace file." }
            }
        }, enabled = newPath.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showCreateFile = false }) { Text("Cancel") } }
    )

    if (showCreateFolder) AlertDialog(
        onDismissRequest = { showCreateFolder = false },
        title = { Text("Create workspace folder") },
        text = { OutlinedTextField(value = newPath, onValueChange = { newPath = it }, label = { Text("Folder name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val path = if (currentFolder.isEmpty()) newPath else "$folderPrefix$newPath"
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.createFolder(context, selectedSite.id, path) } }
                    .onSuccess { showCreateFolder = false; refresh(); status = "Folder created in the Workbench workspace." }
                    .onFailure { status = it.message ?: "Could not create folder." }
            }
        }, enabled = newPath.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("Cancel") } }
    )

    if (showCommit) AlertDialog(
        onDismissRequest = { showCommit = false },
        title = { Text("Commit workspace changes") },
        text = { OutlinedTextField(value = commitMessage, onValueChange = { commitMessage = it.take(160) },
            label = { Text("Commit message") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.commit(context, selectedSite.id, commitMessage, File(selectedSite.root)) } }
                    .onSuccess { showCommit = false; refresh(); status = "Local commit ${it.id} created. Live website unchanged." }
                    .onFailure { status = it.message ?: "Could not create local commit." }
            }
        }, enabled = commitMessage.isNotBlank()) { Text("Commit") } },
        dismissButton = { TextButton(onClick = { showCommit = false }) { Text("Cancel") } }
    )

    renameSource?.let { source ->
        AlertDialog(
            onDismissRequest = { renameSource = null },
            title = { Text("Rename workspace entry") },
            text = { OutlinedTextField(value = renameDestination, onValueChange = { renameDestination = it },
                label = { Text("New relative path") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.rename(context, selectedSite.id, source, renameDestination) } }
                        .onSuccess { renameSource = null; refresh(); status = "Workspace entry renamed." }
                        .onFailure { status = it.message ?: "Could not rename entry." }
                }
            }, enabled = renameDestination.isNotBlank() && renameDestination != source) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameSource = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete workspace file?") },
            text = { Text("Delete $target from the editable workspace? The live website remains unchanged.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { WorkbenchWorkspaceManager.deleteFile(context, selectedSite.id, target) } }
                        .onSuccess { deleteTarget = null; if (selectedFile == target) selectedFile = null; refresh(); status = "Workspace file deleted. Live website unchanged." }
                        .onFailure { status = it.message ?: "Could not delete file." }
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
