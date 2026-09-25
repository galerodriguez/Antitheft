package com.sinaptic.centinela.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contracts.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sinaptic.centinela.R
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.data.PinManager
import com.sinaptic.centinela.location.LocationService
import com.sinaptic.centinela.sos.SosCommand

/**
 * Pantalla principal + puerta de entrada (consentimiento / PIN) + permisos en runtime.
 *
 * Ubicación:
 *  1) Se pide la ubicación en primer plano (FINE/COARSE) + notificaciones (Android 13+).
 *  2) Luego, por separado, la ubicación en SEGUNDO PLANO ("Permitir todo el tiempo"), que es
 *     lo que permite al modo Familiar seguir compartiendo con la app cerrada (Android 10+).
 * Cámara: se pide al activar la foto antirrobo.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var switchPhoto: Switch
    private lateinit var switchTracking: Switch

    // Paso 1: ubicación en primer plano (+ notificaciones)
    private val fgLocPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                 result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) ensureBackgroundThenEnable()
        else {
            switchTracking.isChecked = false
            toast("Se necesita permiso de ubicación para compartir tu ubicación.")
        }
    }

    // Paso 2: ubicación en segundo plano ("Permitir todo el tiempo")
    private val bgLocPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        enableTracking() // el rastreo se activa igual; el background mejora el modo cerrado
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toast("Para compartir con la app cerrada, elegí \"Permitir todo el tiempo\" en Ajustes.")
        }
    }

    private val cameraPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setPhotoEnabled(true)
        else {
            switchPhoto.isChecked = false
            toast("Se necesita permiso de cámara para la foto antirrobo.")
        }
    }

    private val sosPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        SosCommand(this).trigger()
        toast("SOS activado: avisando a tus contactos.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = DeviceRepository(this).prefs()

        if (!prefs.getBoolean("consent_accepted", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java)); finish(); return
        }
        if (!unlockedThisProcess) {
            val mode = if (PinManager(this).isPinSet())
                PinActivity.MODE_UNLOCK else PinActivity.MODE_SETUP
            startActivity(Intent(this, PinActivity::class.java)
                .putExtra(PinActivity.EXTRA_MODE, mode))
            finish(); return
        }
        setContentView(R.layout.activity_main)
        setupUi()
    }

    private fun setupUi() {
        val prefs = DeviceRepository(this).prefs()

        switchPhoto = findViewById(R.id.switchPhoto)
        switchPhoto.isChecked = prefs.getBoolean("antitheft_photo_enabled", false)
        switchPhoto.setOnCheckedChangeListener { _, on ->
            if (on) {
                if (hasPermission(Manifest.permission.CAMERA)) setPhotoEnabled(true)
                else cameraPerm.launch(Manifest.permission.CAMERA)
            } else setPhotoEnabled(false)
        }

        switchTracking = findViewById(R.id.switchTracking)
        switchTracking.isChecked = prefs.getBoolean("tracking_enabled", false)
        switchTracking.setOnCheckedChangeListener { _, on ->
            if (on) {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) ensureBackgroundThenEnable()
                else fgLocPerms.launch(foregroundLocationPerms())
            } else disableTracking()
        }

        findViewById<Button>(R.id.btnDeviceAdmin).setOnClickListener { requestDeviceAdmin() }
        findViewById<Button>(R.id.btnSos).setOnClickListener { onSos() }
    }

    /** Tras tener la ubicación de primer plano, pide (con explicación) la de segundo plano. */
    private fun ensureBackgroundThenEnable() {
        val needsBg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (!needsBg) { enableTracking(); return }

        AlertDialog.Builder(this)
            .setTitle("Ubicación en segundo plano")
            .setMessage("Para que el modo Familiar comparta tu ubicación con la app cerrada, " +
                "Android va a pedirte que elijas \"Permitir todo el tiempo\" en la siguiente pantalla.")
            .setPositiveButton("Continuar") { _, _ ->
                bgLocPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton("Solo con la app abierta") { _, _ ->
                enableTracking() // funciona en primer plano; sin background prolongado
            }
            .setCancelable(false)
            .show()
    }

    private fun onSos() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            SosCommand(this).trigger()
            toast("SOS activado: compartiendo tu ubicación.")
        } else {
            sosPerms.launch(foregroundLocationPerms())
        }
    }

    private fun setPhotoEnabled(on: Boolean) {
        DeviceRepository(this).prefs().edit().putBoolean("antitheft_photo_enabled", on).apply()
    }

    private fun enableTracking() {
        DeviceRepository(this).prefs().edit().putBoolean("tracking_enabled", true).apply()
        val i = Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }
        ContextCompat.startForegroundService(this, i)
        toast("Compartiendo ubicación.")
    }

    private fun disableTracking() {
        DeviceRepository(this).prefs().edit().putBoolean("tracking_enabled", false).apply()
        stopService(Intent(this, LocationService::class.java))
    }

    private fun foregroundLocationPerms(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = CentinelaDeviceAdminReceiver.componentName(this)
        if (dpm.isAdminActive(admin)) {
            toast("La protección antirrobo ya está activa.")
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation))
        }
        startActivity(intent)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    companion object {
        @JvmStatic
        var unlockedThisProcess = false
    }
}
