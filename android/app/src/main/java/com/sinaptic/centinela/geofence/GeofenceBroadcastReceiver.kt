package com.sinaptic.centinela.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.sinaptic.centinela.data.DeviceRepository

/** Recibe las transiciones de geocerca y reporta el evento al backend. */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Error de geocerca: ${event.errorCode}")
            return
        }
        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "GEOFENCE_ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT  -> "GEOFENCE_EXIT"
            else -> return
        }
        val ids = event.triggeringGeofences?.joinToString { it.requestId } ?: ""
        DeviceRepository(context).reportEvent(type, "Zonas: $ids")
    }

    companion object { private const val TAG = "GeofenceRx" }
}
