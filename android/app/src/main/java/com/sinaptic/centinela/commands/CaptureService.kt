package com.sinaptic.centinela.commands

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sinaptic.centinela.R
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.data.FirebaseSync
import java.io.ByteArrayOutputStream

/**
 * Servicio en primer plano (tipo cámara) que toma la foto del intruso y la sube al portal.
 * Se necesita un FGS tipo cámara porque Android 11+ bloquea la cámara en segundo plano.
 * Vive solo lo que dura la captura y se detiene solo.
 */
class CaptureService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Nunca dejamos que este servicio crashee la app: todo va envuelto.
        try {
            if (!startForegroundSafe()) { stopSelf(); return START_NOT_STICKY }

            // Sin permiso de cámara no intentamos nada (evita SecurityException).
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Sin permiso de cámara; no capturo"); stopSelf(); return START_NOT_STICKY
            }
            if (!DeviceRepository(this).prefs().getBoolean("antitheft_photo_enabled", false)) {
                Log.i(TAG, "Foto antirrobo deshabilitada; no capturo"); stopSelf(); return START_NOT_STICKY
            }

            val requestId = intent?.getStringExtra(EXTRA_REQUEST) ?: "intruso"
            IntruderCamera(this).capture(count = 3) { shots ->
                runCatching {
                    if (shots.isNotEmpty()) {
                        val sync = FirebaseSync(applicationContext)
                        shots.forEach { jpeg ->
                            val b64 = compressToBase64(jpeg)
                            if (b64 != null) sync.uploadIntruderPhoto(b64, requestId)
                        }
                        Log.i(TAG, "Ráfaga subida: ${shots.size} foto(s)")
                    } else Log.w(TAG, "No se pudo capturar la ráfaga")
                }
                runCatching { stopSelf() }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo en captura, cierro sin romper la app", e)
            runCatching { stopSelf() }
        }
        return START_NOT_STICKY
    }

    /** Reduce y comprime para que entre en un documento de Firestore (~1 MB). */
    private fun compressToBase64(jpeg: ByteArray): String? {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val maxSide = 800
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f)
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        else bmp
        var quality = 70
        var out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        // baja calidad hasta entrar cómodo en Firestore (< ~650 KB en base64)
        while (out.size() * 4 / 3 > 650_000 && quality > 30) {
            quality -= 15; out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Pasa a primer plano con tipo cámara. Devuelve false si el sistema no lo permite. */
    private fun startForegroundSafe(): Boolean {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Antitheft", NotificationManager.IMPORTANCE_MIN))
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, type)
            true
        } catch (e: Throwable) {
            // Android 14 puede bloquear FGS-cámara desde segundo plano. No rompemos la app.
            Log.e(TAG, "No se pudo iniciar FGS de cámara", e)
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL = "centinela_capture"
        private const val NOTIF_ID = 1002
        const val EXTRA_REQUEST = "request_id"

        fun start(context: Context, requestId: String) {
            val i = Intent(context, CaptureService::class.java).putExtra(EXTRA_REQUEST, requestId)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
