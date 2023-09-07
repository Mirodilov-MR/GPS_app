package com.app.andorid.mmm

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.app.andorid.mmm.databinding.ActivityMainBinding
import com.app.andorid.mmm.room.AppDatabase
import com.app.andorid.mmm.room.MarkedPlaceDao
import com.app.andorid.mmm.service.LocationEvent
import com.app.andorid.mmm.service.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private var service: Intent? = null
    private var isFirstLocationReceived = false
    private var firstLocationLatLng: LatLng? = null
    private var lastLocationLatLng: LatLng? = null
    private val lineOptions = PolylineOptions().width(5f).color(Color.BLUE)
    private var currentMarker: Marker? = null
    private var markedPlaceDao: MarkedPlaceDao? = null
    private var locationService: LocationService? = null
    private val backgroundLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
            }
        }
    private val locationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                it.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (ActivityCompat.checkSelfPermission(
                                this,
                                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            backgroundLocation.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }

                it.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) -> {

                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationService = LocationService()
         binding.btnStop.isEnabled = false
        service = Intent(this, LocationService::class.java)
        val appDatabase = AppDatabase.getDatabase(applicationContext)
        markedPlaceDao = appDatabase.markedPlaceDao()
        binding.apply {
            binding.btnStart.setOnClickListener {
                checkPermissions()
                if (firstLocationLatLng != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(firstLocationLatLng!!, 15f))
                    map.clear()
                   // startService()
                    placeMarker(
                        firstLocationLatLng,
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                } else {
                    Toast.makeText(this@MainActivity, "First location not available", Toast.LENGTH_SHORT).show()
                }
            }
            btnStop.setOnClickListener {
                logSavedLocations()
               // binding.btnStop.isEnabled = false
                binding.btnStart.isEnabled = true
                placeMarker(
                    lastLocationLatLng,
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
                stopService(service)
                if (EventBus.getDefault().isRegistered(this)) {
                    EventBus.getDefault().unregister(this)
                }
            }
            btnClear.setOnClickListener {
                stopService(service)
                clearMapLines()
                clearDatabase()
                btnStop.isEnabled = false
                Toast.makeText(this@MainActivity, "Map and Database cleared", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        locationService = LocationService()
        GlobalScope.launch(Dispatchers.IO) {
            val savedLocations = getSavedLocations()
            runOnUiThread {
                drawSavedLocationMarkers(savedLocations)
            }
        }
    }

    private fun logSavedLocations() {
        GlobalScope.launch(Dispatchers.IO) {
            val markedPlaces = markedPlaceDao?.getAllMarkedPlaces()
            markedPlaces?.forEachIndexed { index, markedPlace ->
                val locationInfo =
                    "Location $index - Latitude: ${markedPlace.latitude}, Longitude: ${markedPlace.longitude}"
                Log.d("SavedLocations", locationInfo)
            }
        }
    }

    private fun drawSavedLocationMarkers(savedLocations: List<LatLng>) {
        savedLocations.forEachIndexed { index, latLng ->
            if (index == 0) {
                placeMarker(
                    latLng,
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            } else if (index == savedLocations.size - 1) {
//                placeMarker(
//                    latLng,
//                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
//                )
            }

            if (index > 0) {
                val previousLatLng = savedLocations[index - 1]
                lineOptions.add(previousLatLng, latLng)
            }
        }
        map.addPolyline(lineOptions)
    }


    private suspend fun getSavedLocations(): List<LatLng> {
        val markedPlaces = markedPlaceDao?.getAllMarkedPlaces()
        val locations = mutableListOf<LatLng>()
        markedPlaces?.forEach { markedPlace ->
            val latLng = LatLng(markedPlace.latitude, markedPlace.longitude)
            locations.add(latLng)
        }
        return locations
    }

    private fun startService() {
        binding.btnStop.isEnabled = true
        val intent = Intent(this, LocationService::class.java)
        intent.action = "START"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun clearMapLines() {
        map.clear()
        lineOptions.points.clear()
        isFirstLocationReceived = false
        firstLocationLatLng = null
        lastLocationLatLng = null
    }

    private fun clearDatabase() {
        GlobalScope.launch(Dispatchers.IO) {
            markedPlaceDao!!.deleteAllMarkedPlaces()

        }
    }

    private fun placeMarker(latLng: LatLng?, icon: BitmapDescriptor) {
        if (latLng != null) {
            // currentMarker?.remove()
            val currentTime =
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(currentTime)
                .icon(icon)
            currentMarker = map.addMarker(markerOptions)
            binding.btnStop.isEnabled = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val default = LatLng(41.31119328501219, 69.27997241419695)
        map.moveCamera(CameraUpdateFactory.newLatLng(default))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(default, 10f))
    }

    fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissions.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                startService(service)
            }
        }
    }

    private fun saveLocationToDatabase(latLng: LatLng) {
        locationService?.saveLocationToDatabase(latLng)
    }

    @Subscribe
    fun receiveLocationEvent(locationEvent: LocationEvent) {
        Log.d(
            "LocationService",
            "Received location event: Latitude=${locationEvent.latitude}, Longitude=${locationEvent.longitude}"
        )
        val latLng = LatLng(locationEvent.latitude ?: 0.0, locationEvent.longitude ?: 0.0)
        val currentTime =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val markerOptions = MarkerOptions()
            .position(latLng)
            .title(currentTime)
        if (!isFirstLocationReceived) {
            isFirstLocationReceived = true
            firstLocationLatLng = latLng
        } else {
            lastLocationLatLng = latLng
        }
        saveLocationToDatabase(latLng)
      //  map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        if (isFirstLocationReceived && lastLocationLatLng != null) {
            lineOptions.add(latLng, lastLocationLatLng)
            map.addPolyline(lineOptions)
        }
    }
//    override fun onDestroy() {
//        super.onDestroy()
//        stopService(service)
//        if (EventBus.getDefault().isRegistered(this)) {
//            EventBus.getDefault().unregister(this)
//        }
//    }
}