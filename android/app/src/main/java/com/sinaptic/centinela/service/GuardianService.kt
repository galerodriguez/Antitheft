package com.sinaptic.centinela.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.ListenerRegistration
import com.sinaptic.centinela.R
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver
import com.sinaptic.centinela.commands.AlarmCommand
import com.sinaptic.centinela.commands.IntruderPhotoCommand
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.data.FirebaseSync
import com.sinaptic.centinela.ui.LostMessageActivity

/**
 * Servicio guardián en primer plano: mantiene viva la escucha de Firestore aunque la app esté
 * cerrada, para recibir comandos (localizar, bloquear, alarma) y aplicar ajustes en tiempo real.
 * También hace el rastreo de ubicación cuando "Compartir ubicación" está activo.
 *
 * Muestra una notificación persistente (obligatoria y transparente). Requiere permiso de
 * ubicación para arrancar (por eso se inicia cuando ya está concedido).
 */
class GuardianService : Service() {

    private lateinit var sync: FirebaseSync
    private lateinit var fused: FusedLocationProviderClient
    private var docReg: ListenerRegistration? = null
    private var trackingOn = false

    // Latido: refresca batería y conexión cada pocos minutos aunque no haya movimiento.
    private val heartbeat = Handler(Looper.getMainLooper())
    private val heartbeatTask = object : Runnable {
        override fun run() {
            runCatching { if (sync.isLinked()) sync.reportStatus() }
            heartbeat.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val locCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { sync.uploadLocation(it.latitude, it.longitude) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sync = FirebaseSync(applicationContext)
        fused = LocationServices.getFusedLocationProviderClient(this)
        startForegroundNotification()
        startListening()
        // Arranca el latido (el primer reporte ya lo hizo startListening()).
        heartbeat.removeCallbacks(heartbeatTask)
        heartbeat.postDelayed(heartbeatTask, HEARTBEAT_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (docReg == null) startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (!sync.isLinked()) { stopSelf(); return }
        sync.reportStatus()
        docReg?.remove()
        docReg = sync.listen(
            onSettings = { tracking, _ -> applyTracking(tracking) },
            onCommand = { type, msg -> execute(type, msg) },
        )
    }

    @SuppressLint("MissingPermission")
    private fun applyTracking(on: Boolean) {
        DeviceRepository(applicationContext).prefs().edit().putBoolean("tracking_enabled", on).apply()
        if (on && !trackingOn) {
            trackingOn = true
            val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
                .setMinUpdateIntervalMillis(30_000L).build()
            runCatching { fused.requestLocationUpdates(req, locCallback, mainLooper) }
        } else if (!on && trackingOn) {
            trackingOn = false
            runCatching { fused.removeLocationUpdates(locCallback) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun execute(type: String, message: String?) {
        // Cualquier comando del portal es señal de vida: refrescamos batería/conexión al toque.
        runCatching { sync.reportStatus() }
        when (type.uppercase()) {
            "LOCATE" -> fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) sync.uploadLocation(loc.latitude, loc.longitude)
                    else fused.lastLocation.addOnSuccessListener { l ->
                        if (l != null) sync.uploadLocation(l.latitude, l.longitude)
                    }
                }
            "ALARM" -> AlarmCommand(this).start(60)
            "STOP_ALARM" -> AlarmCommand(this).stop()
            "LOCK" -> lock(message)
            "PHOTO" -> IntruderPhotoCommand(this).capture("remote")
        }
    }

    private fun lock(message: String?) {
        // Muestra el mensaje ANTES de bloquear: si primero se apaga la pantalla, el full-screen
        // intent la vuelve a encender igual, pero así evitamos cualquier carrera.
        LostMessageActivity.show(this, message)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = CentinelaDeviceAdminReceiver.componentName(this)
        if (dpm.isAdminActive(admin)) dpm.lockNow()
    }

    override fun onDestroy() {
        heartbeat.removeCallbacks(heartbeatTask)
        docReg?.remove()
        runCatching { fused.removeLocationUpdates(locCallback) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Antitheft", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL = "centinela_guardian"
        const val NOTIF_ID = 1001
        private const val HEARTBEAT_MS = 5 * 60 * 1000L  // refresca estado cada 5 minutos

        /** Arranca el servicio (solo tiene sentido con permiso de ubicación concedido). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GuardianService::class.java))
        }
    }
}
