package com.example.geofinence


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent


class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {


        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent!!.hasError()) {
            Log.d(TAG, "onReceive: Error receiving geofence event...")
            return
        }

        val geofenceList = geofencingEvent.triggeringGeofences
        for (geofence in geofenceList!!) {
            Log.d(TAG, "onReceive: " + geofence.requestId)
        }
        //        Location location = geofencingEvent.getTriggeringLocation();
        val transitionType = geofencingEvent.geofenceTransition
        Log.d(TAG, "onReceive: $transitionType")

        val geofenceId = geofenceList[0].requestId
        val actionIntent = Intent("GEOFENCE_EVENT")
        actionIntent.putExtra("geofenceId", geofenceId)
        actionIntent.putExtra("transitionType", transitionType)
        LocalBroadcastManager.getInstance(context).sendBroadcast(actionIntent)

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "onReceive: Enter")
                Toast.makeText(
                    context.applicationContext,
                    "GEOFENCE_TRANSITION_ENTER",
                    Toast.LENGTH_SHORT
                ).show()
            }

            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                Log.d(TAG, "onReceive: DWELL")
                Toast.makeText(
                    context.applicationContext,
                    "GEOFENCE_TRANSITION_DWELL",
                    Toast.LENGTH_SHORT
                ).show()
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "onReceive: EXIT")
                Toast.makeText(
                    context.applicationContext,
                    "GEOFENCE_TRANSITION_EXIT",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiv"
    }
}