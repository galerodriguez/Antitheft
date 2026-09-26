package com.sinaptic.centinela.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import com.sinaptic.centinela.BuildConfig
import com.sinaptic.centinela.data.DeviceRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Auto-actualización sin Play Store. Lee un version.json publicado en el portal; si hay una
 * versión más nueva, baja la APK del Release de GitHub y la instala:
 *   - Device Owner  -> instalación SILENCIOSA (sin tocar el teléfono).
 *   - Modo normal   -> el sistema pide confirmar (notificación) — requiere "instalar apps
 *                       desconocidas" habilitado para Antitheft una sola vez.
 *
 * Pensado para "configurar una vez y no abrir nunca más": lo dispara el GuardianService.
 */
object Updater {

    private const val TAG = "Updater"
    private const val VERSION_URL = "https://antitheft-2b140.web.app/version.json"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L // cada 6 horas como máximo

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true)
        .build()

    /** Chequea (respetando el intervalo) en un hilo aparte. Seguro de llamar seguido. */
    fun maybeCheck(ctx: Context) {
        val prefs = DeviceRepository(ctx).prefs()
        val last = prefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
        Thread { runCatching { checkNow(ctx) }.onFailure { Log.w(TAG, "update check", it) } }.start()
    }

    private fun checkNow(ctx: Context) {
        val body = http.newCall(Request.Builder().url(VERSION_URL).build()).execute().use {
            if (!it.isSuccessful) return; it.body?.string() ?: return
        }
        val json = JSONObject(body)
        val remoteCode = json.optInt("versionCode", 0)
        val apkUrl = json.optString("apkUrl", "")
        if (remoteCode <= BuildConfig.VERSION_CODE || apkUrl.isBlank()) {
            Log.i(TAG, "Sin actualización (remota=$remoteCode, actual=${BuildConfig.VERSION_CODE})")
            return
        }
        Log.i(TAG, "Descargando actualización a v$remoteCode")
        val bytes = http.newCall(Request.Builder().url(apkUrl).build()).execute().use {
            if (!it.isSuccessful) return; it.body?.bytes() ?: return
        }
        install(ctx, bytes)
    }

    private fun install(ctx: Context, apk: ByteArray) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("antitheft.apk", 0, apk.size.toLong()).use { out ->
                out.write(apk); session.fsync(out)
            }
            val statusIntent = android.content.Intent(ctx, UpdateInstallReceiver::class.java)
                .setAction(UpdateInstallReceiver.ACTION_STATUS)
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pi = android.app.PendingIntent.getBroadcast(ctx, sessionId, statusIntent, flags)
            session.commit(pi.intentSender)
        }
        Log.i(TAG, "Instalación enviada (silenciosa si es Device Owner)")
    }
}
