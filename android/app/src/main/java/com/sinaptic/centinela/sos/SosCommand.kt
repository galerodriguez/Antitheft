package com.sinaptic.centinela.sos

import android.content.Context
import android.util.Log
import com.sinaptic.centinela.data.DeviceRepository

/**
 * SOS / Persona: el usuario dispara una emergencia. Sube su ubicación en vivo y notifica
 * a los contactos de confianza (círculo) a través del backend, que envía el aviso.
 */
class SosCommand(private val context: Context) {

    fun trigger() {
        Log.i(TAG, "SOS disparado por el usuario")
        // 1) Marca sesión SOS en el backend (activa compartir ubicación en tiempo real).
        DeviceRepository(context).startSosSession()
        // 2) Sube ubicación inmediata.
        com.sinaptic.centinela.commands.LocateCommand(context).execute("sos")
        // 3) El backend notifica a los contactos del círculo (push/SMS/email).
    }

    companion object { private const val TAG = "SosCommand" }
}
