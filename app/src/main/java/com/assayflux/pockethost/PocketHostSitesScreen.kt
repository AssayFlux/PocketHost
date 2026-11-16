package com.assayflux.pockethost

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("WEBSITE", fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text(state.importedSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Text("Site files and folders", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { actions.onShowCreateSiteFileChange(true) }, enabled = !state.hosting && !state.importing,
                            modifier = Modifier.fillMaxWidth()) { Text("New text file") }
                        OutlinedButton(onClick = { actions.onUploadIndividualFile() }, enabled = !state.hosting && !state.importing,
                            modifier = Modifier.fillMaxWidth()) { Text("Upload individual file") }
                        if (state.siteFiles.isEmpty()) {
                            Text("No imported site files yet. Choose a folder or ZIP to deploy your website.", color = palette.onSurfaceVariant)
                        } else {
                            state.siteFiles.forEach { entry ->
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = {
                                                actions.onRenamingSiteFilePathChange(entry.relativePath)
                                                actions.onRenamedSiteFilePathChange(entry.relativePath)
                                            }, enabled = !state.hosting) { Text("Rename") }
                                            OutlinedButton(onClick = {
                                                runCatching { SiteFileManager.readText(context, entry.relativePath) }
                                                    .onSuccess { actions.onEditingSiteFilePathChange(entry.relativePath); actions.onEditingSiteFileContentChange(it) }
                                                    .onFailure { actions.onStatusChange(it.message ?: "This file cannot be edited as text.") }
                                            }, enabled = !state.hosting && entry.isEditable) { Text("Edit") }
                                            if (entry.relativePath.endsWith(".html", true) || entry.relativePath.endsWith(".htm", true)) {
                                                OutlinedButton(onClick = { actions.onPendingIndexSiteFileChange(entry.relativePath) },
                                                    enabled = !state.hosting) { Text("Set as index") }
                                            }
                                            OutlinedButton(onClick = { actions.onPendingDeleteSiteFileChange(entry.relativePath) },
                                                enabled = !state.hosting) { Text("Delete") }
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
                        Text("Imports are staged and validated before replacing the current website. Maximum import: 50 MB, 5,000 files and 10,000 total entries.", color = palette.onSurfaceVariant, fontSize = 14.sp)
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
                        if (state.previousReleases.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val result = runCatching { SiteImporter.rollbackLatest(context) }
                                    result.onSuccess {
                                        actions.onPreviousReleasesChange(SiteImporter.listReleases(context))
                                        actions.onImportedSummaryChange("Restored previous deployment")
                                        actions.onStatusChange("Rollback completed. Restart hosting to load the restored site.")
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putString("site_summary", state.importedSummary).apply()
                                    }.onFailure { actions.onStatusChange(it.message ?: "Rollback failed.") }
                                },
                                enabled = !state.hosting && !state.importing,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Rollback previous deployment") }
                            Text("${state.previousReleases.size} previous release(s) retained · latest first", color = palette.onSurfaceVariant, fontSize = 14.sp)
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
