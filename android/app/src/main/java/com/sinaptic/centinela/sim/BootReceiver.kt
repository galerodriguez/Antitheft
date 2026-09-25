package com.sinaptic.centinela.sim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tras reiniciar: verifica cambio de SIM y reanuda el rastreo si estaba activo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SimWatcher(context).checkOnBoot()
        // Reanudar LocationService si el usuario tenía el rastreo activado (ver prefs).
    }
}
