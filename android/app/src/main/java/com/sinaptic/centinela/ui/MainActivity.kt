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
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
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
 * Pantalla principal + consentimiento + BLOQUEO POR PIN + permisos + sincronización con el portal.
 *
 * Seguridad del PIN: se pide el PIN cada vez que la app vuelve desde segundo plano. El re-bloqueo
 * lo dispara CentinelaApp (cuando la app deja de estar visible pone unlockedThisProcess=false).
 * Acá, en onResume, si está bloqueada, se muestra PinActivity antes de dejar usar la app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var switchPhoto: Switch
    private lateinit var switchTracking: Switch
    private lateinit var accountStatus: android.widget.TextView
    private lateinit var btnLink: Button

    private lateinit var sync: FirebaseSync
    private var listener: ListenerRegistration? = null
    private var applyingRemote = false
    private var gateInFlight = false     // hay una PinActivity abierta esperando desbloqueo

    // --- Puerta del PIN (se relanza al volver de segundo plano) ----------------------
    private val pinGate = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        gateInFlight = false
        if (unlockedThisProcess) {
            refreshAccountUi(); startFirebaseListener()
        } else {
            // El usuario salió sin desbloquear: cerramos para no dejar la app accesible.
            finishAffinity()
        }
    }

    // --- Permisos --------------------------------------------------------------------
    private val fgLocPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                 result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) ensureBackgroundThenEnable()
        else { switchTracking.isChecked = false; toast("Se necesita permiso de ubicación.") }
    }
    private val bgLocPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        enableTracking()
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            toast("Para compartir con la app cerrada, elegí \"Permitir todo el tiempo\" en Ajustes.")
    }
    private val cameraPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setPhotoEnabled(true)
        else { switchPhoto.isChecked = false; toast("Se necesita permiso de cámara para la foto antirrobo.") }
    }
    private val sosPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> SosCommand(this).trigger(); toast("SOS activado: avisando a tus contactos.") }

    // Diálogo del sistema para ENCENDER la ubicación con un toque.
    private var pendingAfterGps: (() -> Unit)? = null
    private val gpsResolution = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingAfterGps; pendingAfterGps = null
        if (result.resultCode == RESULT_OK) action?.invoke()
        else toast("Necesitás encender la ubicación para esta función.")
    }

    // --- Ciclo de vida ---------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sync = FirebaseSync(this)

        val prefs = DeviceRepository(this).prefs()
        if (!prefs.getBoolean("consent_accepted", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        setupUi()
    }

    override fun onResume() {
        super.onResume()
        if (!DeviceRepository(this).prefs().getBoolean("consent_accepted", false)) return

        if (!unlockedThisProcess) {
            if (!gateInFlight) {
                gateInFlight = true
                val mode = if (PinManager(this).isPinSet())
                    PinActivity.MODE_UNLOCK else PinActivity.MODE_SETUP
                pinGate.launch(Intent(this, PinActivity::class.java)
                    .putExtra(PinActivity.EXTRA_MODE, mode))
            }
            return
        }
        refreshAccountUi()
        startFirebaseListener()
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
        if (!::accountStatus.isInitialized) return
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
        sync.reportStatus()
        listener = sync.listen(
            onSettings = { tracking, photo -> applyRemoteSettings(tracking, photo) },
            onCommand = { type, message -> onRemoteCommand(type, message) },
        )
    }

    private fun applyRemoteSettings(tracking: Boolean, photo: Boolean) {
        applyingRemote = true
        if (switchTracking.isChecked != tracking) switchTracking.isChecked = tracking
        if (switchPhoto.isChecked != photo) switchPhoto.isChecked = photo
        applyingRemote = false
    }

    private fun onRemoteCommand(type: String, message: String?) {
        when (type.uppercase()) {
            "LOCATE" -> fetchAndUploadLocation()
            "LOCK" -> lockWithMessage(message)
            else -> CommandDispatcher(this).dispatch(type, "", emptyMap())
        }
        toast("Comando del portal: $type")
    }

    /** Bloquea la pantalla y muestra una pantalla de "teléfono protegido" con el mensaje. */
    private fun lockWithMessage(message: String?) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = CentinelaDeviceAdminReceiver.componentName(this)
        if (dpm.isAdminActive(admin)) dpm.lockNow()
        val msg = if (message.isNullOrBlank()) getString(R.string.lost_default) else message
        startActivity(Intent(this, LostMessageActivity::class.java)
            .putExtra("message", msg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Verifica que la ubicación del sistema esté encendida; si no, muestra el diálogo para prenderla.
     * Usamos prioridad "equilibrada": alcanza con ubicación por RED (WiFi/antenas), no exige GPS.
     */
    private fun ensureGpsThen(action: () -> Unit) {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L).build()
        val settings = LocationSettingsRequest.Builder().addLocationRequest(req).build()
        LocationServices.getSettingsClient(this).checkLocationSettings(settings)
            .addOnSuccessListener { action() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    pendingAfterGps = action
                    runCatching {
                        gpsResolution.launch(IntentSenderRequest.Builder(e.resolution).build())
                    }.onFailure { toast("No se pudo abrir el diálogo de ubicación.") }
                } else {
                    toast("Encendé la ubicación del teléfono para esta función.")
                }
            }
    }

    private fun fetchAndUploadLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            toast("Activá \"Compartir ubicación\" en la app para permitir localizar.")
            return
        }
        ensureGpsThen { doFetchLocation() }
    }

    @SuppressLint("MissingPermission")
    private fun doFetchLocation() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        toast("Buscando ubicación…")
        // Prioridad equilibrada: usa WiFi/antenas (red) además del GPS -> funciona sin GPS.
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    sync.uploadLocation(loc.latitude, loc.longitude)
                    toast("Ubicación enviada.")
                } else {
                    // Plan B: última ubicación conocida.
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) {
                                sync.uploadLocation(last.latitude, last.longitude)
                                toast("Ubicación (última conocida) enviada.")
                            } else {
                                toast("No se pudo obtener ubicación. Revisá que el GPS del teléfono esté encendido.")
                            }
                        }
                        .addOnFailureListener { toast("No se pudo obtener ubicación (GPS apagado?).") }
                }
            }
            .addOnFailureListener { toast("Error al localizar: ${it.message}") }
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
            .setPositiveButton("Continuar") { _, _ -> bgLocPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            .setNegativeButton("Solo con la app abierta") { _, _ -> enableTracking() }
            .setCancelable(false)
            .show()
    }

    private fun onSos() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            SosCommand(this).trigger(); toast("SOS activado: compartiendo tu ubicación.")
        } else sosPerms.launch(foregroundLocationPerms())
    }

    private fun setPhotoEnabled(on: Boolean) {
        DeviceRepository(this).prefs().edit().putBoolean("antitheft_photo_enabled", on).apply()
    }

    private fun enableTracking() {
        DeviceRepository(this).prefs().edit().putBoolean("tracking_enabled", true).apply()
        ensureGpsThen {
            val i = Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }
            ContextCompat.startForegroundService(this, i)
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
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
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_explanation))
        }
        startActivity(intent)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    companion object {
        @JvmStatic
        var unlockedThisProcess = false
    }
}
