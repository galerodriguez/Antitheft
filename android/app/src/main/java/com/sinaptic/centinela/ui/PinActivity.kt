package com.sinaptic.centinela.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sinaptic.centinela.R
import com.sinaptic.centinela.data.PinManager

/**
 * Pantalla de bloqueo por PIN con teclado numérico.
 *
 * Modos:
 *  - SETUP: primer arranque. Pide el PIN dos veces (crear + confirmar).
 *  - UNLOCK: pide el PIN para acceder a la app.
 *  - VERIFY: verificación puntual (ej. antes de desactivar el admin). Devuelve RESULT_OK.
 */
class PinActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private lateinit var display: TextView
    private lateinit var prompt: TextView
    private lateinit var error: TextView

    private var mode = MODE_UNLOCK
    private val entered = StringBuilder()
    private var firstPin: String? = null   // para el paso de confirmación en SETUP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        pinManager = PinManager(this)
        mode = intent.getStringExtra(EXTRA_MODE)
            ?: if (pinManager.isPinSet()) MODE_UNLOCK else MODE_SETUP

        display = findViewById(R.id.pinDisplay)
        prompt = findViewById(R.id.pinPrompt)
        error = findViewById(R.id.pinError)

        if (mode == MODE_SETUP) prompt.setText(R.string.pin_setup_title)

        // Conectar los dígitos (botones con tag) y las teclas de acción.
        connectDigits(findViewById(android.R.id.content))
        findViewById<Button>(R.id.keyDelete).setOnClickListener { onDelete() }
        findViewById<Button>(R.id.keyOk).setOnClickListener { onOk() }
    }

    private fun connectDigits(view: View) {
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) connectDigits(view.getChildAt(i))
        } else if (view is Button && view.tag != null) {
            val digit = view.tag.toString()
            view.setOnClickListener { onDigit(digit) }
        }
    }

    private fun onDigit(d: String) {
        if (entered.length >= 8) return
        entered.append(d)
        render()
    }

    private fun onDelete() {
        if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
        render()
    }

    private fun onOk() {
        error.text = ""
        val pin = entered.toString()
        when (mode) {
            MODE_SETUP -> handleSetup(pin)
            else -> {
                if (pinManager.verify(pin)) success()
                else fail(getString(R.string.pin_wrong))
            }
        }
    }

    private fun handleSetup(pin: String) {
        if (firstPin == null) {
            if (pin.length < 4) return fail(getString(R.string.pin_too_short))
            firstPin = pin
            entered.clear(); render()
            prompt.setText(R.string.pin_confirm_title)
        } else {
            if (pin != firstPin) {
                firstPin = null
                entered.clear(); render()
                prompt.setText(R.string.pin_setup_title)
                return fail(getString(R.string.pin_mismatch))
            }
            pinManager.setPin(pin)
            success()
        }
    }

    private fun success() {
        // SETUP, UNLOCK y VERIFY: marcamos desbloqueado y devolvemos OK a quien nos llamó.
        MainActivity.unlockedThisProcess = true
        setResult(RESULT_OK)
        finish()
    }

    private fun fail(msg: String) {
        error.text = msg
        entered.clear(); render()
    }

    private fun render() {
        display.text = "•".repeat(entered.length)
    }

    // El botón atrás no debe saltear el PIN: en SETUP/UNLOCK manda la app a segundo plano.
    override fun onBackPressed() {
        when (mode) {
            MODE_VERIFY -> { setResult(RESULT_CANCELED); super.onBackPressed() }
            else -> moveTaskToBack(true)
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SETUP = "setup"
        const val MODE_UNLOCK = "unlock"
        const val MODE_VERIFY = "verify"
    }
}
