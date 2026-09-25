package com.sinaptic.centinela.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sinaptic.centinela.commands.CommandDispatcher
import com.sinaptic.centinela.data.PinManager

/**
 * Administrador de dispositivo. Habilita bloqueo remoto, borrado y detección de intentos
 * de desbloqueo fallidos (para la foto del intruso).
 *
 * El usuario debe activarlo explícitamente en el onboarding (Ajustes > Seguridad >
 * Administradores del dispositivo). No se puede activar de forma silenciosa: Android
 * siempre muestra la pantalla de confirmación al usuario.
 */
class CentinelaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin habilitado por el usuario")
    }

    /**
     * Se dispara cuando alguien intenta DESACTIVAR el administrador (paso obligatorio para
     * poder desinstalar la app). Devolvemos un texto de advertencia; si hay PIN configurado,
     * el usuario deberá haberlo ingresado en la app para llegar hasta acá.
     *
     * Nota: Android no permite que una app se niegue por completo a que la desactiven, pero
     * esta advertencia + el PIN de acceso a los ajustes es la protección anti-desinstalación
     * estándar y aprobable (la misma que usan las apps antirrobo y de control parental).
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Se solicitó desactivar el administrador de dispositivo")
        return if (PinManager(context).isPinSet())
            "Para desactivar Antitheft y poder desinstalarla necesitás el PIN maestro. " +
            "Si no lo tenés, este dispositivo está protegido contra robo."
        else
            "Desactivar Antitheft quitará la protección antirrobo de este dispositivo."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin deshabilitado")
    }

    /** Se dispara cuando alguien ingresa un PIN/patrón incorrecto: candidato a foto del intruso. */
    override fun onPasswordFailed(context: Context, intent: Intent, user: android.os.UserHandle) {
        Log.i(TAG, "Intento de desbloqueo fallido")
        CommandDispatcher(context).onFailedUnlock()
    }

    companion object {
        private const val TAG = "CentinelaAdmin"
        fun componentName(context: Context) =
            ComponentName(context, CentinelaDeviceAdminReceiver::class.java)
    }
}
