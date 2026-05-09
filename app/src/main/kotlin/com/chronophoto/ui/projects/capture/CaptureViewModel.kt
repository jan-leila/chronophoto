package com.chronophoto.ui.projects.capture

import android.content.Context
import android.location.Location
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronophoto.data.capture.CameraCapabilities
import com.chronophoto.data.capture.ChronoCameraController
import com.chronophoto.data.capture.LiveCameraReadout
import com.chronophoto.data.capture.MetadataCollector
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.data.repo.PhotoRepository
import com.chronophoto.data.repo.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CaptureUiState(
    val projectLoaded: Boolean = false,
    val storeAmbientBrightness: Boolean = false,
    val storeCameraSettings: Boolean = false,
    val storeGps: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val readout: LiveCameraReadout = LiveCameraReadout(),
    val ambientLux: Float? = null,
    val liveLocation: Location? = null,
    val lastPhoto: Photo? = null,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val frontAvailable: Boolean = false,
    val backAvailable: Boolean = true,
    val manualMode: Boolean = false,
    val capabilities: CameraCapabilities = CameraCapabilities(),
    val targetIso: Int? = null,
    val targetExposureNs: Long? = null,
    val targetAwbMode: Int? = null,
) {
    val canMatchLast: Boolean
        get() = lastPhoto != null && capabilities.supportsManualSensor
}

data class CaptureTarget(val sequence: Int, val file: File)

class CaptureViewModel(
    private val projectId: Long,
    private val photos: PhotoRepository,
    private val projects: ProjectRepository,
    appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var collector: MetadataCollector? = null
    val controller = ChronoCameraController(appContext)

    init {
        viewModelScope.launch {
            val project = projects.getById(projectId) ?: return@launch
            _state.update {
                it.copy(
                    projectLoaded = true,
                    storeAmbientBrightness = project.storeAmbientBrightness,
                    storeCameraSettings = project.storeCameraSettings,
                    storeGps = project.storeGps,
                )
            }
            val coll = MetadataCollector(
                context = appContext,
                collectAmbientLight = project.storeAmbientBrightness,
                collectLocation = project.storeGps,
                collectCameraSettings = project.storeCameraSettings,
            ).also { it.start() }
            collector = coll
            launch {
                coll.luxFlow.collect { lux ->
                    _state.update { it.copy(ambientLux = lux) }
                }
            }
            launch {
                coll.locationFlow.collect { loc ->
                    _state.update { it.copy(liveLocation = loc) }
                }
            }
        }

        viewModelScope.launch {
            controller.readout.collect { r -> _state.update { it.copy(readout = r) } }
        }
        viewModelScope.launch {
            controller.facing.collect { f -> _state.update { it.copy(lensFacing = f) } }
        }
        viewModelScope.launch {
            controller.frontAvailable.collect { v -> _state.update { it.copy(frontAvailable = v) } }
        }
        viewModelScope.launch {
            controller.backAvailable.collect { v -> _state.update { it.copy(backAvailable = v) } }
        }
        viewModelScope.launch {
            controller.capabilities.collect { c -> _state.update { it.copy(capabilities = c) } }
        }
        viewModelScope.launch {
            controller.manualMode.collect { m -> _state.update { it.copy(manualMode = m) } }
        }
        viewModelScope.launch {
            photos.observeLatest(projectId).collect { p -> _state.update { it.copy(lastPhoto = p) } }
        }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            runCatching { controller.bind(lifecycleOwner, previewView) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Camera bind failed") } }
        }
    }

    fun unbindCamera() {
        controller.unbind()
    }

    fun toggleFacing() {
        val next = if (state.value.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        if (next == CameraSelector.LENS_FACING_FRONT && !state.value.frontAvailable) return
        if (next == CameraSelector.LENS_FACING_BACK && !state.value.backAvailable) return
        _state.update { it.copy(targetIso = null, targetExposureNs = null, targetAwbMode = null) }
        controller.setFacing(next)
    }

    fun setManualMode(enabled: Boolean) {
        if (enabled && !state.value.manualMode) {
            val r = state.value.readout
            _state.update {
                it.copy(
                    targetIso = it.targetIso ?: r.iso,
                    targetExposureNs = it.targetExposureNs ?: r.exposureTimeNs,
                )
            }
            state.value.targetIso?.let { controller.setIso(it) }
            state.value.targetExposureNs?.let { controller.setExposureTimeNs(it) }
        }
        controller.setManualMode(enabled)
    }

    fun setIso(iso: Int) {
        _state.update { it.copy(targetIso = iso) }
        controller.setIso(iso)
    }

    fun setExposureNs(ns: Long) {
        _state.update { it.copy(targetExposureNs = ns) }
        controller.setExposureTimeNs(ns)
    }

    fun setAwbMode(mode: Int) {
        _state.update { it.copy(targetAwbMode = mode) }
        controller.setAwbMode(mode)
    }

    fun matchLast() {
        val cs = state.value.lastPhoto?.cameraSettings ?: return
        controller.applyTemplate(cs)
        _state.update {
            it.copy(
                targetIso = cs.iso ?: it.targetIso,
                targetExposureNs = cs.exposureTimeNs ?: it.targetExposureNs,
                targetAwbMode = ChronoCameraController.awbModeFromName(cs.whiteBalanceMode) ?: it.targetAwbMode,
            )
        }
    }

    fun capture() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val seq = photos.nextSequence(projectId)
                val file = photos.fileFor(projectId, seq)
                controller.capture(file)
                val metadata = collector?.snapshot(file, controller.facing.value) ?: error("Collector not ready")
                photos.recordCapture(projectId, seq, file, metadata = metadata)
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.message ?: "Capture failed") }
                }
        }
    }

    override fun onCleared() {
        controller.unbind()
        collector?.stop()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context, projectId: Long) = viewModelFactory {
            initializer {
                CaptureViewModel(
                    projectId = projectId,
                    photos = PhotoRepository.from(context),
                    projects = ProjectRepository.from(context),
                    appContext = context.applicationContext,
                )
            }
        }
    }
}
