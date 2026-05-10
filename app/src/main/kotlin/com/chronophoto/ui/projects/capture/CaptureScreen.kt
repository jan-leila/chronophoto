package com.chronophoto.ui.projects.capture

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chronophoto.data.capture.CameraCapabilities
import com.chronophoto.data.capture.ChronoCameraController
import com.chronophoto.data.capture.LiveCameraReadout
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.ui.photo.PhotoFormat
import java.io.File
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
                    onSetManualMode = vm::setManualMode,
                    onSetIso = vm::setIso,
                    onSetExposureNs = vm::setExposureNs,
                    onSetAwbMode = vm::setAwbMode,
                    onMatchLast = vm::matchLast,
                    onCommitPending = vm::commitPending,
                    onDiscardPending = vm::discardPending,
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
    onSetManualMode: (Boolean) -> Unit,
    onSetIso: (Int) -> Unit,
    onSetExposureNs: (Long) -> Unit,
    onSetAwbMode: (Int) -> Unit,
    onMatchLast: () -> Unit,
    onCommitPending: () -> Unit,
    onDiscardPending: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var ghostOpacity by remember { mutableStateOf(0.3f) }
    var controlsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(previewView) { onBindCamera(lifecycleOwner, previewView) }
    DisposableEffect(Unit) { onDispose { onUnbindCamera() } }

    BackHandler(enabled = state.pendingCapture != null) {
        onDiscardPending()
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.pendingCapture == null) {
            state.lastPhoto?.let { last ->
                val mirror = state.lensFacing == CameraSelector.LENS_FACING_FRONT
                AsyncImage(
                    model = File(last.filePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(ghostOpacity)
                        .graphicsLayer { scaleX = if (mirror) -1f else 1f },
                )
            }
        }

        MetadataStrip(
            readout = state.readout,
            ambientLux = state.ambientLux,
            liveLocation = state.liveLocation,
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

        if (state.pendingCapture == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (controlsExpanded) {
                    ControlPanel(
                        state = state,
                        ghostOpacity = ghostOpacity,
                        onGhostOpacityChange = { ghostOpacity = it },
                        onSetManualMode = onSetManualMode,
                        onSetIso = onSetIso,
                        onSetExposureNs = onSetExposureNs,
                        onSetAwbMode = onSetAwbMode,
                        onMatchLast = onMatchLast,
                        onCollapse = { controlsExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    IconButton(
                        onClick = { controlsExpanded = true },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.ExpandLess,
                            contentDescription = "Show controls",
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FloatingActionButton(
                    onClick = onCapture,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Capture")
                }
            }
        } else {
            ReviewOverlay(
                pending = state.pendingCapture!!,
                lastPhoto = state.lastPhoto,
                lensFacing = state.lensFacing,
                saving = state.saving,
                onSave = onCommitPending,
                onRetake = onDiscardPending,
            )
        }
    }
}

@Composable
private fun ReviewOverlay(
    pending: PendingCapture,
    lastPhoto: com.chronophoto.data.db.entity.Photo?,
    lensFacing: Int,
    saving: Boolean,
    onSave: () -> Unit,
    onRetake: () -> Unit,
) {
    var showLast by remember { mutableStateOf(false) }
    val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val displayFile = if (showLast && lastPhoto != null) File(lastPhoto.filePath) else pending.file
        AsyncImage(
            model = displayFile,
            contentDescription = if (showLast) "Previous photo" else "New photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (mirror) -1f else 1f },
        )
        Text(
            text = if (showLast) "Last photo" else "New photo",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (lastPhoto != null) {
            OutlinedButton(
                onClick = { showLast = !showLast },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            ) {
                Text(if (showLast) "Show new" else "Show last")
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = onRetake, enabled = !saving) { Text("Retake") }
            Button(onClick = onSave, enabled = !saving) {
                Text(if (saving) "Saving…" else "Save")
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: CaptureUiState,
    ghostOpacity: Float,
    onGhostOpacityChange: (Float) -> Unit,
    onSetManualMode: (Boolean) -> Unit,
    onSetIso: (Int) -> Unit,
    onSetExposureNs: (Long) -> Unit,
    onSetAwbMode: (Int) -> Unit,
    onMatchLast: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showAny = state.lastPhoto != null || state.capabilities.supportsManualSensor
    if (!showAny) return

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.height(32.dp)) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Hide controls",
                        tint = Color.White,
                    )
                }
            }
            if (state.lastPhoto != null) {
                LabeledSlider(
                    label = "ghost · ${(ghostOpacity * 100).roundToInt()}%",
                    value = ghostOpacity,
                    onValueChange = onGhostOpacityChange,
                    valueRange = 0f..1f,
                )
            }
            if (state.capabilities.supportsManualSensor) {
                ManualModeRow(
                    manualMode = state.manualMode,
                    canMatchLast = state.canMatchLast,
                    onSetManualMode = onSetManualMode,
                    onMatchLast = onMatchLast,
                )
                if (state.manualMode) {
                    IsoSlider(
                        target = state.targetIso,
                        range = state.capabilities.isoRange,
                        readoutIso = state.readout.iso,
                        onChange = onSetIso,
                    )
                    ExposureSlider(
                        target = state.targetExposureNs,
                        range = state.capabilities.exposureNsRange,
                        readoutNs = state.readout.exposureTimeNs,
                        onChange = onSetExposureNs,
                    )
                    AwbChips(
                        selected = state.targetAwbMode,
                        supported = state.capabilities.supportedAwbModes,
                        onSelect = onSetAwbMode,
                    )
                }
            } else if (state.lastPhoto != null) {
                Text(
                    text = "Manual sensor control isn't supported on this camera.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ManualModeRow(
    manualMode: Boolean,
    canMatchLast: Boolean,
    onSetManualMode: (Boolean) -> Unit,
    onMatchLast: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (manualMode) "Manual" else "Auto",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = manualMode,
            onCheckedChange = onSetManualMode,
        )
        Spacer(Modifier.weight(1f))
        if (canMatchLast) {
            OutlinedButton(onClick = onMatchLast) { Text("Match last") }
        }
    }
}

@Composable
private fun IsoSlider(
    target: Int?,
    range: IntRange?,
    readoutIso: Int?,
    onChange: (Int) -> Unit,
) {
    val current = target ?: readoutIso ?: range?.first ?: 100
    val min = range?.first ?: 50
    val max = range?.last ?: 6400
    val logMin = log2(min.toFloat())
    val logMax = log2(max.toFloat())
    val logVal = log2(current.toFloat()).coerceIn(logMin, logMax)
    LabeledSlider(
        label = "ISO $current",
        value = logVal,
        onValueChange = { v ->
            val next = 2f.pow(v).roundToInt().coerceIn(min, max)
            if (next != current) onChange(next)
        },
        valueRange = logMin..logMax,
    )
}

@Composable
private fun ExposureSlider(
    target: Long?,
    range: LongRange?,
    readoutNs: Long?,
    onChange: (Long) -> Unit,
) {
    val current = target ?: readoutNs ?: range?.first ?: 4_000_000L
    val min = range?.first ?: 125_000L
    val max = range?.last ?: 1_000_000_000L
    val logMin = log2(min.toFloat())
    val logMax = log2(max.toFloat())
    val logVal = log2(current.toFloat()).coerceIn(logMin, logMax)
    LabeledSlider(
        label = "exposure · ${PhotoFormat.exposure(current)}",
        value = logVal,
        onValueChange = { v ->
            val next = 2f.pow(v).toDouble().roundToLong().coerceIn(min, max)
            if (next != current) onChange(next)
        },
        valueRange = logMin..logMax,
    )
}

@Composable
private fun AwbChips(
    selected: Int?,
    supported: List<Int>,
    onSelect: (Int) -> Unit,
) {
    if (supported.isEmpty()) return
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        supported.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(ChronoCameraController.awbName(mode)) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Column {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun MetadataStrip(
    readout: LiveCameraReadout,
    ambientLux: Float?,
    liveLocation: Location?,
    lastPhoto: Photo?,
    modifier: Modifier = Modifier,
) {
    val live = formatReadoutLine(readout, ambientLux)
    val previous = lastPhoto?.let { formatPhotoLine(it) }
    val delta = lastPhoto?.let { formatDeltaLine(ambientLux, liveLocation, it) }

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
            if (delta != null) {
                Text(
                    text = "Δ · $delta",
                    color = Color(0xFFFFC107),
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

private fun formatDeltaLine(
    liveLux: Float?,
    liveLocation: Location?,
    last: Photo,
): String? {
    val luxDelta = PhotoFormat.luxDiff(liveLux, last.ambientLux)?.let {
        // PhotoFormat.luxDiff returns "+10.5 lux from previous"; trim suffix
        it.removeSuffix(" from previous")
    }
    val gpsDelta = formatGpsLiveDelta(liveLocation, last)
    val parts = listOfNotNull(luxDelta, gpsDelta)
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun formatGpsLiveDelta(live: Location?, last: Photo): String? {
    val nowLat = live?.latitude ?: return null
    val nowLng = live.longitude
    val prevLat = last.gpsLocation.latitude ?: return null
    val prevLng = last.gpsLocation.longitude ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(prevLat, prevLng, nowLat, nowLng, out)
    val meters = out[0]
    return when {
        meters < 1f -> null
        meters < 1_000f -> "%.0f m from last".format(meters)
        else -> "%.2f km from last".format(meters / 1_000f)
    }
}
