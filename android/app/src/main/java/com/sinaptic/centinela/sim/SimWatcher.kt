package com.sinaptic.centinela.sim

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import com.sinaptic.centinela.data.DeviceRepository

/**
 * Detecta cambio de SIM: guarda el identificador de la SIM y lo compara en cada arranque.
 * Si cambió, reporta un evento al backend (posible robo).
 */
class SimWatcher(private val context: Context) {

    @SuppressLint("HardwareIds", "MissingPermission")
    fun checkOnBoot() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val current = runCatching { tm.simSerialNumber }.getOrNull() ?: return
        val prefs = DeviceRepository(context).prefs()
        val saved = prefs.getString(KEY_SIM, null)

        when {
            saved == null -> prefs.edit().putString(KEY_SIM, current).apply()
            saved != current -> {
                Log.w(TAG, "¡Cambio de SIM detectado!")
                DeviceRepository(context).reportEvent(
                    type = "SIM_CHANGED",
                    detail = "SIM anterior distinta de la actual"
                )
                prefs.edit().putString(KEY_SIM, current).apply()
            }
        }
    }

    companion object {
        private const val TAG = "SimWatcher"
        private const val KEY_SIM = "last_sim_serial"
    }
}
