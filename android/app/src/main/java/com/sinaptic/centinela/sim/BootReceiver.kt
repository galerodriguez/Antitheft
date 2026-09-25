package com.sinaptic.centinela.sim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.location.LocationService

/**
 * Al reiniciar el teléfono:
 *  1) Verifica si cambió la SIM (posible robo).
 *  2) Reanuda el rastreo de ubicación si el usuario lo tenía activado (modo Familiar).
 *
 * Nota: reanudar el servicio de ubicación desde el arranque en Android 12+ requiere que el
 * usuario haya concedido "Permitir todo el tiempo" (ubicación en segundo plano). Si no está,
 * el sistema puede impedir el arranque del servicio; por eso se envuelve en try/catch.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 1) Chequeo de cambio de SIM
        SimWatcher(context).checkOnBoot()

        // 2) Reanudar rastreo si estaba activo
        val trackingOn = DeviceRepository(context).prefs().getBoolean("tracking_enabled", false)
        if (trackingOn) {
            try {
                val i = Intent(context, LocationService::class.java)
                    .apply { action = LocationService.ACTION_START }
                ContextCompat.startForegroundService(context, i)
                Log.i(TAG, "Rastreo reanudado tras reinicio")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo reanudar el rastreo tras reinicio", e)
            }
        }
    }

    companion object { private const val TAG = "BootReceiver" }
}
