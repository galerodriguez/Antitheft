package com.sinaptic.centinela.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.sinaptic.centinela.R
import com.sinaptic.centinela.data.DeviceRepository

/**
 * Primer arranque: explica en lenguaje claro qué hace la app y pide consentimiento explícito.
 * Al aceptar, marca el consentimiento y pasa a crear el PIN maestro.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<Button>(R.id.btnAccept).setOnClickListener {
            DeviceRepository(this).prefs().edit()
                .putBoolean("consent_accepted", true)
                .putLong("consent_at", System.currentTimeMillis())
                .apply()

            // Ir a crear el PIN maestro.
            startActivity(Intent(this, PinActivity::class.java)
                .putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP))
            finish()
        }
    }
}
