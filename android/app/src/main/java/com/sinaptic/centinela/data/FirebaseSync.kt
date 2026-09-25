package com.sinaptic.centinela.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.UUID

/**
 * Puente entre la app y Firebase (Firestore + Auth).
 *
 * users/{uid}/devices/{deviceId}
 *   name (editable desde el portal), model, updatedAt
 *   settings: { tracking, photo }
 *   status:   { battery, network, ts }      <- batería y tipo de conexión
 *   command:  { type, id, ts, message? }
 *   lastLocation: { lat, lng, ts } + subcolección locations/
 */
class FirebaseSync(private val context: Context) {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val prefs = DeviceRepository(context).prefs()

    fun isLinked(): Boolean = auth.currentUser != null
    fun currentEmail(): String? = auth.currentUser?.email

    fun deviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id == null) { id = UUID.randomUUID().toString(); prefs.edit().putString("device_id", id).apply() }
        return id
    }

    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.message) }
    }

    fun register(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.message) }
    }

    fun signOut() = auth.signOut()

    private fun deviceDoc() = auth.currentUser?.let {
        db.collection("users").document(it.uid).collection("devices").document(deviceId())
    }

    /**
     * Crea/actualiza el dispositivo SIN pisar el nombre ni los ajustes que ya existan
     * (para que renombrar desde el portal no se sobrescriba). Luego reporta estado.
     */
    fun registerDevice() {
        val doc = deviceDoc() ?: return
        doc.get().addOnSuccessListener { snap ->
            val data = hashMapOf<String, Any>(
                "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "updatedAt" to System.currentTimeMillis(),
            )
            if (!snap.exists() || snap.getString("name") == null) data["name"] = Build.MODEL
            if (!snap.exists() || snap.get("settings") == null) {
                data["settings"] = mapOf(
                    "tracking" to prefs.getBoolean("tracking_enabled", false),
                    "photo" to prefs.getBoolean("antitheft_photo_enabled", false),
                )
            }
            doc.set(data, SetOptions.merge())
            reportStatus()
        }.addOnFailureListener { Log.e(TAG, "registerDevice", it) }
    }

    /** Reporta batería y tipo de conexión al documento del dispositivo. */
    fun reportStatus() {
        val doc = deviceDoc() ?: return
        doc.set(mapOf("status" to mapOf(
            "battery" to batteryLevel(),
            "network" to networkType(),
            "ts" to System.currentTimeMillis(),
        )), SetOptions.merge())
    }

    private fun batteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { -1 }
    }

    private fun networkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nc = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                nc == null -> "Sin conexión"
                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
                else -> "Otra"
            }
        } catch (e: Exception) { "Desconocida" }
    }

    fun pushSetting(key: String, value: Boolean) {
        deviceDoc()?.set(mapOf("settings" to mapOf(key to value)), SetOptions.merge())
    }

    /** Sube la última ubicación conocida Y la agrega al historial. */
    fun uploadLocation(lat: Double, lng: Double) {
        val doc = deviceDoc() ?: return
        val ts = System.currentTimeMillis()
        doc.set(mapOf("lastLocation" to mapOf("lat" to lat, "lng" to lng, "ts" to ts)), SetOptions.merge())
        doc.collection("locations").add(mapOf("lat" to lat, "lng" to lng, "ts" to ts))
    }

    fun listen(
        onSettings: (tracking: Boolean, photo: Boolean) -> Unit,
        onCommand: (type: String, message: String?) -> Unit,
    ): ListenerRegistration? {
        val doc = deviceDoc() ?: return null
        return doc.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) return@addSnapshotListener

            @Suppress("UNCHECKED_CAST")
            val settings = snap.get("settings") as? Map<String, Any?>
            onSettings(settings?.get("tracking") as? Boolean ?: false,
                       settings?.get("photo") as? Boolean ?: false)

            @Suppress("UNCHECKED_CAST")
            val command = snap.get("command") as? Map<String, Any?>
            val cmdId = command?.get("id") as? String
            val cmdType = command?.get("type") as? String
            val cmdMsg = command?.get("message") as? String
            if (cmdType != null && cmdId != null && cmdId != lastCmdId) {
                lastCmdId = cmdId
                onCommand(cmdType, cmdMsg)
            }
        }
    }

    private var lastCmdId: String? = null

    companion object { private const val TAG = "FirebaseSync" }
}
