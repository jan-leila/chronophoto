package com.chronophoto.ui.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chronophoto.data.db.entity.CameraSettings
import com.chronophoto.data.db.entity.GpsLocation
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.data.db.entity.Project
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photoId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: PhotoDetailViewModel = viewModel(
        factory = PhotoDetailViewModel.factory(context, photoId),
        key = "photo-$photoId",
    )
    val state by vm.uiState.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val seq = state.photo?.sequence
                    Text(if (seq != null) "Photo ${seq + 1}" else "Photo")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.photo != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> Loading(padding)
            state.photo == null -> Missing(padding)
            else -> Body(padding, state.photo!!, state.previous, state.project)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photo?") },
            text = { Text("The image file and its metadata will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Loading(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text("Loading…")
    }
}

@Composable
private fun Missing(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text("Photo not found.")
    }
}

@Composable
private fun Body(
    padding: PaddingValues,
    photo: Photo,
    previous: Photo?,
    project: Project?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = "Photo ${photo.sequence + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        )

        SectionCard(title = "Capture") {
            FieldRow(
                label = "Sequence",
                value = "${photo.sequence + 1}",
            )
            FieldRow(
                label = "Captured at",
                value = PhotoFormat.date(photo.capturedAt),
            )
        }

        if (project?.storeCameraSettings != false && hasAnyCameraData(photo.cameraSettings)) {
            SectionCard(title = "Camera") {
                CameraSection(photo.cameraSettings, previous?.cameraSettings)
            }
        }

        if (project?.storeAmbientBrightness != false && photo.ambientLux != null) {
            SectionCard(title = "Light") {
                FieldRow(
                    label = "Ambient",
                    value = PhotoFormat.lux(photo.ambientLux),
                    diff = PhotoFormat.luxDiff(photo.ambientLux, previous?.ambientLux),
                )
            }
        }

        if (project?.storeGps != false && hasGpsData(photo.gpsLocation)) {
            SectionCard(title = "Location") {
                LocationSection(photo.gpsLocation, previous?.gpsLocation)
            }
        }
    }
}

@Composable
private fun CameraSection(now: CameraSettings, prev: CameraSettings?) {
    FieldRow(
        label = "ISO",
        value = PhotoFormat.iso(now.iso),
        diff = PhotoFormat.isoDiff(now.iso, prev?.iso),
    )
    FieldRow(
        label = "Exposure",
        value = PhotoFormat.exposure(now.exposureTimeNs),
        diff = PhotoFormat.exposureDiff(now.exposureTimeNs, prev?.exposureTimeNs),
    )
    FieldRow(
        label = "Focal length",
        value = PhotoFormat.focalLength(now.focalLengthMm),
        diff = PhotoFormat.focalLengthDiff(now.focalLengthMm, prev?.focalLengthMm),
    )
    FieldRow(
        label = "Aperture",
        value = PhotoFormat.aperture(now.apertureFNumber),
    )
    FieldRow(
        label = "White balance",
        value = PhotoFormat.whiteBalance(now.whiteBalanceMode),
        diff = PhotoFormat.whiteBalanceDiff(now.whiteBalanceMode, prev?.whiteBalanceMode),
    )
    FieldRow(
        label = "Lens",
        value = PhotoFormat.lensFacing(now.lensFacing),
    )
}

@Composable
private fun LocationSection(now: GpsLocation, prev: GpsLocation?) {
    FieldRow(
        label = "Position",
        value = PhotoFormat.gpsLatLng(now),
        diff = prev?.let { PhotoFormat.gpsDiff(now, it) },
    )
    FieldRow(
        label = "Altitude",
        value = PhotoFormat.altitude(now.altitudeM),
    )
    FieldRow(
        label = "Accuracy",
        value = PhotoFormat.accuracy(now.accuracyM),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, diff: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
            if (diff != null) {
                Text(
                    text = diff,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun hasAnyCameraData(cs: CameraSettings): Boolean =
    cs.iso != null ||
        cs.exposureTimeNs != null ||
        cs.focalLengthMm != null ||
        cs.apertureFNumber != null ||
        cs.whiteBalanceMode != null ||
        cs.lensFacing != null

private fun hasGpsData(gps: GpsLocation): Boolean =
    gps.latitude != null || gps.longitude != null || gps.altitudeM != null || gps.accuracyM != null
