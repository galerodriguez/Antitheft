package com.sinaptic.centinela.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe la alarma periódica del watchdog: reanima el servicio guardián si el sistema lo mató
 * y vuelve a programar la próxima alarma. Así la protección sigue viva sin abrir la app.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BackgroundGuard.ensureRunning(context)
        BackgroundGuard.scheduleWatchdog(context) // re-arma para el próximo ciclo
    }
}
