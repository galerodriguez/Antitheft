package com.sinaptic.centinela.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sinaptic.centinela.R

/**
 * Recibe el estado de la instalación de la actualización.
 * En modo normal, Android pide confirmación del usuario: mostramos una notificación que,
 * al tocarla, abre el instalador. En Device Owner la instalación es silenciosa (no llega acá
 * un pedido de confirmación).
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Intentamos abrir directo; si estamos en segundo plano, notificamos.
                runCatching { context.startActivity(confirm) }.onFailure {
                    notifyConfirm(context, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Actualización instalada")
            else -> Log.w(TAG, "Instalación no completada, status=$status: " +
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    private fun notifyConfirm(ctx: Context, confirm: Intent?) {
        if (confirm == null) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Actualizaciones", NotificationManager.IMPORTANCE_HIGH))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(ctx, 0, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val n: Notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Actualización disponible")
            .setContentText("Tocá para instalar la nueva versión de Antitheft.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        mgr.notify(3001, n)
    }

    companion object {
        private const val TAG = "UpdateInstall"
        private const val CHANNEL = "centinela_update"
        const val ACTION_STATUS = "com.sinaptic.centinela.UPDATE_STATUS"
    }
}
