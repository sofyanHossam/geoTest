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
import android.widget.Button
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
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var btn: FloatingActionButton
    private val markersMap = mutableMapOf<String, Marker>()
    private val circlesMap = mutableMapOf<String, Circle>()
    private var mMap: GoogleMap? = null
    private var geofencingClient: GeofencingClient? = null
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocationMarker: Marker? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val GEOFENCE_RADIUS = 10f
        private const val FINE_LOCATION_ACCESS_REQUEST_CODE = 10001
        private const val BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002
    }

    // 27 نقاط على خط مستقيم تقريبا
    val points: List<LatLng> = listOf(
        LatLng(29.066848597159858, 31.098161616413687), // Tahrir Square
        LatLng(29.066201404626188, 31.098449333059524),
        LatLng(29.065369293953434, 31.09615606212457),
        LatLng(29.064670315793272, 31.092766929150166),
        LatLng(29.063804907693108, 31.089631663787507),
        LatLng(29.061556292028982, 31.092001094990156),
        LatLng(29.059266943784177, 31.09436206401036),
        LatLng(29.060010340297357, 31.09708267883072),
        LatLng(29.06123452886125, 31.099650973141397),
        LatLng(29.06288031836529, 31.103107804148625),
        LatLng(29.065114111993502, 31.106293843055592),
        LatLng(29.06818734826611, 31.11091846516768),
        LatLng(29.069522383863273, 31.11268707640256),
        LatLng(29.072528924238295, 31.114679937362336),
        LatLng(29.07389348743074, 31.11549231381209),
        LatLng(29.0730466468343, 31.11230627504449),
        LatLng(29.071822598541896, 31.108447486948062),
        LatLng(29.071445396432132, 31.105092202961952),
        LatLng(29.070946156235387, 31.099955614403314),
        LatLng(29.070772346118236, 31.098656658268542),
        LatLng(29.070480296046885, 31.09870619153342),
        LatLng(29.069327228409918, 31.099114856204928),
        LatLng(29.069137596998864, 31.09849769849689),
        LatLng(29.068595610600077, 31.098625571008224),
        LatLng(29.06848392307514, 31.098409652291263),
        LatLng(29.06834093885015, 31.09767549116876),
        LatLng(29.06815456204961, 31.097600389315613)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btn = findViewById(R.id.fab_my_location)

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
        btn.setOnClickListener {
            mMap?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    currentLocationMarker?.position ?: startPoint, 18f
                )
            )
        }
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
            PolylineOptions().addAll(decoded).color(Color.BLUE).width(16f)
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
