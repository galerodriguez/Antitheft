package com.sinaptic.centinela.commands

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sinaptic.centinela.location.LocationService

/**
 * Traduce el nombre de un comando remoto a la acción concreta en el dispositivo.
 * Punto único de entrada para push (FCM), SOS local y eventos de admin.
 */
class CommandDispatcher(private val context: Context) {

    fun dispatch(command: String, requestId: String, params: Map<String, String>) {
        when (command.uppercase()) {
            "LOCATE"  -> LocateCommand(context).execute(requestId)
            "LOCK"    -> LockCommand(context).execute(params["message"])
            "ALARM"   -> AlarmCommand(context).start(params["durationSec"]?.toIntOrNull() ?: 30)
            "STOP_ALARM" -> AlarmCommand(context).stop()
            "WIPE"    -> WipeCommand(context).execute(requestId) // exige doble confirmación en el panel
            "PHOTO"   -> IntruderPhotoCommand(context).capture(requestId)
            "TRACK_ON"  -> startTracking()
            "TRACK_OFF" -> stopTracking()
            "SOS"     -> SosCommand(context).trigger()
            else -> Log.w(TAG, "Comando desconocido: $command")
        }
    }

    /** Intento de desbloqueo fallido reportado por el DeviceAdminReceiver. */
    fun onFailedUnlock() {
        // Solo captura si el usuario habilitó la función antirrobo en ajustes.
        IntruderPhotoCommand(context).capture(requestId = "failed-unlock")
    }

    private fun startTracking() {
        val i = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        androidx.core.content.ContextCompat.startForegroundService(context, i)
    }

    private fun stopTracking() {
        context.stopService(Intent(context, LocationService::class.java))
    }

    companion object { private const val TAG = "CmdDispatcher" }
}
