package com.sinaptic.centinela.root

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.DataOutputStream

/**
 * Control por ROOT para teléfonos donde no se pudo activar Device Owner (ej. Samsung con Knox,
 * que bloquea el Device Owner en equipos rooteados).
 *
 * Si hay "su" disponible, permite forzar la ubicación del sistema encendida — la función que
 * normalmente daría el Device Owner. Best-effort: si no hay root, no hace nada y no rompe.
 */
object RootControl {
    private const val TAG = "RootControl"
    @Volatile private var cachedAvailable: Boolean? = null

    /** ¿Hay root (su) disponible? Se cachea para no pedirlo cada vez. */
    fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val ok = runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        }.getOrDefault(false)
        cachedAvailable = ok
        return ok
    }

    /** Corre una línea como root. Devuelve true si el proceso terminó con código 0. */
    private fun runAsRoot(vararg commands: String): Boolean {
        return runCatching {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(p.outputStream).use { os ->
                for (c in commands) { os.writeBytes(c + "\n") }
                os.writeBytes("exit\n"); os.flush()
            }
            p.waitFor() == 0
        }.onFailure { Log.w(TAG, "Fallo su", it) }.getOrDefault(false)
    }

    /**
     * Si el sistema tiene la ubicación apagada y hay root, la vuelve a prender (alta precisión).
     * Devuelve true si intentó prenderla.
     */
    fun ensureLocationOn(ctx: Context): Boolean {
        val mode = runCatching {
            Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE)
        }.getOrDefault(Settings.Secure.LOCATION_MODE_OFF)
        if (mode != Settings.Secure.LOCATION_MODE_OFF) return false // ya está encendida
        if (!isAvailable()) return false
        Log.i(TAG, "Ubicación apagada: forzando encendido por root")
        // Dos vías por compatibilidad entre versiones de Android.
        runAsRoot(
            "settings put secure location_mode 3",
            "cmd location set-location-enabled true"
        )
        return true
    }

    /** Apaga la ubicación del sistema por root. Devuelve true si intentó apagarla. */
    fun turnLocationOff(ctx: Context): Boolean {
        if (!isAvailable()) return false
        Log.i(TAG, "Apagando ubicación por root")
        runAsRoot(
            "cmd location set-location-enabled false",
            "settings put secure location_mode 0"
        )
        return true
    }
}
