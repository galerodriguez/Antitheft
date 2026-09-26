package com.sinaptic.centinela.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Superpoderes que SOLO se activan si la app fue provisionada como "Device Owner"
 * (teléfono reseteado + `adb shell dpm set-device-owner ...`). Si la app está instalada
 * de forma normal, todo esto se ignora y sigue funcionando el modo estándar.
 *
 * La MISMA app cubre los dos casos: detecta el modo y usa lo más fuerte disponible.
 */
object DeviceOwnerManager {

    private const val TAG = "DeviceOwner"

    fun isDeviceOwner(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(ctx.packageName)
    }

    /** Aplica todo el blindaje de Device Owner. No hace nada si la app no es owner. */
    fun applyIfOwner(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val admin = CentinelaDeviceAdminReceiver.componentName(ctx)

        // 1) No se puede desinstalar (ni desde Ajustes).
        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, true) }

        // 2) Autoconcede permisos: sin diálogos, imposible que el ladrón los revoque.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_PHONE_STATE,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.forEach { p ->
                runCatching {
                    dpm.setPermissionGrantState(admin, ctx.packageName, p,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                }
            }
        }

        // 3) Fuerza la ubicación del sistema ENCENDIDA (el ladrón no puede apagar el GPS).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { dpm.setLocationEnabled(admin, true) }
        }

        // 4) Que la cámara nunca quede deshabilitada por política (foto del intruso).
        runCatching { dpm.setCameraDisabled(admin, false) }

        Log.i(TAG, "Blindaje Device Owner aplicado")
    }

    /** Libera el bloqueo de desinstalación (para mantenimiento). Solo owner. */
    fun allowUninstall(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching {
            dpm.setUninstallBlocked(CentinelaDeviceAdminReceiver.componentName(ctx), ctx.packageName, false)
        }
    }
}
