package com.sinaptic.centinela.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sinaptic.centinela.commands.CaptureService
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.data.FirebaseSync

/**
 * Detecta cambio de SIM (posible robo) y lo reporta al portal.
 *
 * En Android 10+ el número de serie de la SIM (ICCID) ya no es legible por apps normales,
 * así que usamos una huella con lo que SÍ se puede leer con READ_PHONE_STATE:
 * operador (MCC+MNC), nombre del operador y país. Si el ladrón pone un chip de otra
 * compañía, se detecta. Un cambio por otro chip de la MISMA compañía puede no detectarse
 * (límite del sistema, no de la app).
 */
class SimWatcher(private val context: Context) {

    @SuppressLint("HardwareIds", "MissingPermission")
    fun check() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val operator = runCatching { tm.simOperator }.getOrNull().orEmpty()          // MCC+MNC
        val name = runCatching { tm.simOperatorName }.getOrNull().orEmpty()          // "Movistar"…
        val country = runCatching { tm.simCountryIso }.getOrNull().orEmpty()
        val fingerprint = "$operator|$country"
        if (operator.isBlank()) return // sin SIM legible; no hacemos nada

        val prefs = DeviceRepository(context).prefs()
        val saved = prefs.getString(KEY_SIM, null)

        when {
            saved == null -> prefs.edit().putString(KEY_SIM, fingerprint).apply()
            saved != fingerprint -> {
                Log.w(TAG, "¡Cambio de SIM detectado! $saved -> $fingerprint")
                prefs.edit().putString(KEY_SIM, fingerprint).apply()
                val quien = if (name.isNotBlank()) name else operator
                runCatching {
                    FirebaseSync(context.applicationContext).reportAlert(
                        type = "SIM_CHANGED",
                        detail = "Se cambió la SIM. Nueva compañía: $quien",
                        extra = mapOf("carrier" to name, "operator" to operator, "country" to country)
                    )
                }
                // Robo probable: sacamos foto del intruso y ubicación automáticamente.
                runCatching { CaptureService.start(context, "sim-change") }
                runCatching { fetchLocationOnce() }
            }
        }
    }

    /** Compatibilidad: se sigue llamando desde el arranque. */
    fun checkOnBoot() = check()

    @SuppressLint("MissingPermission")
    private fun fetchLocationOnce() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        val fused = LocationServices.getFusedLocationProviderClient(context)
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) FirebaseSync(context.applicationContext).uploadLocation(loc.latitude, loc.longitude)
                else fused.lastLocation.addOnSuccessListener { l ->
                    if (l != null) FirebaseSync(context.applicationContext).uploadLocation(l.latitude, l.longitude)
                }
            }
    }

    companion object {
        private const val TAG = "SimWatcher"
        private const val KEY_SIM = "last_sim_fingerprint"
    }
}
