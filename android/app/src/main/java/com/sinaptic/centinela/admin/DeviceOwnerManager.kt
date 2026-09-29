package com.sinaptic.centinela.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager
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
                Manifest.permission.RECORD_AUDIO,
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

        // 3) Fuerza la ubicación del sistema ENCENDIDA (el ladrón no puede apagar el GPS),
        //    salvo que el dueño la haya apagado a propósito desde el portal.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            com.sinaptic.centinela.data.DeviceRepository(ctx.applicationContext).isAutoLocation()) {
            runCatching { dpm.setLocationEnabled(admin, true) }
        }

        // 4) Que la cámara nunca quede deshabilitada por política (foto del intruso).
        runCatching { dpm.setCameraDisabled(admin, false) }

        // 5) Permitir que la app entre en modo kiosko (fijar pantalla).
        runCatching { dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName)) }

        // 6) Restricciones antirrobo: no reseteo de fábrica, no arranque seguro, no modo avión.
        applyRestrictions(ctx, true)

        Log.i(TAG, "Blindaje Device Owner aplicado")
    }

    /** Restricciones fuertes: bloquear reseteo de fábrica, modo avión, arranque seguro, agregar usuarios. */
    fun applyRestrictions(ctx: Context, on: Boolean) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val admin = CentinelaDeviceAdminReceiver.componentName(ctx)
        val restrictions = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_AIRPLANE_MODE,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_CONFIG_TETHERING,
        )
        restrictions.forEach { r ->
            runCatching {
                if (on) dpm.addUserRestriction(admin, r) else dpm.clearUserRestriction(admin, r)
            }
        }
    }

    /** Prende o apaga la ubicación del sistema (solo Device Owner). */
    fun setLocation(ctx: Context, on: Boolean) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { dpm.setLocationEnabled(CentinelaDeviceAdminReceiver.componentName(ctx), on) }
        }
    }

    /** Quita/pone el bloqueo de pantalla del sistema (abrir el teléfono a distancia). */
    fun setKeyguardDisabled(ctx: Context, disabled: Boolean) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching { dpm.setKeyguardDisabled(CentinelaDeviceAdminReceiver.componentName(ctx), disabled) }
    }

    /** Oculta o muestra una app instalada (bloqueo de apps). Devuelve true si pudo. */
    fun hideApp(ctx: Context, pkg: String, hidden: Boolean): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
        return runCatching {
            dpm.setApplicationHidden(CentinelaDeviceAdminReceiver.componentName(ctx), pkg, hidden)
        }.getOrDefault(false)
    }

    /** Libera el bloqueo de desinstalación (para mantenimiento, sin quitar la protección). */
    fun allowUninstall(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching {
            dpm.setUninstallBlocked(CentinelaDeviceAdminReceiver.componentName(ctx), ctx.packageName, false)
        }
    }

    /**
     * Libera TODO el teléfono: quita las restricciones (reseteo de fábrica, modo avión, etc.),
     * desbloquea la desinstalación y renuncia al Device Owner. Se usa para vender o entregar
     * el teléfono. OJO: es irreversible — para volver a ponerlo como Device Owner hay que
     * resetear de fábrica y provisionar de nuevo por QR.
     */
    fun releaseDevice(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val admin = CentinelaDeviceAdminReceiver.componentName(ctx)
        // 1) Sacar restricciones ANTES de renunciar (después ya no tenemos permisos).
        runCatching { applyRestrictions(ctx, false) }
        // 2) Volver a permitir el bloqueo de pantalla del sistema si estaba desactivado.
        runCatching { dpm.setKeyguardDisabled(admin, false) }
        // 3) Permitir desinstalar.
        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, false) }
        // 4) Renunciar al Device Owner: esto por sí solo limpia todas las políticas restantes.
        runCatching { dpm.clearDeviceOwnerApp(ctx.packageName) }
    }
}
