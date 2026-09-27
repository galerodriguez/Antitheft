package com.sinaptic.centinela.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat

/**
 * Captura UNA foto con la cámara frontal SIN vista previa (headless), usando Camera2.
 * Pensada para correr desde un servicio en primer plano (tipo cámara). Best-effort:
 * si algo falla, devuelve null y no rompe nada.
 */
class IntruderCamera(private val context: Context) {

    /** Captura una ráfaga de `count` fotos del stream (sin sonido) y las devuelve juntas. */
    fun capture(count: Int = 3, onResult: (List<ByteArray>) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) { onResult(emptyList()); return }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = frontCameraId(manager) ?: run {
            Log.w(TAG, "No hay cámara frontal"); onResult(emptyList()); return
        }
        val thread = HandlerThread("intruder-cam").apply { start() }
        val handler = Handler(thread.looper)

        val chars = manager.getCameraCharacteristics(camId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
        val size = sizes.filter { it.width <= 1280 }.maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.firstOrNull() ?: Size(640, 480)

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 3)
        var camera: CameraDevice? = null
        var done = false
        val shots = ArrayList<ByteArray>()
        fun finish() {
            if (done) return
            done = true
            runCatching { camera?.close() }
            runCatching { reader.close() }
            runCatching { thread.quitSafely() }
            onResult(shots)
        }

        var frames = 0
        reader.setOnImageAvailableListener({ r ->
            runCatching {
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                frames++
                // Descartamos los primeros cuadros (exposición) y después tomamos 1 de cada 2.
                if (frames < 4 || (frames % 2 == 1)) { img.close(); return@setOnImageAvailableListener }
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                img.close()
                shots.add(bytes)
                if (shots.size >= count) finish()
            }.onFailure { if (shots.isNotEmpty()) finish() }
        }, handler)

        try {
            manager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    runCatching {
                        // Usamos TEMPLATE_PREVIEW + repeating: capturamos un cuadro del stream,
                        // sin "disparo" de obturador → SIN sonido de cámara.
                        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        req.addTarget(reader.surface)
                        device.createCaptureSession(listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    runCatching { session.setRepeatingRequest(req.build(), null, handler) }
                                        .onFailure { finish() }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) = finish()
                            }, handler)
                    }.onFailure { finish() }
                }
                override fun onDisconnected(device: CameraDevice) { runCatching { device.close() }; finish() }
                override fun onError(device: CameraDevice, error: Int) { runCatching { device.close() }; finish() }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera", e); finish()
        }

        // Red de seguridad: cerramos a los 8 s con lo que haya (ráfaga puede tardar un poco más).
        handler.postDelayed({ finish() }, 8000)
    }

    private fun frontCameraId(manager: CameraManager): String? {
        return runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList.firstOrNull()
        }.getOrNull()
    }

    companion object { private const val TAG = "IntruderCamera" }
}
