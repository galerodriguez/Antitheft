package com.sinaptic.centinela.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Modo Familiar/Kids: define zonas (casa, escuela) y avisa al entrar/salir.
 * Requiere consentimiento del titular del dispositivo monitoreado.
 */
class GeofenceManager(private val context: Context) {

    private val client = LocationServices.getGeofencingClient(context)

    @SuppressLint("MissingPermission")
    fun addFence(id: String, lat: Double, lng: Double, radiusM: Float,
                 onEnter: Boolean = true, onExit: Boolean = true) {
        var transitions = 0
        if (onEnter) transitions = transitions or Geofence.GEOFENCE_TRANSITION_ENTER
        if (onExit)  transitions = transitions or Geofence.GEOFENCE_TRANSITION_EXIT

        val fence = Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(lat, lng, radiusM)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence)
            .build()

        client.addGeofences(request, pendingIntent())
    }

    fun removeFence(id: String) = client.removeGeofences(listOf(id))

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
