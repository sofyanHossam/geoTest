package com.example.geofinence

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var btn: FloatingActionButton
    private val markersMap = mutableMapOf<String, Marker>()
    private val circlesMap = mutableMapOf<String, Circle>()
    private var mMap: GoogleMap? = null
    private var geofencingClient: GeofencingClient? = null
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocationMarker: Marker? = null

    private var currentGeofenceIndex = 0

    // Map لتخزين الصور الأصلية لكل StudentPoint
    private val studentBitmaps = mutableMapOf<String, Bitmap>()

    companion object {
        private const val TAG = "MainActivity"
        private const val GEOFENCE_RADIUS = 30f
        private const val FINE_LOCATION_ACCESS_REQUEST_CODE = 10001
        private const val BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002
        private const val defaultMarkerUrl =
            "https://cdn-icons-png.flaticon.com/512/149/149071.png"
    }

    private val studentPoints = listOf(
        StudentPoint(
            LatLng(30.108185601722635, 31.372857385097245),
            "https://randomuser.me/api/portraits/men/1.jpg",
            "AHMED"
        ),
        StudentPoint(
            LatLng(30.108394430648445, 31.37225657055705),
            "https://randomuser.me/api/portraits/women/2.jpg",
            "SARA"
        ),
        StudentPoint(
            LatLng(30.108744798319538, 31.37137948826812),
            "https://randomuser.me/api/portraits/men/3.jpg",
            "ZIAD"
        ),
        StudentPoint(
            LatLng(30.108276077694168, 31.37160751017212),
            "https://randomuser.me/api/portraits/women/4.jpg",
            "MARYAM"
        ),
        StudentPoint(
            LatLng(30.107816669335467, 31.37240945643499),
            "https://randomuser.me/api/portraits/men/5.jpg",
            "ABDELRAHMAN"
        ),
        StudentPoint(
            LatLng(30.107308514997197, 31.37191056554164),
            "https://randomuser.me/api/portraits/women/6.jpg",
            "SALMA"
        )
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val loc = locationResult.lastLocation ?: return
            val latLng = LatLng(loc.latitude, loc.longitude)

            if (currentLocationMarker == null) {
                val icon = BitmapDescriptorFactory.fromBitmap(
                    BitmapFactory.decodeResource(resources, R.drawable.img).scale(80, 80, false)
                )
                currentLocationMarker = mMap?.addMarker(
                    MarkerOptions().position(latLng).title("You").icon(icon)
                )
            } else {
                currentLocationMarker?.let { moveMarkerSmooth(it, latLng, 1000L) }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btn = findViewById(R.id.fab_my_location)
        geofencingClient = LocationServices.getGeofencingClient(this)
        geofenceHelper = GeofenceHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(geofenceEventReceiver, IntentFilter("GEOFENCE_EVENT"))


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
        val bitmap = studentBitmaps[geofenceId] ?: return

        marker?.let {
            when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> it.setIcon(
                    getMarkerBitmap(
                        bitmap,
                        Color.GREEN
                    )
                )

                Geofence.GEOFENCE_TRANSITION_DWELL -> it.setIcon(
                    getMarkerBitmap(
                        bitmap,
                        Color.YELLOW
                    )
                )

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    it.setIcon(getMarkerBitmap(bitmap, Color.RED))
                    Handler(mainLooper).postDelayed({
                        // إزالة Marker و Circle الحالي
                        it.remove()
                        circle?.remove()
                        markersMap.remove(geofenceId)
                        circlesMap.remove(geofenceId)
                        geofencingClient?.removeGeofences(listOf(geofenceId))

                        // إضافة النقطة التالية
                        currentGeofenceIndex++
                        if (currentGeofenceIndex < studentPoints.size) {
                            addNextGeofence(studentPoints[currentGeofenceIndex])
                        }
                    }, 1000)
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val startPoint = studentPoints.first().latLng
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 16f))
        btn.setOnClickListener {
            mMap?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    currentLocationMarker?.position ?: startPoint,
                    18f
                )
            )
        }
        enableUserLocation()
        drawRoutePolyline()
        startLocationUpdates()
        checkGpsOn()
        // تحميل كل الصور مرة واحدة عند البداية
        CoroutineScope(Dispatchers.IO).launch {
            studentPoints.forEach { student ->
                try {
                    val input = URL(student.imageUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(input)
                    studentBitmaps[student.geofenceId] = bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // بعد تحميل الصور، إضافة الماركرز على الخريطة
            withContext(Dispatchers.Main) {
                addNextGeofence(studentPoints.first())
            }
        }
    }

    private fun addNextGeofence(student: StudentPoint) {
        val bitmap = studentBitmaps[student.geofenceId]
        val icon = bitmap?.let { getMarkerBitmap(it, Color.GRAY) }

        val marker = mMap?.addMarker(
            MarkerOptions().position(student.latLng).title(student.geofenceId).icon(icon)
        )
        markersMap[student.geofenceId] = marker!!

        val circle = mMap?.addCircle(
            CircleOptions().center(student.latLng).radius(GEOFENCE_RADIUS.toDouble())
                .strokeColor(Color.argb(255, 255, 0, 0))
                .fillColor(Color.argb(64, 255, 0, 0))
                .strokeWidth(4f)
        )
        circlesMap[student.geofenceId] = circle!!

        addGeofence(student.latLng, student.geofenceId)
    }

    private fun addAllPoints() {
        studentPoints.forEach { student ->
            val bitmap = studentBitmaps[student.geofenceId]
            val icon = bitmap?.let { getMarkerBitmap(it, Color.GRAY) }

            val marker = mMap?.addMarker(
                MarkerOptions().position(student.latLng).title(student.geofenceId).icon(icon)
            )
            markersMap[student.geofenceId] = marker!!

            val circle = mMap?.addCircle(
                CircleOptions().center(student.latLng).radius(GEOFENCE_RADIUS.toDouble())
                    .strokeColor(Color.argb(255, 255, 0, 0)).fillColor(Color.argb(64, 255, 0, 0))
                    .strokeWidth(4f)
            )
            circlesMap[student.geofenceId] = circle!!

            addGeofence(student.latLng, student.geofenceId)
        }
    }

    private fun getMarkerBitmap(
        bitmap: Bitmap,
        borderColor: Int,
        borderWidth: Float = 6f
    ): BitmapDescriptor {
        val size = 150
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, false)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        val radius = size / 2f

        // عمل clip لدائرة للصورة فقط
        val path = android.graphics.Path().apply {
            addCircle(radius, radius, radius - borderWidth, android.graphics.Path.Direction.CCW)
        }
        canvas.save()            // حفظ حالة الكانفاس قبل الـ clip
        canvas.clipPath(path)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        canvas.restore()         // رجوع للوضع الطبيعي عشان نرسم الـ border

        // ارسم الـ border
        paint.apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = borderWidth
        }
        canvas.drawCircle(radius, radius, radius - borderWidth / 2, paint)

        return BitmapDescriptorFactory.fromBitmap(output)
    }


    @SuppressLint("MissingPermission")
    private fun addGeofence(latLng: LatLng, id: String) {
        val geofence = geofenceHelper.getGeofence(
            id, latLng, GEOFENCE_RADIUS,
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT
        )
        val geofencingRequest: GeofencingRequest = geofenceHelper.getGeofencingRequest(geofence)
        val pendingIntent: PendingIntent = geofenceHelper.geofencePendingIntent

        geofencingClient?.addGeofences(geofencingRequest, pendingIntent)
            ?.addOnSuccessListener { Log.d(TAG, "Geofence $id added") }
            ?.addOnFailureListener { e ->
                Log.d(
                    TAG,
                    "Geofence add failed: ${geofenceHelper.getErrorString(e)}"
                )
            }
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap?.isMyLocationEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_ACCESS_REQUEST_CODE
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_LOCATION_ACCESS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FINE_LOCATION_ACCESS_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()
        }
    }

    private fun checkGpsOn() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnFailureListener { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }

    private fun drawRoutePolyline() {
        if (studentPoints.size < 2) return
        val firstHalf = studentPoints.subList(0, studentPoints.size / 2)
        val secondHalf = studentPoints.subList((studentPoints.size / 2) - 1, studentPoints.size)

        CoroutineScope(Dispatchers.IO).launch {
            val polyline1 = getPolylineFromGoogle(firstHalf.map { it.latLng })
            val polyline2 = getPolylineFromGoogle(secondHalf.map { it.latLng })
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
                    "&destination=${destination.latitude},${destination.longitude}&waypoints=$waypoints&key=$apiKey"
        return try {
            val response = URL(url).readText()
            val points = JSONObject(response).getJSONArray("routes").getJSONObject(0)
                .getJSONObject("overview_polyline").getString("points")
            val decoded = decodePoly(points)
            PolylineOptions().addAll(decoded).color(Color.BLUE).width(16f)
        } catch (e: Exception) {
            Log.e(TAG, "Polyline fetch error: ${e.message}")
            null
        }
    }

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

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(1f).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(geofenceEventReceiver)
        stopLocationUpdates()
    }

    private fun moveMarkerSmooth(marker: Marker, toPosition: LatLng, duration: Long = 1000L) {
        val startPosition = marker.position
        val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
        valueAnimator.duration = duration
        valueAnimator.addUpdateListener { animation ->
            val v = animation.animatedFraction
            val lat = startPosition.latitude + (toPosition.latitude - startPosition.latitude) * v
            val lng = startPosition.longitude + (toPosition.longitude - startPosition.longitude) * v
            marker.position = LatLng(lat, lng)
        }
        valueAnimator.start()
    }

}

data class StudentPoint(val latLng: LatLng, val imageUrl: String, val geofenceId: String)
