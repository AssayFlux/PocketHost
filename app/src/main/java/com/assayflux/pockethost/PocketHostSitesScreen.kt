package com.assayflux.pockethost

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assayflux.pockethost.storage.SiteFileManager
import com.assayflux.pockethost.storage.SiteImporter

internal data class PocketHostSitesState(
    val importedSummary: String,
    val status: String,
    val siteFiles: List<SiteFileManager.Entry>,
    val hosting: Boolean,
    val importing: Boolean,
    val showCreateSiteFile: Boolean,
    val newSiteFilePath: String,
    val newSiteFileContent: String,
    val renamingSiteFilePath: String?,
    val renamedSiteFilePath: String,
    val editingSiteFilePath: String?,
    val editingSiteFileContent: String,
    val pendingDeleteSiteFile: String?,
    val pendingIndexSiteFile: String?,
    val backupPassphrase: String,
    val pythonHosting: Boolean,
    val nodeHosting: Boolean,
    val previousReleases: List<String>
)

internal data class PocketHostSitesActions(
    val onShowCreateSiteFileChange: (Boolean) -> Unit,
    val onNewSiteFilePathChange: (String) -> Unit,
    val onNewSiteFileContentChange: (String) -> Unit,
    val onRenamingSiteFilePathChange: (String?) -> Unit,
    val onRenamedSiteFilePathChange: (String) -> Unit,
    val onEditingSiteFilePathChange: (String?) -> Unit,
    val onEditingSiteFileContentChange: (String) -> Unit,
    val onPendingDeleteSiteFileChange: (String?) -> Unit,
    val onPendingIndexSiteFileChange: (String?) -> Unit,
    val onBackupPassphraseChange: (String) -> Unit,
    val onUploadIndividualFile: () -> Unit,
    val onImportFolder: () -> Unit,
    val onImportZip: () -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onPreviewSite: () -> Unit,
    val onFilesChanged: () -> Unit,
    val onStatusChange: (String) -> Unit,
    val onImportedSummaryChange: (String) -> Unit,
    val onPreviousReleasesChange: (List<String>) -> Unit
)

@Composable
internal fun PocketHostSitesScreen(
    context: Context,
    state: PocketHostSitesState,
    actions: PocketHostSitesActions
) {
    val palette = MaterialTheme.colorScheme
    var selectedPage by remember { mutableStateOf("Overview") }
    val fileSearch = remember { mutableStateOf("") }
    val filePage = remember(fileSearch.value) { mutableStateOf(0) }
    val matchingSiteFiles = remember(state.siteFiles, fileSearch.value) {
        state.siteFiles.filter { it.relativePath.contains(fileSearch.value.trim(), ignoreCase = true) }
            .sortedBy { it.relativePath.lowercase() }
    }
    val pageSize = 100
    val pageCount = maxOf(1, (matchingSiteFiles.size + pageSize - 1) / pageSize)
    val currentPage = filePage.value.coerceIn(0, pageCount - 1)
    val visibleSiteFiles = matchingSiteFiles.drop(currentPage * pageSize).take(pageSize)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Overview", "Files", "Backup", "Releases").forEach { page ->
                    FilterChip(
                        selected = selectedPage == page,
                        onClick = { selectedPage = page },
                        label = { Text(page, fontSize = 12.sp, maxLines = 1) }
                    )
                }
            }
            if (selectedPage == "Overview") {
                if (state.status.contains("import", ignoreCase = true) ||
                    state.status.contains("ZIP", ignoreCase = true) ||
                    state.status.contains("website", ignoreCase = true)) {
                    val failed = listOf("failed", "could not", "cannot", "unsafe", "unsupported", "no index")
                        .any { state.status.contains(it, ignoreCase = true) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (failed) palette.errorContainer else palette.secondaryContainer
                        )
                    ) {
                        Text(
                            state.status,
                            modifier = Modifier.padding(12.dp),
                            color = if (failed) palette.onErrorContainer else palette.onSecondaryContainer,
                            fontSize = 13.sp
                        )
                    }
                }
                Text("WEBSITE OVERVIEW", fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text(state.importedSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                if (state.siteFiles.isEmpty()) {
                    Text("Your first publish — no terminal needed", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Choose a website folder or ZIP archive and keep index.html at the top level. Imports are staged and validated before replacing the current website.", color = palette.onSurfaceVariant)
                    Text("After import, open Hosting and start hosting, then use Preview site to verify the website.", color = palette.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = { actions.onPreviewSite() },
                    enabled = state.hosting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Preview site") }
                Button(
                    onClick = { actions.onImportFolder() },
                    enabled = !state.hosting && !state.importing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.importing) "Importing website…" else "Choose website folder") }
                OutlinedButton(
                    onClick = { actions.onImportZip() },
                    enabled = !state.hosting && !state.importing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import website ZIP") }
                Text("Maximum import: 500 MB for the ZIP archive and extracted website contents; 5,000 files and 10,000 total entries.", color = palette.onSurfaceVariant, fontSize = 13.sp)
            }
            if (selectedPage == "Files") {
            Text("SITE FILES", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
            Text("Site files and folders", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { actions.onShowCreateSiteFileChange(true) }, enabled = !state.hosting && !state.importing,
                modifier = Modifier.fillMaxWidth()) { Text("New text file") }
            OutlinedButton(onClick = { actions.onUploadIndividualFile() }, enabled = !state.hosting && !state.importing,
                modifier = Modifier.fillMaxWidth()) { Text("Upload individual file") }
            if (state.siteFiles.isEmpty()) {
                Text("No website files yet. Use Overview to import a folder or ZIP archive.", color = palette.onSurfaceVariant)
            } else {
                OutlinedTextField(
                    value = fileSearch.value,
                    onValueChange = { fileSearch.value = it },
                    label = { Text("Search website files") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val firstVisible = if (matchingSiteFiles.isEmpty()) 0 else currentPage * pageSize + 1
                val lastVisible = minOf((currentPage + 1) * pageSize, matchingSiteFiles.size)
                Text(
                    "Showing $firstVisible–$lastVisible of ${matchingSiteFiles.size} matches (${state.siteFiles.size} discovered entries)",
                    color = palette.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { filePage.value = currentPage - 1 },
                        enabled = currentPage > 0
                    ) { Text("Previous") }
                    Text("Page ${currentPage + 1} of $pageCount", color = palette.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { filePage.value = currentPage + 1 },
                        enabled = currentPage + 1 < pageCount
                    ) { Text("Next") }
                }
                if (visibleSiteFiles.isEmpty()) {
                    Text("No website files match this search.", color = palette.onSurfaceVariant)
                }
                visibleSiteFiles.forEach { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(if (entry.isDirectory) "▣" else "▤", Modifier.padding(end = 8.dp), color = palette.onSurfaceVariant)
                            Text(entry.relativePath, Modifier.weight(1f), color = palette.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.isDirectory) "Folder" else formatBytes(entry.size), color = palette.onSurfaceVariant)
                        }
                        if (!entry.isDirectory) {
                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.lastModified)),
                                color = palette.onSurfaceVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        actions.onRenamingSiteFilePathChange(entry.relativePath)
                                        actions.onRenamedSiteFilePathChange(entry.relativePath)
                                    }, enabled = !state.hosting, modifier = Modifier.weight(1f)) { Text("Rename") }
                                    OutlinedButton(onClick = {
                                        runCatching { SiteFileManager.readText(context, entry.relativePath) }
                                            .onSuccess { actions.onEditingSiteFilePathChange(entry.relativePath); actions.onEditingSiteFileContentChange(it) }
                                            .onFailure { actions.onStatusChange(it.message ?: "This file cannot be edited as text.") }
                                    }, enabled = !state.hosting && entry.isEditable, modifier = Modifier.weight(1f)) { Text("Edit") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (entry.relativePath.endsWith(".html", true) || entry.relativePath.endsWith(".htm", true)) {
                                        OutlinedButton(onClick = { actions.onPendingIndexSiteFileChange(entry.relativePath) },
                                            enabled = !state.hosting, modifier = Modifier.weight(1f)) { Text("Set as index", maxLines = 1) }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    OutlinedButton(onClick = { actions.onPendingDeleteSiteFileChange(entry.relativePath) },
                                        enabled = !state.hosting, modifier = Modifier.weight(1f)) { Text("Delete") }
                                }
                            }
                        } else {
                            OutlinedButton(onClick = {
                                actions.onRenamingSiteFilePathChange(entry.relativePath)
                                actions.onRenamedSiteFilePathChange(entry.relativePath)
                            }, enabled = !state.hosting) { Text("Rename folder") }
                        }
                    }
                    Divider(color = palette.outline)
                }
            }
            Text(if (SiteFileManager.hasIndex(context)) "Entry page: index.html" else "No index.html found at the site root yet.", color = palette.onSurfaceVariant)
            }
            if (selectedPage == "Backup") {
            OutlinedTextField(
                value = state.backupPassphrase,
                onValueChange = { if (it.length <= 128) actions.onBackupPassphraseChange(it) },
                label = { Text("Backup passphrase (12+ characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.hosting && !state.pythonHosting && !state.nodeHosting && !state.importing,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { actions.onExportBackup() },
                enabled = !state.hosting && !state.pythonHosting && !state.nodeHosting && !state.importing && state.backupPassphrase.length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export encrypted full app backup") }
            OutlinedButton(
                onClick = { actions.onRestoreBackup() },
                enabled = !state.hosting && !state.pythonHosting && !state.nodeHosting && !state.importing && state.backupPassphrase.length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore encrypted full app backup") }
            Text("Includes website files, optional Python/Node projects, hostname routes and safe port settings. Device credentials and common secret files are excluded. Review source for embedded secrets before sharing. Restored runtimes stay stopped until you approve trusted code.", color = palette.onSurfaceVariant, fontSize = 13.sp)
            }
            if (selectedPage == "Releases") {
                Text("RELEASE HISTORY", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                if (state.previousReleases.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val result = runCatching { SiteImporter.rollbackLatest(context) }
                            result.onSuccess {
                                actions.onPreviousReleasesChange(SiteImporter.listReleases(context))
                                actions.onImportedSummaryChange("Restored previous deployment")
                                actions.onStatusChange("Rollback completed. Restart hosting to load the restored site.")
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                    .putString("site_summary", "Restored previous deployment").apply()
                            }.onFailure { actions.onStatusChange(it.message ?: "Rollback failed.") }
                        },
                        enabled = !state.hosting && !state.importing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rollback previous deployment") }
                    Text("${state.previousReleases.size} previous release(s) retained · latest first", color = palette.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    Text("No previous deployments are available to roll back.", color = palette.onSurfaceVariant)
                }
            }
        }
    }

                    if (state.showCreateSiteFile) {
                        AlertDialog(
                            onDismissRequest = { actions.onShowCreateSiteFileChange(false) },
                            title = { Text("Create text file") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(value = state.newSiteFilePath, onValueChange = { actions.onNewSiteFilePathChange(it) },
                                        label = { Text("Relative file path") }, singleLine = true)
                                    OutlinedTextField(value = state.newSiteFileContent, onValueChange = { actions.onNewSiteFileContentChange(it) },
                                        label = { Text("File contents") }, minLines = 4, maxLines = 8)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.createText(context, state.newSiteFilePath, state.newSiteFileContent) }
                                        .onSuccess {
                                            actions.onFilesChanged()
                                            actions.onNewSiteFilePathChange("new-page.html")
                                            actions.onNewSiteFileContentChange("")
                                            actions.onStatusChange("Text file created.")
                                        }
                                        .onFailure { actions.onStatusChange(it.message ?: "Could not create the file.") }
                                    actions.onShowCreateSiteFileChange(false)
                                }) { Text("Create") }
                            },
                            dismissButton = { TextButton(onClick = { actions.onShowCreateSiteFileChange(false) }) { Text("Cancel") } }
                        )
                    }
                    state.renamingSiteFilePath?.let { sourcePath ->
                        AlertDialog(
                            onDismissRequest = { actions.onRenamingSiteFilePathChange(null) },
                            title = { Text("Rename website entry") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Enter the new relative path. The destination must be inside an existing website folder and cannot overwrite another entry.")
                                    OutlinedTextField(
                                        value = state.renamedSiteFilePath,
                                        onValueChange = { actions.onRenamedSiteFilePathChange(it) },
                                        label = { Text("New relative path") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.renameEntry(context, sourcePath, state.renamedSiteFilePath) }
                                        .onSuccess {
                                            actions.onFilesChanged()
                                            actions.onStatusChange("Website entry renamed.")
                                        }
                                        .onFailure { actions.onStatusChange(it.message ?: "Could not rename the website entry.") }
                                    actions.onRenamingSiteFilePathChange(null)
                                }, enabled = state.renamedSiteFilePath.isNotBlank() && state.renamedSiteFilePath != sourcePath) { Text("Rename") }
                            },
                            dismissButton = { TextButton(onClick = { actions.onRenamingSiteFilePathChange(null) }) { Text("Cancel") } }
                        )
                    }
                    state.editingSiteFilePath?.let { path ->
                        AlertDialog(
                            onDismissRequest = { actions.onEditingSiteFilePathChange(null) },
                            title = { Text("Edit $path") },
                            text = {
                                OutlinedTextField(value = state.editingSiteFileContent, onValueChange = { actions.onEditingSiteFileContentChange(it) },
                                    modifier = Modifier.fillMaxWidth(), minLines = 6, maxLines = 12)
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.writeText(context, path, state.editingSiteFileContent) }
                                        .onSuccess { actions.onFilesChanged(); actions.onStatusChange("File saved.") }
                                        .onFailure { actions.onStatusChange(it.message ?: "Could not save the file.") }
                                    actions.onEditingSiteFilePathChange(null)
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { actions.onEditingSiteFilePathChange(null) }) { Text("Cancel") } }
                        )
                    }
                    state.pendingDeleteSiteFile?.let { path ->
                        AlertDialog(
                            onDismissRequest = { actions.onPendingDeleteSiteFileChange(null) },
                            title = { Text("Delete file?") },
                            text = { Text("Delete $path permanently from the imported website?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.deleteFile(context, path) }
                                        .onSuccess { actions.onFilesChanged(); actions.onStatusChange("File deleted.") }
                                        .onFailure { actions.onStatusChange(it.message ?: "Could not delete the file.") }
                                    actions.onPendingDeleteSiteFileChange(null)
                                }) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { actions.onPendingDeleteSiteFileChange(null) }) { Text("Cancel") } }
                        )
                    }
                    state.pendingIndexSiteFile?.let { path ->
                        AlertDialog(
                            onDismissRequest = { actions.onPendingIndexSiteFileChange(null) },
                            title = { Text("Set website entry page?") },
                            text = { Text("Copy $path to the website root as index.html? An existing index.html will be replaced.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.setIndex(context, path) }
                                        .onSuccess { actions.onFilesChanged(); actions.onStatusChange("Website entry page updated to index.html.") }
                                        .onFailure { actions.onStatusChange(it.message ?: "Could not set the entry page.") }
                                    actions.onPendingIndexSiteFileChange(null)
                                }) { Text("Set index.html") }
                            },
                            dismissButton = { TextButton(onClick = { actions.onPendingIndexSiteFileChange(null) }) { Text("Cancel") } }
                        )
                    }

}
