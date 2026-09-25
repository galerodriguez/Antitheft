package com.sinaptic.centinela.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sinaptic.centinela.R
import com.sinaptic.centinela.data.FirebaseSync

/**
 * Vincula la app con la cuenta del portal (Firebase Auth, email/contraseña).
 * Con la misma cuenta, la app y el portal ven el mismo dispositivo y sus ajustes.
 */
class LinkAccountActivity : AppCompatActivity() {

    private lateinit var sync: FirebaseSync
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_link)
        sync = FirebaseSync(this)

        email = findViewById(R.id.email)
        password = findViewById(R.id.password)
        error = findViewById(R.id.linkError)

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { doAuth(register = false) }
        findViewById<Button>(R.id.btnRegister).setOnClickListener { doAuth(register = true) }
    }

    private fun doAuth(register: Boolean) {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (e.isEmpty() || p.length < 6) {
            error.text = getString(R.string.link_invalid)
            return
        }
        error.text = getString(R.string.link_working)

        val cb: (Boolean, String?) -> Unit = { ok, msg ->
            if (ok) {
                sync.registerDevice()
                finish() // vuelve a la principal, que ya detecta la vinculación
            } else {
                error.text = msg ?: getString(R.string.link_error)
            }
        }
        if (register) sync.register(e, p, cb) else sync.signIn(e, p, cb)
    }
}
