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
    private var currentRid: String = "audio"
    private var finished = false
    private var clipSeconds = 8
    private var remaining = 1          // clips que faltan grabar en la tanda
    private var totalCount = 1         // total de clips pedidos
    private var burstCancelled = false // true si pidieron STOP
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Pedido de detener una grabación (y cancelar la tanda si hay).
            if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
                burstCancelled = true; remaining = 0
                if (recorder != null) { finishRecording() }
                else { runCatching { stopSelf() } }
                return START_NOT_STICKY
            }
            if (!startForegroundSafe()) { stopSelf(); return START_NOT_STICKY }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Sin permiso de micrófono"); stopSelf(); return START_NOT_STICKY
            }
            // Si ya hay una grabación/tanda en curso, ignoramos el nuevo inicio.
            if (recorder != null) return START_NOT_STICKY
            clipSeconds = intent?.getIntExtra(EXTRA_SECONDS, 8) ?: 8
            requestId = intent?.getStringExtra(EXTRA_REQUEST) ?: "audio"
            val reps = intent?.getIntExtra(EXTRA_REPEAT, 1) ?: 1
            totalCount = reps.coerceIn(1, MAX_BURST)
            remaining = totalCount
            burstCancelled = false
            startClip()
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo al grabar", e); runCatching { stopSelf() }
        }
        return START_NOT_STICKY
    }

    /** Arranca el próximo clip de la tanda (numerado si son varios). */
    private fun startClip() {
        val idx = totalCount - remaining + 1
        currentRid = if (totalCount > 1) "$requestId ($idx/$totalCount)" else requestId
        startRecording(clipSeconds)
    }

    private fun startRecording(seconds: Int) {
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
        // Duración fija (con tope de seguridad por el límite de Firestore). seconds<=0 = manual.
        val capped = if (seconds > 0) seconds.coerceAtMost(MANUAL_MAX_S) else MANUAL_MAX_S
        Log.i(TAG, "Grabando ${capped}s… ($currentRid)")
        handler.postDelayed({ finishRecording() }, capped * 1000L)
    }

    private fun finishRecording() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        val rid = currentRid
        runCatching {
            val f = outFile
            if (f != null && f.exists() && f.length() > 0) {
                val b64 = "data:audio/mp4;base64," +
                    Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                if (b64.length < 900_000) FirebaseSync(applicationContext).uploadAudio(b64, rid)
                else Log.w(TAG, "Audio demasiado grande para Firestore")
                f.delete()
            }
        }
        remaining -= 1
        if (!burstCancelled && remaining > 0) {
            // Pausa corta antes del próximo clip: deja liberar el micrófono y empezar la subida.
            handler.postDelayed({ startClip() }, GAP_MS)
        } else {
            runCatching { stopSelf() }
        }
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
        const val EXTRA_REPEAT = "repeat"
        // Tope de seguridad: ~90 s a 64 kbps entra en el límite de ~900 KB de Firestore (plan gratis).
        const val MANUAL_MAX_S = 90
        // Máximo de clips en una tanda (cada uno se sube por separado).
        const val MAX_BURST = 10
        // Pausa entre clips de una tanda.
        private const val GAP_MS = 1200L

        /** Inicia una grabación. seconds > 0 = fija; seconds <= 0 = manual (hasta stop). repeat = cantidad de clips seguidos. */
        fun start(context: Context, seconds: Int = 8, requestId: String = "audio", repeat: Int = 1) {
            val i = Intent(context, AudioCaptureService::class.java)
                .putExtra(EXTRA_SECONDS, seconds).putExtra(EXTRA_REQUEST, requestId)
                .putExtra(EXTRA_REPEAT, repeat)
            ContextCompat.startForegroundService(context, i)
        }

        /** Detiene una grabación manual en curso y sube lo grabado. */
        fun stop(context: Context) {
            val i = Intent(context, AudioCaptureService::class.java).putExtra(EXTRA_STOP, true)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
