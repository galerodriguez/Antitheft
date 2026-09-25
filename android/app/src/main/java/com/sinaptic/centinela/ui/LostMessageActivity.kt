package com.sinaptic.centinela.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.sinaptic.centinela.R

/**
 * Pantalla que se muestra SOBRE el bloqueo cuando se envía el comando "Bloquear" con mensaje.
 * Sirve como "pantalla de teléfono perdido": muestra el mensaje del dueño.
 */
class LostMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContentView(R.layout.activity_lost)
        findViewById<TextView>(R.id.lostMsg).text =
            intent.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.lost_default)
    }

    // No permitir salir con el botón atrás.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* bloqueado a propósito */ }

    companion object {
        const val EXTRA_MESSAGE = "message"
        private const val CHANNEL = "centinela_lost"
        private const val NOTIF_ID = 2001

        /**
         * Muestra la pantalla de "teléfono perdido" de forma FIABLE, incluso con la app cerrada
         * y el teléfono bloqueado. En Android 10+ un servicio en segundo plano NO puede abrir una
         * Activity directamente; la vía autorizada es una notificación con "full-screen intent",
         * que el sistema convierte en la pantalla a pantalla completa sobre el bloqueo.
         */
        fun show(context: Context, message: String?) {
            val msg = if (message.isNullOrBlank())
                context.getString(R.string.lost_default) else message

            val activityIntent = Intent(context, LostMessageActivity::class.java)
                .putExtra(EXTRA_MESSAGE, msg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pending = PendingIntent.getActivity(context, 0, activityIntent, piFlags)

            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL, "Antitheft", NotificationManager.IMPORTANCE_HIGH)
                ch.setBypassDnd(true)
                mgr.createNotificationChannel(ch)
            }

            val notif: Notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(R.string.notif_title))
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pending, true)   // <- abre la pantalla sobre el bloqueo
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            mgr.notify(NOTIF_ID, notif)

            // Intento directo también: funciona al instante si la app está en primer plano.
            runCatching { context.startActivity(activityIntent) }
        }
    }
}
