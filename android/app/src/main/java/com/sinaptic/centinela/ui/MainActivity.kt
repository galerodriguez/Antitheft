package com.sinaptic.centinela.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sinaptic.centinela.R
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver
import com.sinaptic.centinela.commands.CommandDispatcher
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.data.PinManager
import com.sinaptic.centinela.location.LocationService
import com.sinaptic.centinela.sos.SosCommand

/**
 * Pantalla principal: estado, toggles de funciones y botón SOS.
 *
 * Ruteo de arranque:
 *   sin consentimiento  -> OnboardingActivity
 *   sin PIN             -> PinActivity (SETUP)
 *   con PIN             -> PinActivity (UNLOCK)  antes de mostrarse
 */
class MainActivity : AppCompatActivity() {

    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupUi()
    }

    override fun onStart() {
        super.onStart()
        val prefs = DeviceRepository(this).prefs()
        val pin = PinManager(this)

        // 1) Consentimiento
        if (!prefs.getBoolean("consent_accepted", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java)); finish(); return
        }
        // 2) Bloqueo por PIN al abrir
        if (!unlocked) {
            val mode = if (pin.isPinSet()) PinActivity.MODE_UNLOCK else PinActivity.MODE_SETUP
            startActivity(Intent(this, PinActivity::class.java)
                .putExtra(PinActivity.EXTRA_MODE, mode))
            unlocked = true
        }
    }

    override fun onStop() {
        super.onStop()
        unlocked = false // vuelve a pedir PIN la próxima vez que se abra
    }

    private fun setupUi() {
        val prefs = DeviceRepository(this).prefs()

        findViewById<Switch>(R.id.switchPhoto).apply {
            isChecked = prefs.getBoolean("antitheft_photo_enabled", false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("antitheft_photo_enabled", on).apply()
            }
        }

        findViewById<Switch>(R.id.switchTracking).apply {
            isChecked = prefs.getBoolean("tracking_enabled", false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("tracking_enabled", on).apply()
                if (on) startTracking() else stopService(Intent(this@MainActivity, LocationService::class.java))
            }
        }

        findViewById<Button>(R.id.btnDeviceAdmin).setOnClickListener { requestDeviceAdmin() }

        findViewById<Button>(R.id.btnSos).setOnClickListener {
            SosCommand(this).trigger()
        }
    }

    private fun startTracking() {
        val i = Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }
        ContextCompat.startForegroundService(this, i)
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = CentinelaDeviceAdminReceiver.componentName(this)
        if (dpm.isAdminActive(admin)) return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation))
        }
        startActivity(intent)
    }
}
