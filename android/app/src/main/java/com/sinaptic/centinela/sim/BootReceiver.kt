package com.sinaptic.centinela.sim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.service.GuardianService

/**
 * Al reiniciar el teléfono:
 *  1) Verifica si cambió la SIM (posible robo).
 *  2) Reanuda el servicio guardián si el rastreo estaba activo (recibe comandos y rastrea).
 *
 * Requiere que el usuario haya concedido ubicación (idealmente "Permitir todo el tiempo").
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        SimWatcher(context).checkOnBoot()

        val trackingOn = DeviceRepository(context).prefs().getBoolean("tracking_enabled", false)
        if (trackingOn) {
            try {
                GuardianService.start(context)
                Log.i(TAG, "Guardián reanudado tras reinicio")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo reanudar el guardián tras reinicio", e)
            }
        }
    }

    companion object { private const val TAG = "BootReceiver" }
}
