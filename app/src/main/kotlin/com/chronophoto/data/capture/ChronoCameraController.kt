package com.chronophoto.data.capture

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.chronophoto.data.db.entity.CameraSettings
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CameraCapabilities(
    val supportsManualSensor: Boolean = false,
    val isoRange: IntRange? = null,
    val exposureNsRange: LongRange? = null,
    val supportedAwbModes: List<Int> = emptyList(),
)

private data class ManualSettings(
    val aeOff: Boolean = false,
    val awbOff: Boolean = false,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val awbMode: Int? = null,
)

class ChronoCameraController(private val context: Context) {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private val _readout = MutableStateFlow(LiveCameraReadout())
    val readout: StateFlow<LiveCameraReadout> = _readout.asStateFlow()

    private val _facing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val facing: StateFlow<Int> = _facing.asStateFlow()

    private val _frontAvailable = MutableStateFlow(false)
    val frontAvailable: StateFlow<Boolean> = _frontAvailable.asStateFlow()

    private val _backAvailable = MutableStateFlow(true)
    val backAvailable: StateFlow<Boolean> = _backAvailable.asStateFlow()

    private val _capabilities = MutableStateFlow(CameraCapabilities())
    val capabilities: StateFlow<CameraCapabilities> = _capabilities.asStateFlow()

    private val _manualMode = MutableStateFlow(false)
    val manualMode: StateFlow<Boolean> = _manualMode.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var owner: LifecycleOwner? = null
    private var preview: PreviewView? = null
    private var manual = ManualSettings()

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.owner = lifecycleOwner
        this.preview = previewView
        val p = ProcessCameraProvider.getInstance(context).await(mainExecutor)
        provider = p
        _frontAvailable.value = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        _backAvailable.value = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        rebind()
    }

    fun unbind() {
        provider?.unbindAll()
        provider = null
        imageCapture = null
        camera = null
        owner = null
        preview = null
        manual = ManualSettings()
        _manualMode.value = false
        _readout.value = LiveCameraReadout()
        _capabilities.value = CameraCapabilities()
    }

    fun setFacing(facing: Int) {
        if (_facing.value == facing) return
        _facing.value = facing
        manual = ManualSettings()
        _manualMode.value = false
        rebind()
    }

    suspend fun capture(file: File): ImageCapture.OutputFileResults {
        val ic = imageCapture ?: error("Camera is not bound")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        return suspendCancellableCoroutine { cont ->
            ic.takePicture(opts, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    cont.resume(results)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            })
        }
    }

    fun setManualMode(enabled: Boolean) {
        manual = manual.copy(aeOff = enabled, awbOff = enabled)
        _manualMode.value = enabled
        pushManual()
    }

    fun setIso(iso: Int) {
        manual = manual.copy(iso = iso)
        pushManual()
    }

    fun setExposureTimeNs(ns: Long) {
        manual = manual.copy(exposureNs = ns)
        pushManual()
    }

    fun setAwbMode(mode: Int) {
        manual = manual.copy(awbMode = mode)
        pushManual()
    }

    fun applyTemplate(template: CameraSettings) {
        manual = ManualSettings(
            aeOff = true,
            awbOff = true,
            iso = template.iso,
            exposureNs = template.exposureTimeNs,
            awbMode = awbModeFromName(template.whiteBalanceMode),
        )
        _manualMode.value = true
        pushManual()
    }

    private fun rebind() {
        val p = provider ?: return
        val o = owner ?: return
        val pv = preview ?: return

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(captureCallback)
        val previewUseCase = previewBuilder.build().also {
            it.surfaceProvider = pv.surfaceProvider
        }
        val capture = ImageCapture.Builder().build()

        val selector = if (_facing.value == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        p.unbindAll()
        camera = p.bindToLifecycle(o, selector, previewUseCase, capture)
        imageCapture = capture
        _readout.value = LiveCameraReadout()
        readCapabilities()
    }

    private fun pushManual() {
        val cam = camera ?: return
        val builder = CaptureRequestOptions.Builder()
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            if (manual.aeOff) CaptureRequest.CONTROL_AE_MODE_OFF else CaptureRequest.CONTROL_AE_MODE_ON,
        )
        val awbMode = manual.awbMode
            ?: if (manual.awbOff) CaptureRequest.CONTROL_AWB_MODE_OFF else CaptureRequest.CONTROL_AWB_MODE_AUTO
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
        if (manual.aeOff) {
            manual.iso?.let { builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it) }
            manual.exposureNs?.let {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
            }
        }
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(builder.build())
    }

    private fun readCapabilities() {
        val cam = camera ?: return
        val cameraId = runCatching { Camera2CameraInfo.from(cam.cameraInfo).cameraId }.getOrNull() ?: return
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val ch = runCatching { mgr.getCameraCharacteristics(cameraId) }.getOrNull() ?: return
        val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val supportsManual = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val iso = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposure = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val awbModes = ch.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.map { it.toInt() }
            .orEmpty()
        _capabilities.value = CameraCapabilities(
            supportsManualSensor = supportsManual,
            isoRange = iso?.let { it.lower..it.upper },
            exposureNsRange = exposure?.let { it.lower..it.upper },
            supportedAwbModes = awbModes,
        )
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            _readout.value = LiveCameraReadout(
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                focalLengthMm = result.get(CaptureResult.LENS_FOCAL_LENGTH),
                apertureFNumber = result.get(CaptureResult.LENS_APERTURE),
                whiteBalanceMode = result.get(CaptureResult.CONTROL_AWB_MODE)?.let(::awbName),
                focusDistanceDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            )
        }
    }

    companion object {
        fun awbName(mode: Int): String = when (mode) {
            CaptureRequest.CONTROL_AWB_MODE_AUTO -> "auto"
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "incandescent"
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "fluorescent"
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "warm fluorescent"
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "daylight"
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "cloudy"
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "twilight"
            CaptureRequest.CONTROL_AWB_MODE_SHADE -> "shade"
            CaptureRequest.CONTROL_AWB_MODE_OFF -> "manual"
            else -> "mode $mode"
        }

        fun awbModeFromName(name: String?): Int? = when (name) {
            "auto" -> CaptureRequest.CONTROL_AWB_MODE_AUTO
            "incandescent" -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
            "fluorescent" -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
            "warm fluorescent" -> CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT
            "daylight" -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
            "cloudy" -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            "twilight" -> CaptureRequest.CONTROL_AWB_MODE_TWILIGHT
            "shade" -> CaptureRequest.CONTROL_AWB_MODE_SHADE
            "manual" -> CaptureRequest.CONTROL_AWB_MODE_OFF
            else -> null
        }
    }
}

private suspend fun <T> ListenableFuture<T>.await(executor: Executor): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }, executor)
    }
