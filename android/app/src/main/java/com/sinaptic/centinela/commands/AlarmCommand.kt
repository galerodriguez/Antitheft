package com.sinaptic.centinela.commands

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Hace sonar una alarma a volumen máximo para localizar el teléfono / disuadir al ladrón. */
class AlarmCommand(private val context: Context) {

    fun start(durationSec: Int) {
        stop() // cortar cualquier alarma previa para no dejar reproductores "huérfanos"
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = MediaPlayer().apply {
            setDataSource(context, uri)
            setAudioStreamType(AudioManager.STREAM_ALARM)
            isLooping = true
            prepare()
            start()
        }
        Log.i(TAG, "Alarma iniciada por ${durationSec}s")
        Handler(Looper.getMainLooper()).postDelayed({ stop() }, durationSec * 1000L)
    }

    fun stop() {
        player?.run { if (isPlaying) stop(); release() }
        player = null
        Log.i(TAG, "Alarma detenida")
    }

    companion object {
        private const val TAG = "AlarmCommand"
        // Estático para poder detenerla desde otro comando (STOP_ALARM).
        private var player: MediaPlayer? = null
    }
}
