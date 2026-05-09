package com.chronophoto.ui.projects.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chronophoto.data.capture.LiveCameraReadout
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.ui.photo.PhotoFormat
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    projectId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: CaptureViewModel = viewModel(
        factory = CaptureViewModel.factory(context, projectId),
        key = "capture-$projectId",
    )
    val state by vm.state.collectAsState()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        result[Manifest.permission.CAMERA]?.let { hasCamera = it }
        result[Manifest.permission.ACCESS_FINE_LOCATION]?.let { hasLocation = it }
    }

    LaunchedEffect(state.projectLoaded, state.storeGps) {
        if (!state.projectLoaded) return@LaunchedEffect
        val toRequest = buildList {
            if (!hasCamera) add(Manifest.permission.CAMERA)
            if (state.storeGps && !hasLocation) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (toRequest.isNotEmpty()) launcher.launch(toRequest.toTypedArray())
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture photo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.frontAvailable && state.backAvailable) {
                        IconButton(onClick = vm::toggleFacing) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.projectLoaded -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Loading…")
                }
            }
            !hasCamera -> {
                PermissionRationale(
                    padding = padding,
                    text = "Camera permission is needed to capture photos.",
                ) {
                    launcher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            }
            else -> {
                CameraSurface(
                    padding = padding,
                    state = state,
                    locationGranted = hasLocation,
                    onCapture = vm::capture,
                    onBindCamera = vm::bindCamera,
                    onUnbindCamera = vm::unbindCamera,
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(padding: PaddingValues, text: String, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@Composable
private fun CameraSurface(
    padding: PaddingValues,
    state: CaptureUiState,
    locationGranted: Boolean,
    onCapture: () -> Unit,
    onBindCamera: (androidx.lifecycle.LifecycleOwner, PreviewView) -> Unit,
    onUnbindCamera: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var ghostOpacity by remember { mutableStateOf(0.3f) }

    LaunchedEffect(previewView) { onBindCamera(lifecycleOwner, previewView) }
    DisposableEffect(Unit) { onDispose { onUnbindCamera() } }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        state.lastPhoto?.let { last ->
            AsyncImage(
                model = File(last.filePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(ghostOpacity),
            )
        }

        MetadataStrip(
            readout = state.readout,
            ambientLux = state.ambientLux,
            lastPhoto = state.lastPhoto,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
        if (state.storeGps && !locationGranted) {
            Text(
                text = "Location permission denied — GPS won't be saved.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.lastPhoto != null) {
                GhostOpacitySlider(
                    opacity = ghostOpacity,
                    onChange = { ghostOpacity = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            FloatingActionButton(
                onClick = onCapture,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Capture")
            }
        }
    }
}

@Composable
private fun GhostOpacitySlider(
    opacity: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column {
            Text(
                text = "ghost · ${(opacity * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Slider(
                value = opacity,
                onValueChange = onChange,
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
private fun MetadataStrip(
    readout: LiveCameraReadout,
    ambientLux: Float?,
    lastPhoto: Photo?,
    modifier: Modifier = Modifier,
) {
    val live = formatReadoutLine(readout, ambientLux)
    val previous = lastPhoto?.let { formatPhotoLine(it) }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = "live · $live",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            if (previous != null) {
                Text(
                    text = "last · $previous",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatReadoutLine(r: LiveCameraReadout, lux: Float?): String {
    val parts = buildList {
        r.iso?.let { add("ISO $it") }
        if (r.exposureTimeNs != null) add(PhotoFormat.exposure(r.exposureTimeNs))
        r.focalLengthMm?.let { add(PhotoFormat.focalLength(it)) }
        r.apertureFNumber?.let { add(PhotoFormat.aperture(it)) }
        r.whiteBalanceMode?.let { add("AWB $it") }
        lux?.let { add(PhotoFormat.lux(it)) }
    }
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

private fun formatPhotoLine(photo: Photo): String {
    val cs = photo.cameraSettings
    val parts = buildList {
        cs.iso?.let { add("ISO $it") }
        cs.exposureTimeNs?.let { add(PhotoFormat.exposure(it)) }
        cs.focalLengthMm?.let { add(PhotoFormat.focalLength(it)) }
        cs.apertureFNumber?.let { add(PhotoFormat.aperture(it)) }
        cs.whiteBalanceMode?.let { add("AWB $it") }
        photo.ambientLux?.let { add(PhotoFormat.lux(it)) }
    }
    return if (parts.isEmpty()) "no metadata" else parts.joinToString(" · ")
}
