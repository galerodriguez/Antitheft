package com.sinaptic.centinela.commands

import android.content.Context
import android.util.Log
import com.sinaptic.centinela.data.DeviceRepository

/**
 * Captura una foto con la cámara frontal cuando hay señales de robo (varios PIN fallidos,
 * o comando remoto PHOTO) y la sube al backend.
 *
 * Implementación real: usar CameraX en un servicio en primer plano de corta duración.
 * Se deja el flujo cableado y la subida lista; el pipeline de CameraX (ImageCapture ->
 * archivo temporal) se completa según el hardware objetivo.
 */
class IntruderPhotoCommand(private val context: Context) {

    fun capture(requestId: String) {
        if (!isAntiTheftEnabled()) {
            Log.i(TAG, "Función antirrobo deshabilitada por el usuario; no se captura foto")
            return
        }
        Log.i(TAG, "Capturando foto de intruso ($requestId)")
        // Lanza el servicio en primer plano (tipo cámara) que toma la foto y la sube.
        runCatching { CaptureService.start(context, requestId) }
            .onFailure { Log.w(TAG, "No se pudo iniciar la captura", it) }
    }

    /** Respeta la preferencia del usuario: la captura antirrobo es opt-in. */
    private fun isAntiTheftEnabled(): Boolean =
        DeviceRepository(context).prefs().getBoolean("antitheft_photo_enabled", false)

    companion object { private const val TAG = "IntruderPhoto" }
}
