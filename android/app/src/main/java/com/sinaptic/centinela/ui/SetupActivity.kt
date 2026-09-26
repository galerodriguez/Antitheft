package com.sinaptic.centinela.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sinaptic.centinela.admin.CentinelaDeviceAdminReceiver
import com.sinaptic.centinela.data.DeviceRepository
import com.sinaptic.centinela.service.BackgroundGuard

/**
 * Checklist de configuración: muestra en verde lo que ya está OK y, en lo que falta, un botón
 * que lleva directo al ajuste. Se re-verifica solo cada vez que se vuelve a la pantalla.
 * Pensado para dejar el teléfono "a prueba de balas" en la instalación única.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var summary: TextView

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { render() }
    private val reqOne = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { render() }
    private val genericResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0D1117.toInt())
            isFillViewport = true
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(container)
        setContentView(scroll)
        title = "Configuración"
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        container.removeAllViews()

        val header = TextView(this).apply {
            text = "Configuración de Antitheft"
            setTextColor(Color.WHITE); textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(header)

        summary = TextView(this).apply {
            setTextColor(0xFF8B949E.toInt()); textSize = 14f
            setPadding(0, dp(6), 0, dp(16))
        }
        container.addView(summary)

        val items = buildItems()
        var pending = 0
        items.forEach { if (!it.ok()) pending++; container.addView(rowFor(it)) }

        summary.text = if (pending == 0)
            "✅ ¡Todo configurado! El teléfono está protegido."
        else "Faltan $pending paso(s) para que quede protegido al 100%."
        summary.setTextColor(if (pending == 0) 0xFF3FB950.toInt() else 0xFFD29922.toInt())
    }

    // ---- Definición de los ítems del checklist ----
    private data class Item(
        val title: String, val desc: String,
        val ok: () -> Boolean, val manual: Boolean = false,
        val action: () -> Unit,
    )

    private fun buildItems(): List<Item> {
        val list = mutableListOf<Item>()

        list.add(Item(
            "Ubicación: Permitir todo el tiempo",
            "Para localizar el teléfono con la app cerrada.",
            { has(Manifest.permission.ACCESS_FINE_LOCATION) &&
              (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) },
        ) {
            when {
                !has(Manifest.permission.ACCESS_FINE_LOCATION) ->
                    reqPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> openAppDetails()
                else -> {}
            }
        })

        list.add(Item(
            "Cámara",
            "Para la foto del intruso tras PIN fallidos.",
            { has(Manifest.permission.CAMERA) },
        ) { reqOne.launch(Manifest.permission.CAMERA) })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Item(
                "Notificaciones",
                "Necesario para el servicio de protección.",
                { has(Manifest.permission.POST_NOTIFICATIONS) },
            ) { reqOne.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }

        list.add(Item(
            "Batería sin restricciones",
            "Que Android no duerma la app. Imprescindible.",
            { BackgroundGuard.isIgnoringBatteryOptimizations(this) },
        ) { BackgroundGuard.promptIgnoreBatteryOptimizations(this) })

        list.add(Item(
            "Administrador de dispositivo",
            "Habilita bloqueo y borrado remoto.",
            { isAdmin() },
        ) {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    CentinelaDeviceAdminReceiver.componentName(this))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Antitheft necesita esto para bloquear o borrar el teléfono si te lo roban.")
            genericResult.launch(i)
        })

        // Paso manual de Samsung (no hay API para leerlo): lo confirma el usuario.
        list.add(Item(
            "Que nunca se duerma (Samsung)",
            "Ajustes → Batería → Apps que nunca se duermen → agregá Antitheft. " +
            "Y en 'Pausar actividad si no se usa' → desactivar.",
            { DeviceRepository(this).prefs().getBoolean("samsung_sleep_done", false) },
            manual = true,
        ) { openAppDetails() })

        return list
    }

    // ---- Construcción de cada fila ----
    private fun rowFor(item: Item): LinearLayout {
        val ok = item.ok()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFF161B22.toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(5), 0, dp(5)); layoutParams = lp
        }

        val icon = TextView(this).apply {
            text = if (ok) "✓" else "○"
            setTextColor(if (ok) 0xFF3FB950.toInt() else 0xFF8B949E.toInt())
            textSize = 20f; setPadding(0, 0, dp(12), 0)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        card.addView(icon)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = item.title; setTextColor(Color.WHITE); textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = item.desc; setTextColor(0xFF8B949E.toInt()); textSize = 12.5f
        })
        card.addView(texts)

        if (!ok) {
            val btn = Button(this).apply {
                text = if (item.manual) "Abrir" else "Configurar"
                textSize = 12f
                setOnClickListener {
                    item.action()
                    if (item.manual) confirmManual()
                }
            }
            card.addView(btn)
        }
        return card
    }

    /** Para el paso manual: tras abrir ajustes, ofrecemos marcarlo como hecho. */
    private fun confirmManual() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("¿Ya lo configuraste?")
            .setMessage("Agregaste Antitheft a 'Apps que nunca se duermen' y desactivaste " +
                "'Pausar actividad si no se usa'?")
            .setPositiveButton("Sí, listo") { _, _ ->
                DeviceRepository(this).prefs().edit().putBoolean("samsung_sleep_done", true).apply()
                render()
            }
            .setNegativeButton("Todavía no", null)
            .show()
    }

    private fun openAppDetails() {
        runCatching {
            genericResult.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName")))
        }
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun isAdmin(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(CentinelaDeviceAdminReceiver.componentName(this))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** Cuántos pasos críticos auto-detectables faltan (para sugerir abrir el checklist). */
        fun criticalPending(ctx: Context): Int {
            var n = 0
            fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) n++
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) n++
            if (!has(Manifest.permission.CAMERA)) n++
            if (!BackgroundGuard.isIgnoringBatteryOptimizations(ctx)) n++
            return n
        }
    }
}
