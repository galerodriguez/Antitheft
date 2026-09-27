package com.sinaptic.centinela.commands

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Hace sonar una alarma a volumen máximo y, opcionalmente, repite un mensaje hablado
 * por el parlante (ej: "Teléfono robado, devolver a…") para disuadir/avisar.
 */
class AlarmCommand(private val context: Context) {

    fun start(durationSec: Int, spokenMessage: String? = null) {
        stop() // cortar cualquier alarma previa
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = MediaPlayer().apply {
            setDataSource(context, uri)
            setAudioStreamType(AudioManager.STREAM_ALARM)
            isLooping = true
            prepare(); start()
        }
        Log.i(TAG, "Alarma iniciada por ${durationSec}s")

        if (!spokenMessage.isNullOrBlank()) startSpeaking(spokenMessage)

        handler.postDelayed({ stop() }, durationSec * 1000L)
    }

    /** Inicializa TTS y repite el mensaje cada ~9 s mientras dure la alarma. */
    private fun startSpeaking(message: String) {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = Locale("es", "ES") }
                    speakLoop(message)
                }
            }
        }
    }

    private fun speakLoop(message: String) {
        val t = tts ?: return
        // subir volumen de medios también, para que se escuche el TTS
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        }
        t.speak(message, TextToSpeech.QUEUE_FLUSH, null, "antitheft-tts")
        handler.postDelayed({ if (tts != null) speakLoop(message) }, 9000L)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.run { if (isPlaying) stop(); release() }
        player = null
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        Log.i(TAG, "Alarma detenida")
    }

    companion object {
        private const val TAG = "AlarmCommand"
        private val handler = Handler(Looper.getMainLooper())
        private var player: MediaPlayer? = null
        private var tts: TextToSpeech? = null
    }
}
