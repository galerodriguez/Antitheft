package com.sinaptic.centinela.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinaptic.centinela.data.FirebaseSync

/**
 * Recibe la alarma periódica del watchdog (dispara incluso en Doze). Hace tres cosas:
 *   1) Reporta estado (batería/conexión) directamente, para que el portal se refresque
 *      cada ~15 min AUNQUE el latido interno del servicio esté frenado.
 *   2) Reanima el servicio guardián si el sistema lo mató.
 *   3) Reprograma la próxima alarma.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val sync = FirebaseSync(context.applicationContext)
            if (sync.isLinked()) sync.reportStatus()
        }
        BackgroundGuard.ensureRunning(context)
        BackgroundGuard.scheduleWatchdog(context) // re-arma para el próximo ciclo
    }
}
