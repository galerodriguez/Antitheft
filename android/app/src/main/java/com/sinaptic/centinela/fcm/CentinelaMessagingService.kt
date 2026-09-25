package com.sinaptic.centinela.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sinaptic.centinela.commands.CommandDispatcher
import com.sinaptic.centinela.data.DeviceRepository

/**
 * Recibe los comandos remotos enviados desde el panel web vía FCM.
 *
 * IMPORTANTE: el push solo transporta la SEÑAL del comando (ej. LOCATE). Los datos
 * sensibles (ubicación, fotos) NO viajan en el push: la app los sube al backend por HTTPS.
 */
class CentinelaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Cada vez que FCM rota el token, lo registramos en el backend.
        DeviceRepository(applicationContext).updateFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val command = data["command"] ?: return
        val requestId = data["requestId"] ?: ""
        Log.i(TAG, "Comando recibido: $command ($requestId)")

        CommandDispatcher(applicationContext).dispatch(command, requestId, data)
    }

    companion object {
        private const val TAG = "CentinelaFCM"
    }
}
