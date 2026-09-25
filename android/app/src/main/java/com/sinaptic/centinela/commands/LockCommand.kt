package com.sinaptic.centinela.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver

/** Bloquea la pantalla inmediatamente y opcionalmente escribe un mensaje en la pantalla de bloqueo. */
class LockCommand(private val context: Context) {

    fun execute(message: String?) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = CentinelaDeviceAdminReceiver.componentName(context)

        if (!dpm.isAdminActive(admin)) {
            Log.w(TAG, "Device admin no activo; no se puede bloquear")
            return
        }

        if (!message.isNullOrBlank()) {
            // Mensaje visible en la pantalla de bloqueo (ej. "Teléfono perdido, llamar al ...").
            runCatching { dpm.setDeviceOwnerLockScreenInfo(admin, message) }
        }
        dpm.lockNow()
        Log.i(TAG, "Pantalla bloqueada remotamente")
    }

    companion object { private const val TAG = "LockCommand" }
}
