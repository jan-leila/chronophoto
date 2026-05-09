package com.chronophoto.data.capture

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var owner: LifecycleOwner? = null
    private var preview: PreviewView? = null

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
        owner = null
        preview = null
        _readout.value = LiveCameraReadout()
    }

    fun setFacing(facing: Int) {
        if (_facing.value == facing) return
        _facing.value = facing
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
        p.bindToLifecycle(o, selector, previewUseCase, capture)
        imageCapture = capture
        _readout.value = LiveCameraReadout()
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

    private fun awbName(mode: Int): String = when (mode) {
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
