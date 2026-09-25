package com.sinaptic.centinela.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.ListenerRegistration
import com.sinaptic.centinela.R
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver
import com.sinaptic.centinela.commands.CommandDispatcher
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.data.FirebaseSync
import com.sinaptic.centinela.data.PinManager
import com.sinaptic.centinela.location.LocationService
import com.sinaptic.centinela.sos.SosCommand

/**
 * Pantalla principal + puerta de entrada (consentimiento / PIN) + permisos en runtime +
 * sincronización con el portal (Firebase).
 *
 * Control remoto de funciones: la app escucha en tiempo real los ajustes en Firestore. Si desde
 * el portal se prende/apaga "Compartir ubicación" o "Foto antirrobo", la app reacciona sola; y si
 * el usuario lo cambia en la app, se refleja en Firestore.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var switchPhoto: Switch
    private lateinit var switchTracking: Switch
    private lateinit var accountStatus: TextView
    private lateinit var btnLink: Button

    private lateinit var sync: FirebaseSync
    private var listener: ListenerRegistration? = null
    private var applyingRemote = false   // evita bucles al aplicar cambios que vienen del portal

    // --- Permisos --------------------------------------------------------------------
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

    private val bgLocPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        enableTracking()
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

    // --- Ciclo de vida ---------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sync = FirebaseSync(this)

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

    override fun onStart() {
        super.onStart()
        // Solo cuando ya está mostrada la UI (no en los pasos de gating)
        if (::switchTracking.isInitialized) {
            refreshAccountUi()
            startFirebaseListener()
        }
    }

    override fun onDestroy() {
        listener?.remove(); listener = null
        super.onDestroy()
    }

    // --- UI --------------------------------------------------------------------------
    private fun setupUi() {
        val prefs = DeviceRepository(this).prefs()

        accountStatus = findViewById(R.id.accountStatus)
        btnLink = findViewById(R.id.btnLink)
        btnLink.setOnClickListener {
            if (sync.isLinked()) confirmUnlink()
            else startActivity(Intent(this, LinkAccountActivity::class.java))
        }

        switchPhoto = findViewById(R.id.switchPhoto)
        switchPhoto.isChecked = prefs.getBoolean("antitheft_photo_enabled", false)
        switchPhoto.setOnCheckedChangeListener { _, on ->
            if (on) {
                if (hasPermission(Manifest.permission.CAMERA)) setPhotoEnabled(true)
                else cameraPerm.launch(Manifest.permission.CAMERA)
            } else setPhotoEnabled(false)
            if (!applyingRemote) sync.pushSetting("photo", on)
        }

        switchTracking = findViewById(R.id.switchTracking)
        switchTracking.isChecked = prefs.getBoolean("tracking_enabled", false)
        switchTracking.setOnCheckedChangeListener { _, on ->
            if (on) {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) ensureBackgroundThenEnable()
                else fgLocPerms.launch(foregroundLocationPerms())
            } else disableTracking()
            if (!applyingRemote) sync.pushSetting("tracking", on)
        }

        findViewById<Button>(R.id.btnDeviceAdmin).setOnClickListener { requestDeviceAdmin() }
        findViewById<Button>(R.id.btnSos).setOnClickListener { onSos() }
    }

    private fun refreshAccountUi() {
        if (sync.isLinked()) {
            accountStatus.text = getString(R.string.account_linked, sync.currentEmail() ?: "")
            btnLink.text = getString(R.string.btn_unlink)
        } else {
            accountStatus.text = getString(R.string.account_not_linked)
            btnLink.text = getString(R.string.btn_link)
        }
    }

    // --- Firebase --------------------------------------------------------------------
    private fun startFirebaseListener() {
        listener?.remove(); listener = null
        if (!sync.isLinked()) return
        sync.registerDevice()
        listener = sync.listen(
            onSettings = { tracking, photo -> applyRemoteSettings(tracking, photo) },
            onCommand = { type -> onRemoteCommand(type) },
        )
    }

    /** Aplica los ajustes que vienen del portal, sin re-escribirlos (evita bucles). */
    private fun applyRemoteSettings(tracking: Boolean, photo: Boolean) {
        applyingRemote = true
        if (switchTracking.isChecked != tracking) switchTracking.isChecked = tracking
        if (switchPhoto.isChecked != photo) switchPhoto.isChecked = photo
        applyingRemote = false
    }

    private fun onRemoteCommand(type: String) {
        when (type.uppercase()) {
            "LOCATE" -> fetchAndUploadLocation()
            else -> CommandDispatcher(this).dispatch(type, "", emptyMap())
        }
        toast("Comando del portal: $type")
    }

    @SuppressLint("MissingPermission")
    private fun fetchAndUploadLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null) sync.uploadLocation(loc.latitude, loc.longitude) }
    }

    private fun confirmUnlink() {
        AlertDialog.Builder(this)
            .setTitle("Desvincular")
            .setMessage("Vas a desconectar este dispositivo del portal. ¿Continuar?")
            .setPositiveButton("Desvincular") { _, _ ->
                sync.signOut(); listener?.remove(); listener = null; refreshAccountUi()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- Permisos / acciones ---------------------------------------------------------
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
            .setNegativeButton("Solo con la app abierta") { _, _ -> enableTracking() }
            .setCancelable(false)
            .show()
    }

    private fun onSos() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            SosCommand(this).trigger()
            toast("SOS activado: compartiendo tu ubicación.")
        } else sosPerms.launch(foregroundLocationPerms())
    }

    private fun setPhotoEnabled(on: Boolean) {
        DeviceRepository(this).prefs().edit().putBoolean("antitheft_photo_enabled", on).apply()
    }

    private fun enableTracking() {
        DeviceRepository(this).prefs().edit().putBoolean("tracking_enabled", true).apply()
        val i = Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }
        ContextCompat.startForegroundService(this, i)
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
        if (dpm.isAdminActive(admin)) { toast("La protección antirrobo ya está activa."); return }
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
