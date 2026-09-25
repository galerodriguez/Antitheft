package com.sinaptic.centinela.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.sinaptic.centinela.R
import com.sinaptic.centinela.data.FirebaseSync

/**
 * Servicio en primer plano que rastrea la ubicación periódicamente (modo Familiar o SOS).
 *
 * Muestra SIEMPRE una notificación persistente mientras rastrea. Esto es obligatorio en
 * Android 8+ y, además, es el requisito de transparencia que distingue a una app de seguridad
 * de un stalkerware. No se puede rastrear en silencio.
 */
class LocationService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                // Sube a Firestore (última ubicación + historial), visible en el portal.
                FirebaseSync(applicationContext).uploadLocation(it.latitude, it.longitude)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        startForeground(NOTIF_ID, buildNotification())
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .build()
        client.requestLocationUpdates(request, callback, mainLooper)
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ubicación Centinela", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // Notificación NEUTRA y discreta (baja prioridad, sin alarmar) pero honesta: no
        // disfraza a la app de otra cosa. Esto es obligatorio en Android para ubicación en
        // segundo plano y es lo que mantiene la app del lado legítimo (no stalkerware).
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.sinaptic.centinela.START_TRACKING"
        private const val CHANNEL = "centinela_location"
        private const val NOTIF_ID = 1001
    }
}
