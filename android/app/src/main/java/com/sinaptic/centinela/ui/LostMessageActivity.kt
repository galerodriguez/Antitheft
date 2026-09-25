package com.sinaptic.centinela.ui

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
            intent.getStringExtra("message") ?: getString(R.string.lost_default)
    }

    // No permitir salir con el botón atrás.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* bloqueado a propósito */ }
}
