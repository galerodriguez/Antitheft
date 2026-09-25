package com.sinaptic.centinela.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sinaptic.centinela.data.FirebaseSync
import com.sinaptic.centinela.service.GuardianService

/**
 * Recepción de FCM.
 *  - onNewToken: guarda el token del dispositivo en Firestore.
 *  - onMessageReceived: despierta el servicio guardián, que lee el comando desde Firestore.
 *
 * Nota: enviar el push "puro" (para despertar una app completamente cerrada) requiere un
 * servidor (Cloud Functions / plan Blaze). Mientras tanto, el GuardianService mantiene la
 * escucha viva y los comandos llegan igual con el teléfono online.
 */
class CentinelaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "Nuevo token FCM")
        FirebaseSync(applicationContext).saveFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "Push recibido; despertando servicio guardián")
        runCatching { GuardianService.start(applicationContext) }
    }

    companion object { private const val TAG = "CentinelaFCM" }
}
