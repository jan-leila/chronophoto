package com.chronophoto.ui.projects.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.data.db.entity.Project
import com.chronophoto.reminder.ReminderSchedule
import com.chronophoto.ui.components.DurationUnit
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ProjectDetailViewModel = viewModel(
        factory = ProjectDetailViewModel.factory(context, projectId),
        key = "project-$projectId",
    )
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.project?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.project != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit project")
                        }
                    }
                    OverflowMenu(
                        archived = state.project?.archivedAt != null,
                        onArchive = vm::archive,
                        onUnarchive = vm::unarchive,
                        onDelete = vm::delete,
                        onImport = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                },
            )
        },
        floatingActionButton = {
            if (state.project != null && state.project?.archivedAt == null) {
                ExtendedFloatingActionButton(
                    text = { Text("Take photo") },
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    onClick = onCapture,
                )
            }
        },
    ) { padding ->
        val project = state.project
        if (project == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }
        Body(
            padding = padding,
            project = project,
            photos = state.photos,
            importing = state.importing,
            importError = state.importError,
            onClearError = vm::clearImportError,
            onOpenPhoto = onOpenPhoto,
        )
    }
}

@Composable
private fun Body(
    padding: PaddingValues,
    project: Project,
    photos: List<Photo>,
    importing: Boolean,
    importError: String?,
    onClearError: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(project = project, photoCount = photos.size, importing = importing)
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Photos", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "No photos yet — tap the camera button or import one from your gallery.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            items(photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = "Photo ${photo.sequence + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onOpenPhoto(photo.id) },
                )
            }
        }
    }

    if (importError != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Import failed") },
            text = { Text(importError) },
            confirmButton = { TextButton(onClick = onClearError) { Text("OK") } },
        )
    }
}

@Composable
private fun Header(project: Project, photoCount: Int, importing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        project.description?.let { Text(it) }
        Text(
            text = buildString {
                append("Created ")
                append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.createdAt)))
                if (project.archivedAt != null) {
                    append(" • archived ")
                    append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.archivedAt)))
                }
                append(" • ")
                append(if (photoCount == 1) "1 photo" else "$photoCount photos")
                if (importing) append(" • importing…")
            },
            style = MaterialTheme.typography.bodySmall,
        )
        MetadataCard(project)
        ReminderCard(project)
    }
}

@Composable
private fun MetadataCard(project: Project) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Metadata captured", fontWeight = FontWeight.SemiBold)
            MetadataLine("Timestamp", project.storeTimestamp)
            MetadataLine("Camera settings", project.storeCameraSettings)
            MetadataLine("Ambient brightness", project.storeAmbientBrightness)
            MetadataLine("GPS location", project.storeGps)
        }
    }
}

@Composable
private fun ReminderCard(project: Project) {
    val amount = project.reminderIntervalAmount
    val unit = project.reminderIntervalUnit?.let { runCatching { DurationUnit.valueOf(it) }.getOrNull() }
    val anchor = project.reminderAnchorMillis
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Reminder", fontWeight = FontWeight.SemiBold)
            Text(
                text = if (amount != null && unit != null && anchor != null) {
                    val base = ReminderSchedule.describe(amount, unit, anchor).replaceFirstChar { it.uppercase() }
                    val refire = project.reminderRefireMinutes
                    if (refire != null) "$base · re-nag every ${ReminderSchedule.describeMinutes(refire)}" else base
                } else {
                    "Off"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (project.reminderActiveFireMillis != null) {
                Text(
                    text = "A reminder is currently waiting on this project.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, enabled: Boolean) {
    Text(
        text = "$label: ${if (enabled) "on" else "off"}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun OverflowMenu(
    archived: Boolean,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Import photo") },
            onClick = { onImport(); open = false },
        )
        if (archived) {
            DropdownMenuItem(
                text = { Text("Unarchive") },
                onClick = { onUnarchive(); open = false },
            )
        } else {
            DropdownMenuItem(
                text = { Text("Archive") },
                onClick = { onArchive(); open = false },
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = { confirmDelete = true; open = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete project?") },
            text = { Text("This removes the project and all its photos. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
