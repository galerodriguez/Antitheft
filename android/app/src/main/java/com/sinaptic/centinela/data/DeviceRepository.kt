package com.sinaptic.centinela.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sinaptic.centinela.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Capa de datos: registra el dispositivo, sube ubicaciones y reporta eventos al backend.
 * Usa almacenamiento cifrado para el deviceId y el token de autenticación.
 *
 * Las llamadas de red se muestran síncronas por claridad; en producción ejecutarlas en
 * corrutinas (Dispatchers.IO) o con WorkManager para reintentos.
 */
class DeviceRepository(private val context: Context) {

    private val http = OkHttpClient()
    private val base = BuildConfig.API_BASE_URL
    private val json = "application/json".toMediaType()

    fun prefs(): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, "centinela_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deviceId() = prefs().getString("device_id", null)
    private fun authToken() = prefs().getString("auth_token", null)

    fun updateFcmToken(token: String) {
        prefs().edit().putString("fcm_token", token).apply()
        val id = deviceId() ?: return
        post("/devices/$id/fcm", JSONObject().put("fcmToken", token))
    }

    fun uploadLocation(lat: Double, lng: Double, accuracy: Float, requestId: String) {
        val id = deviceId() ?: return
        post("/devices/$id/locations", JSONObject()
            .put("lat", lat).put("lng", lng)
            .put("accuracy", accuracy).put("requestId", requestId))
    }

    fun reportEvent(type: String, detail: String) {
        val id = deviceId() ?: return
        post("/devices/$id/events", JSONObject().put("type", type).put("detail", detail))
    }

    fun startSosSession() {
        val id = deviceId() ?: return
        post("/devices/$id/sos", JSONObject().put("status", "active"))
    }

    private fun post(path: String, body: JSONObject) {
        try {
            val req = Request.Builder()
                .url("$base$path")
                .addHeader("Authorization", "Bearer ${authToken().orEmpty()}")
                .post(body.toString().toRequestBody(json))
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) Log.w(TAG, "POST $path -> ${r.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error POST $path", e)
        }
    }

    companion object { private const val TAG = "DeviceRepo" }
}
