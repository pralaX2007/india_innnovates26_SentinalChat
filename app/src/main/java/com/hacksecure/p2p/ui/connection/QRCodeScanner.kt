package com.hacksecure.p2p.ui.connection

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QRCodeScanner(
    private val context: Context,
    private val onQRCodeDetected: (String) -> Unit
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val alreadyFired = AtomicBoolean(false)  // prevent duplicate handshakes

    @SuppressLint("UnsafeOptInUsageError")
    fun startScanner(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(cameraExecutor, QRAnalyzer { result ->
                // Fire callback only once per scanner session
                if (alreadyFired.compareAndSet(false, true)) {
                    onQRCodeDetected(result)
                }
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun stopScanner() {
        cameraExecutor.shutdown()
    }

    private class QRAnalyzer(val callback: (String) -> Unit) : ImageAnalysis.Analyzer {

        private val reader = MultiFormatReader()

        override fun analyze(imageProxy: ImageProxy) {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val source = PlanarYUVLuminanceSource(
                bytes, imageProxy.width, imageProxy.height,
                0, 0, imageProxy.width, imageProxy.height, false
            )

            val bitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(bitmap)
                callback(result.text)
            } catch (_: NotFoundException) {
                // No QR in this frame — normal
            } finally {
                imageProxy.close()
            }
        }
    }
}
