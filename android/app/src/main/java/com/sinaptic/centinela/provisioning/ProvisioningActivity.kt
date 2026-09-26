package com.sinaptic.centinela.provisioning

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import com.sinaptic.centinela.admin.DeviceOwnerManager

/**
 * Activity que el asistente de provisión de Android invoca durante la instalación por QR
 * (Device Owner). En Android 10+ el "Device Policy Controller" DEBE responder a estos dos
 * intents o la provisión se cancela:
 *   - GET_PROVISIONING_MODE  -> declaramos que somos un dispositivo TOTALMENTE administrado.
 *   - ADMIN_POLICY_COMPLIANCE -> confirmamos que ya aplicamos nuestras políticas.
 */
class ProvisioningActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            "android.app.action.GET_PROVISIONING_MODE" -> {
                val result = Intent().putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )
                setResult(RESULT_OK, result)
            }
            "android.app.action.ADMIN_POLICY_COMPLIANCE" -> {
                // Ya somos Device Owner: aplicamos el blindaje y confirmamos cumplimiento.
                runCatching { DeviceOwnerManager.applyIfOwner(applicationContext) }
                setResult(RESULT_OK)
            }
            else -> setResult(RESULT_OK)
        }
        finish()
    }
}
