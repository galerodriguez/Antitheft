package com.sinaptic.centinela.admin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Oculta o muestra el ícono de la app en el menú de aplicaciones.
 *
 * Funciona activando/desactivando el "alias de lanzador" (LauncherIcon) declarado en el manifest.
 * La app sigue corriendo igual (servicios, ubicación, etc.): solo desaparece el ícono.
 *
 * Para volver a abrirla con el ícono oculto:
 *   1) Marcar en el teléfono el código secreto *#*#74633277#*#* ("PINDEAPP"), o
 *   2) Usar el botón "Mostrar ícono" desde el portal.
 */
object AppIcon {
    private const val TAG = "AppIcon"
    private const val ALIAS = "com.sinaptic.centinela.LauncherIcon"

    fun hide(ctx: Context) { setEnabled(ctx, false) }
    fun show(ctx: Context) { setEnabled(ctx, true) }

    fun isHidden(ctx: Context): Boolean {
        return runCatching {
            val pm = ctx.packageManager
            val state = pm.getComponentEnabledSetting(ComponentName(ctx.packageName, ALIAS))
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    private fun setEnabled(ctx: Context, enabled: Boolean) {
        runCatching {
            val pm = ctx.packageManager
            val comp = ComponentName(ctx.packageName, ALIAS)
            val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                           else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            // DONT_KILL_APP: cambia el ícono sin matar el proceso ni cortar los servicios.
            pm.setComponentEnabledSetting(comp, newState, PackageManager.DONT_KILL_APP)
            Log.i(TAG, "Ícono " + if (enabled) "mostrado" else "oculto")
        }.onFailure { Log.e(TAG, "No se pudo cambiar el ícono", it) }
    }
}
