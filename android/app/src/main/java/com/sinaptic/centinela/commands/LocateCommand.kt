package com.sinaptic.centinela.commands

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sinaptic.centinela.data.DeviceRepository

/** Obtiene la ubicación actual y la sube al backend. */
class LocateCommand(private val context: Context) {

    @SuppressLint("MissingPermission") // el permiso se verifica antes de habilitar la función
    fun execute(requestId: String) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    Log.w(TAG, "Ubicación nula")
                    return@addOnSuccessListener
                }
                DeviceRepository(context).uploadLocation(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracy = loc.accuracy,
                    requestId = requestId
                )
            }
            .addOnFailureListener { Log.e(TAG, "Error de ubicación", it) }
    }

    companion object { private const val TAG = "LocateCommand" }
}
