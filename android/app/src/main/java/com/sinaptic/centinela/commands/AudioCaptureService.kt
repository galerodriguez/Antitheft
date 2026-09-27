package com.sinaptic.centinela.commands

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sinaptic.centinela.R
import com.sinaptic.centinela.data.FirebaseSync
import java.io.File

/**
 * Graba audio del ambiente y lo sube al panel (base64, sin guardarlo en el teléfono).
 * Modos:
 *  - Duración fija: seconds > 0 (ej. 8, 15, 30, 60). Se detiene solo.
 *  - Manual: seconds <= 0. Graba hasta recibir STOP (o el tope de seguridad MANUAL_MAX_S).
 * Requiere FGS tipo micrófono en Android moderno. Best-effort: si algo falla, no rompe la app.
 */
class AudioCaptureService : Service() {

    private var recorder: MediaRecorder? = null
    private var outFile: File? = null
    private var requestId: String = "audio"
    private var finished = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Pedido de detener una grabación manual en curso.
            if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
                if (recorder != null) { finishRecording(requestId) }
                else { runCatching { stopSelf() } }
                return START_NOT_STICKY
            }
            if (!startForegroundSafe()) { stopSelf(); return START_NOT_STICKY }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Sin permiso de micrófono"); stopSelf(); return START_NOT_STICKY
            }
            // Si ya hay una grabación en curso, ignoramos el nuevo inicio.
            if (recorder != null) return START_NOT_STICKY
            val secs = intent?.getIntExtra(EXTRA_SECONDS, 8) ?: 8
            requestId = intent?.getStringExtra(EXTRA_REQUEST) ?: "audio"
            startRecording(secs, requestId)
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo al grabar", e); runCatching { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(seconds: Int, requestId: String) {
        finished = false
        val file = File(cacheDir, "amb_${System.currentTimeMillis()}.m4a")
        outFile = file
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        recorder = rec
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(64000)
        rec.setAudioSamplingRate(44100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare(); rec.start()
        if (seconds > 0) {
            // Duración fija (con tope de seguridad por el límite de Firestore).
            val capped = seconds.coerceAtMost(MANUAL_MAX_S)
            Log.i(TAG, "Grabando ${capped}s…")
            handler.postDelayed({ finishRecording(requestId) }, capped * 1000L)
        } else {
            // Manual: graba hasta STOP, pero corta solo a los MANUAL_MAX_S por el límite de tamaño.
            Log.i(TAG, "Grabando en modo manual (tope ${MANUAL_MAX_S}s)…")
            handler.postDelayed({ finishRecording(requestId) }, MANUAL_MAX_S * 1000L)
        }
    }

    private fun finishRecording(requestId: String) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching {
            val f = outFile
            if (f != null && f.exists() && f.length() > 0) {
                val b64 = "data:audio/mp4;base64," +
                    Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                if (b64.length < 900_000) FirebaseSync(applicationContext).uploadAudio(b64, requestId)
                else Log.w(TAG, "Audio demasiado grande para Firestore")
                f.delete()
            }
        }
        runCatching { stopSelf() }
    }

    private fun startForegroundSafe(): Boolean {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Antitheft", NotificationManager.IMPORTANCE_MIN))
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        return try { ServiceCompat.startForeground(this, NOTIF_ID, n, type); true }
        catch (e: Throwable) { Log.e(TAG, "No se pudo iniciar FGS de micrófono", e); false }
    }

    override fun onDestroy() {
        runCatching { recorder?.release() }; recorder = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AudioCapture"
        private const val CHANNEL = "centinela_audio"
        private const val NOTIF_ID = 1003
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_REQUEST = "request_id"
        const val EXTRA_STOP = "stop"
        // Tope de seguridad: ~90 s a 64 kbps entra en el límite de ~900 KB de Firestore (plan gratis).
        const val MANUAL_MAX_S = 90

        /** Inicia una grabación. seconds > 0 = fija; seconds <= 0 = manual (hasta stop). */
        fun start(context: Context, seconds: Int = 8, requestId: String = "audio") {
            val i = Intent(context, AudioCaptureService::class.java)
                .putExtra(EXTRA_SECONDS, seconds).putExtra(EXTRA_REQUEST, requestId)
            ContextCompat.startForegroundService(context, i)
        }

        /** Detiene una grabación manual en curso y sube lo grabado. */
        fun stop(context: Context) {
            val i = Intent(context, AudioCaptureService::class.java).putExtra(EXTRA_STOP, true)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
