package com.sinaptic.centinela.sim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sinaptic.centinela.data.FirebaseSync
import com.sinaptic.centinela.service.BackgroundGuard

/**
 * Al reiniciar el teléfono:
 *  1) Verifica si cambió la SIM (posible robo).
 *  2) Reanuda el servicio guardián si el dispositivo está vinculado a una cuenta, para seguir
 *     recibiendo comandos (localizar, bloquear, alarma), reportar estado y rastrear —
 *     aunque nadie vuelva a abrir la app.
 *  3) Reprograma el watchdog.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED &&
            a != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        runCatching { SimWatcher(context).checkOnBoot() }

        try {
            if (FirebaseSync(context.applicationContext).isLinked()) {
                BackgroundGuard.ensureRunning(context)
                BackgroundGuard.scheduleWatchdog(context)
                Log.i(TAG, "Guardián reanudado tras reinicio/actualización")
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo reanudar el guardián", e)
        }
    }

    companion object { private const val TAG = "BootReceiver" }
}
