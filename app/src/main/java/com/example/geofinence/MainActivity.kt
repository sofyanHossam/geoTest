package com.example.geofinence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import androidx.core.graphics.scale
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val markersMap = mutableMapOf<String, Marker>()
    private val circlesMap = mutableMapOf<String, Circle>()
    private var mMap: GoogleMap? = null
    private var geofencingClient: GeofencingClient? = null
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocationMarker: Marker? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val GEOFENCE_RADIUS = 50f
        private const val FINE_LOCATION_ACCESS_REQUEST_CODE = 10001
        private const val BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002
    }

    // 27 نقاط على خط مستقيم تقريبا
    val points: List<LatLng> = listOf(
        LatLng(30.0444, 31.2357), // Tahrir Square
        LatLng(30.0450, 31.2370),
        LatLng(30.0458, 31.2380),
        LatLng(30.0465, 31.2390),
        LatLng(30.0470, 31.2400),
        LatLng(30.0475, 31.2410),
        LatLng(30.0480, 31.2420),
        LatLng(30.0485, 31.2430),
        LatLng(30.0490, 31.2440),
        LatLng(30.0495, 31.2450),
        LatLng(30.0500, 31.2460),
        LatLng(30.0505, 31.2470),
        LatLng(30.0510, 31.2480),
        LatLng(30.0515, 31.2490),
        LatLng(30.0520, 31.2500),
        LatLng(30.0525, 31.2510),
        LatLng(30.0530, 31.2520),
        LatLng(30.0535, 31.2530),
        LatLng(30.0540, 31.2540),
        LatLng(30.0545, 31.2550),
        LatLng(30.0550, 31.2560),
        LatLng(30.0555, 31.2570),
        LatLng(30.0560, 31.2580),
        LatLng(30.0565, 31.2590),
        LatLng(30.0570, 31.2600),
        LatLng(30.0575, 31.2610),
        LatLng(30.0580, 31.2620)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        geofencingClient = LocationServices.getGeofencingClient(this)
        geofenceHelper = GeofenceHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        LocalBroadcastManager.getInstance(this).registerReceiver(
            geofenceEventReceiver,
            IntentFilter("GEOFENCE_EVENT")
        )
    }

    private val geofenceEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val geofenceId = intent?.getStringExtra("geofenceId") ?: return
            val transitionType = intent.getIntExtra("transitionType", -1)
            handleGeofenceTransition(geofenceId, transitionType)
        }
    }

    private fun handleGeofenceTransition(geofenceId: String, transitionType: Int) {
        val marker = markersMap[geofenceId]
        val circle = circlesMap[geofenceId]

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                // اختياري: ترجع الشكل الأصلي
                marker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            }

            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                // ممكن تخلي لون أصفر مثلاً
                marker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                // تغير الايقون أو تمسحه
                marker?.remove()  // تمسح الماركر
                circle?.remove()  // تمسح الدايرة
                markersMap.remove(geofenceId)
                circlesMap.remove(geofenceId)
            }
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val startPoint = points.first()
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 16f))
        enableUserLocation()
        addAllPoints()
        drawRoutePolyline()
        startLocationUpdates()
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap?.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_LOCATION_ACCESS_REQUEST_CODE
            )
        }
    }

    private fun addAllPoints() {
        points.forEachIndexed { index, latLng ->
            val marker = mMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Point ${index + 1}")
            )
            markersMap["GEOFENCE_$index"] = marker!!

            val circle = mMap?.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(GEOFENCE_RADIUS.toDouble())
                    .strokeColor(Color.argb(255, 255, 0, 0))
                    .fillColor(Color.argb(64, 255, 0, 0))
                    .strokeWidth(4f)
            )
            circlesMap["GEOFENCE_$index"] = circle!!
            // Geofence
            addGeofence(latLng, "GEOFENCE_$index")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofence(latLng: LatLng, id: String) {
        val geofence = geofenceHelper.getGeofence(
            id,
            latLng,
            GEOFENCE_RADIUS,
            Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_DWELL or
                    Geofence.GEOFENCE_TRANSITION_EXIT
        )
        val geofencingRequest: GeofencingRequest = geofenceHelper.getGeofencingRequest(geofence)
        val pendingIntent: PendingIntent = geofenceHelper.geofencePendingIntent

        geofencingClient?.addGeofences(geofencingRequest, pendingIntent)
            ?.addOnSuccessListener { Log.d(TAG, "Geofence $id added") }
            ?.addOnFailureListener { e ->
                Log.d(TAG, "Geofence add failed: ${geofenceHelper.getErrorString(e)}")
            }
    }

    // رسم Route بين النقاط (Polyline) باستخدام Google Directions API
    private fun drawRoutePolyline() {
        // لتقسيم النقاط على جزئين
        val firstHalf = points.subList(0, points.size / 2)
        val secondHalf = points.subList(points.size / 2, points.size)

        // استدعاء Routes لكل جزء
        CoroutineScope(Dispatchers.IO).launch {
            val polyline1 = getPolylineFromGoogle(firstHalf)
            val polyline2 = getPolylineFromGoogle(secondHalf)

            withContext(Dispatchers.Main) {
                polyline1?.let { mMap?.addPolyline(it) }
                polyline2?.let { mMap?.addPolyline(it) }
            }
        }
    }

    private fun getPolylineFromGoogle(pointList: List<LatLng>): PolylineOptions? {
        if (pointList.size < 2) return null
        val origin = pointList.first()
        val destination = pointList.last()
        val waypoints = pointList.subList(1, pointList.size - 1)
            .joinToString("|") { "${it.latitude},${it.longitude}" }

        val apiKey = "AIzaSyDW_EEVCKH7tQlfp0mBUasWRgsAfKE6E1A"
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&waypoints=$waypoints&key=$apiKey"

        return try {
            val response = URL(url).readText()
            val json = JSONObject(response)
            val points = json.getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("overview_polyline")
                .getString("points")

            val decoded = decodePoly(points)
            PolylineOptions().addAll(decoded).color(Color.BLUE).width(6f)
        } catch (e: Exception) {
            Log.e(TAG, "Polyline fetch error: ${e.message}")
            null
        }
    }

    // Decode Polyline from Google API
    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat / 1E5, lng / 1E5)
            poly.add(p)
        }
        return poly
    }

    // متابعة موقع المستخدم وعرضه على Custom Marker
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                val latLng = LatLng(loc.latitude, loc.longitude)

                if (currentLocationMarker == null) {
                    val icon = BitmapDescriptorFactory.fromBitmap(
                        BitmapFactory.decodeResource(resources, R.drawable.img).scale(80, 80, false)
                    )
                    currentLocationMarker = mMap?.addMarker(
                        MarkerOptions().position(latLng).title("You")
                            .icon(icon)
                    )
                } else {
                    currentLocationMarker?.position = latLng
                }
            }
        }, mainLooper)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(geofenceEventReceiver)
    }
}
