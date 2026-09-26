package com.sinaptic.centinela.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.sinaptic.centinela.data.FirebaseSync

/**
 * Mantiene vivo el servicio guardián para el caso de uso real: la app se configura UNA vez y
 * nunca más se abre. Combina tres defensas contra la muerte de servicios en segundo plano
 * (sobre todo en Samsung):
 *   1) Exención de optimización de batería (Doze) — se pide en la configuración inicial.
 *   2) Watchdog con AlarmManager: cada ~15 min reanima el servicio si el sistema lo mató.
 *   3) Reinicio al quitar la app de recientes (onTaskRemoved) y tras reiniciar (BootReceiver).
 */
object BackgroundGuard {

    private const val WATCHDOG_REQ = 7001
    private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L

    /** ¿La app ya está exenta de la optimización de batería? */
    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Lanza el diálogo del sistema para permitir que la app corra sin límites de batería. */
    fun promptIgnoreBatteryOptimizations(ctx: Context) {
        if (isIgnoringBatteryOptimizations(ctx)) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }

    /** Programa (o reprograma) el watchdog que revive el servicio periódicamente. */
    fun scheduleWatchdog(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = watchdogIntent(ctx)
        val next = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
        runCatching {
            // setAndAllowWhileIdle NO requiere permiso especial y dispara incluso en Doze.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }.onFailure {
            runCatching { am.set(AlarmManager.RTC_WAKEUP, next, pi) }
        }
    }

    fun cancelWatchdog(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(watchdogIntent(ctx)) }
    }

    /** Arranca el guardián si el dispositivo está vinculado a una cuenta. */
    fun ensureRunning(ctx: Context) {
        runCatching {
            if (FirebaseSync(ctx.applicationContext).isLinked()) GuardianService.start(ctx)
        }
    }

    private fun watchdogIntent(ctx: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(
            ctx, WATCHDOG_REQ, Intent(ctx, WatchdogReceiver::class.java), flags
        )
    }
}
