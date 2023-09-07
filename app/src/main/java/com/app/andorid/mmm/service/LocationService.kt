package com.app.andorid.mmm.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import org.greenrobot.eventbus.EventBus
import com.app.andorid.mmm.room.AppDatabase
import com.app.andorid.mmm.room.MarkedPlace
import com.app.andorid.mmm.room.MarkedPlaceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class LocationService : Service() {
    companion object {
        const val CHANNEL_ID = "12345"
        const val NOTIFICATION_ID = 12345
    }

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null
    private var notificationManager: NotificationManager? = null
    private var location: Location? = null
    private var markedPlaceDao: MarkedPlaceDao? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    override fun onDestroy() {
        super.onDestroy()
        removeLocationUpdates()
       // serviceScope.cancel()
    }
    @SuppressLint("MissingPermission")
    private fun createLocationRequest() {
        try {
            fusedLocationProviderClient?.requestLocationUpdates(
                locationRequest!!, locationCallback!!, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun removeLocationUpdates() {
        locationCallback?.let {
            fusedLocationProviderClient?.removeLocationUpdates(it)
        }
        stopForeground(true)
        stopSelf()
    }

    private fun onNewLocation(locationResult: LocationResult) {
        location = locationResult.lastLocation
        EventBus.getDefault().post(
            LocationEvent(
                latitude = location?.latitude,
                longitude = location?.longitude
            )
        )
        startForeground(NOTIFICATION_ID, getNotification())

        location?.let { saveLocationToDatabase(LatLng(it.latitude, it.longitude)) }
    }
    override fun onCreate() {
        super.onCreate()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000).setIntervalMillis(60000)
                .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(p0: LocationAvailability) {
                super.onLocationAvailability(p0)
            }

            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult)
            }
        }
        notificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(CHANNEL_ID, "locations", NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(notificationChannel)
        }
        val appDatabase = AppDatabase.getDatabase(applicationContext)
        markedPlaceDao = appDatabase.markedPlaceDao()

    }


    @SuppressLint("SuspiciousIndentation")
    fun getNotification(): Notification {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Updates")
            .setContentText(
                "Latitude--> ${location?.latitude}\nLongitude --> ${location?.longitude}"
            )
        //    .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId(CHANNEL_ID)
        }
        return notification.build()
    }

    fun saveLocationToDatabase(latLng: LatLng) {
        val markedPlace = MarkedPlace(
            latitude = latLng.latitude,
            longitude = latLng.longitude
        )
        GlobalScope.launch(Dispatchers.IO) {
            markedPlaceDao?.insertMarkedPlace(markedPlace)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createLocationRequest()
        return START_STICKY
    }
    override fun onBind(intent: Intent): IBinder? = null
}
