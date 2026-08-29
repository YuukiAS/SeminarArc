package com.yuukias.seminararc.media.camera

import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface PhotoCaptureResult {
    data object Saved : PhotoCaptureResult
    data class Failed(val message: String) : PhotoCaptureResult
}

class CameraXPhotoCaptureController(
    private val context: Context,
) {
    val previewView: PreviewView = PreviewView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { cameraPreview ->
                    cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                    .build()
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    cameraProvider = provider
                    imageCapture = capture
                } catch (_: SecurityException) {
                    imageCapture = null
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    suspend fun capture(file: File): PhotoCaptureResult {
        val capture = imageCapture ?: return PhotoCaptureResult.Failed("Camera is not ready.")
        capture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(PhotoCaptureResult.Saved)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(
                            PhotoCaptureResult.Failed(exception.message ?: "Camera capture failed."),
                        )
                    }
                },
            )
        }
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }
}

@Composable
fun rememberCameraXPhotoCaptureController(): CameraXPhotoCaptureController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { CameraXPhotoCaptureController(context) }
    DisposableEffect(controller, lifecycleOwner) {
        controller.bind(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    return controller
}

@Composable
fun CameraXSlidePreview(
    controller: CameraXPhotoCaptureController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { controller.previewView },
        modifier = modifier.fillMaxWidth(),
    )
}
