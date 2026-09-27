package com.sinaptic.centinela.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sinaptic.centinela.ui.MainActivity

/**
 * Recibe el "código secreto" marcado en el teléfono para reabrir la app cuando el ícono está oculto.
 *
 * Se marca:  *#*#74633277#*#*   (74633277 = "PINDEAPP" en el teclado)
 *
 * Android entrega este broadcast solo a receptores declarados en el manifest y solo con códigos
 * numéricos. Al recibirlo, abrimos la app (que enseguida pide el PIN maestro).
 *
 * Nota: en algunos teléfonos/capas (Samsung, Xiaomi) este mecanismo puede estar bloqueado por el
 * fabricante. Por eso el portal también tiene "Mostrar ícono" como método garantizado.
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching {
            Log.i("SecretCode", "Código secreto recibido: reabriendo app")
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(open)
        }
    }
}
