package com.sinaptic.centinela.data

import android.content.Context
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
 * Modelo de datos:
 *   users/{uid}/devices/{deviceId}
 *     name, model, updatedAt
 *     settings: { tracking: Bool, photo: Bool }     <- controlable desde el portal
 *     command:  { type: String, id: String, ts }    <- comando puntual (LOCATE, LOCK, ...)
 *     lastLocation: { lat, lng, ts }
 *
 * La app y el portal usan la MISMA cuenta (email/contraseña). Así ambos ven el mismo device.
 */
class FirebaseSync(private val context: Context) {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val prefs = DeviceRepository(context).prefs()

    fun isLinked(): Boolean = auth.currentUser != null
    fun currentEmail(): String? = auth.currentUser?.email

    fun deviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
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
        db.collection("users").document(it.uid)
            .collection("devices").document(deviceId())
    }

    /** Crea/actualiza el documento del dispositivo con sus ajustes actuales. */
    fun registerDevice() {
        val doc = deviceDoc() ?: return
        val data = mapOf(
            "name" to Build.MODEL,
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "updatedAt" to System.currentTimeMillis(),
            "settings" to mapOf(
                "tracking" to prefs.getBoolean("tracking_enabled", false),
                "photo" to prefs.getBoolean("antitheft_photo_enabled", false),
            ),
        )
        doc.set(data, SetOptions.merge())
            .addOnFailureListener { Log.e(TAG, "registerDevice", it) }
    }

    /** Escribe un ajuste en Firestore (cuando el usuario lo cambia en la app). */
    fun pushSetting(key: String, value: Boolean) {
        deviceDoc()?.set(mapOf("settings" to mapOf(key to value)), SetOptions.merge())
    }

    /** Sube la última ubicación conocida. */
    fun uploadLocation(lat: Double, lng: Double) {
        deviceDoc()?.set(
            mapOf("lastLocation" to mapOf("lat" to lat, "lng" to lng, "ts" to System.currentTimeMillis())),
            SetOptions.merge()
        )
    }

    /**
     * Escucha en tiempo real los cambios del dispositivo.
     * @param onSettings recibe (tracking, photo) cada vez que cambian.
     * @param onCommand recibe el tipo de comando nuevo (una sola vez por comando).
     */
    fun listen(
        onSettings: (tracking: Boolean, photo: Boolean) -> Unit,
        onCommand: (type: String) -> Unit,
    ): ListenerRegistration? {
        val doc = deviceDoc() ?: return null
        return doc.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) return@addSnapshotListener

            @Suppress("UNCHECKED_CAST")
            val settings = snap.get("settings") as? Map<String, Any?>
            val tracking = settings?.get("tracking") as? Boolean ?: false
            val photo = settings?.get("photo") as? Boolean ?: false
            onSettings(tracking, photo)

            @Suppress("UNCHECKED_CAST")
            val command = snap.get("command") as? Map<String, Any?>
            val cmdId = command?.get("id") as? String
            val cmdType = command?.get("type") as? String
            if (cmdType != null && cmdId != null && cmdId != lastCmdId) {
                lastCmdId = cmdId
                onCommand(cmdType)
            }
        }
    }

    private var lastCmdId: String? = null

    companion object { private const val TAG = "FirebaseSync" }
}
