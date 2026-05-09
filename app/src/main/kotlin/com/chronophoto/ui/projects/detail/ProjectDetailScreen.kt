package com.chronophoto.ui.projects.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronophoto.data.db.entity.Project
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit,
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
                    OverflowMenu(
                        archived = state.project?.archivedAt != null,
                        onArchive = vm::archive,
                        onUnarchive = vm::unarchive,
                        onDelete = vm::delete,
                    )
                },
            )
        },
    ) { padding ->
        val project = state.project
        if (project == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            project.description?.let { Text(it) }
            Text(
                text = buildString {
                    append("Created ")
                    append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.createdAt)))
                    if (project.archivedAt != null) {
                        append(" • archived ")
                        append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.archivedAt)))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )

            MetadataCard(project)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Photos", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "No photos yet — capture flow comes in Phase 1 step 3.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
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
) {
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
