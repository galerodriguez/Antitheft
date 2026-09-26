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

    fun capture(onResult: (ByteArray?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) { onResult(null); return }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = frontCameraId(manager) ?: run {
            Log.w(TAG, "No hay cámara frontal"); onResult(null); return
        }
        val thread = HandlerThread("intruder-cam").apply { start() }
        val handler = Handler(thread.looper)

        val chars = manager.getCameraCharacteristics(camId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
        val size = sizes.filter { it.width <= 1280 }.maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.firstOrNull() ?: Size(640, 480)

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        var camera: CameraDevice? = null
        var done = false
        fun finish(bytes: ByteArray?) {
            if (done) return
            done = true
            runCatching { camera?.close() }
            runCatching { reader.close() }
            runCatching { thread.quitSafely() }
            onResult(bytes)
        }

        reader.setOnImageAvailableListener({ r ->
            runCatching {
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                img.close()
                finish(bytes)
            }.onFailure { finish(null) }
        }, handler)

        try {
            manager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    runCatching {
                        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        req.addTarget(reader.surface)
                        device.createCaptureSession(listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    // pequeño retardo para que el sensor ajuste exposición
                                    handler.postDelayed({
                                        runCatching { session.capture(req.build(), null, handler) }
                                            .onFailure { finish(null) }
                                    }, 500)
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) = finish(null)
                            }, handler)
                    }.onFailure { finish(null) }
                }
                override fun onDisconnected(device: CameraDevice) { runCatching { device.close() }; finish(null) }
                override fun onError(device: CameraDevice, error: Int) { runCatching { device.close() }; finish(null) }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera", e); finish(null)
        }

        // Red de seguridad: si en 6 s no hubo foto, cerramos todo.
        handler.postDelayed({ finish(null) }, 6000)
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
