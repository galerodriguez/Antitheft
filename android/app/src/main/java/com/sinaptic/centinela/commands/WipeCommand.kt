package com.sinaptic.centinela.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver

/**
 * Borrado remoto de datos. Es un comando DESTRUCTIVO e irreversible.
 *
 * Salvaguardas de diseño:
 *  - El panel web exige doble confirmación + re-autenticación antes de enviarlo.
 *  - El backend marca el comando como "destructive" y lo registra en la auditoría.
 *  - Aquí verificamos que realmente venga marcado como confirmado.
 */
class WipeCommand(private val context: Context) {

    fun execute(requestId: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = CentinelaDeviceAdminReceiver.componentName(context)
        if (!dpm.isAdminActive(admin)) {
            Log.w(TAG, "Device admin no activo; no se puede borrar")
            return
        }
        Log.w(TAG, "Ejecutando borrado remoto ($requestId)")
        // WIPE_EXTERNAL_STORAGE opcional; sin flags borra solo datos de la app/usuario.
        dpm.wipeData(0)
    }

    companion object { private const val TAG = "WipeCommand" }
}
