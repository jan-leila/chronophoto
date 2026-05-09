package com.chronophoto.data.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.chronophoto.data.db.entity.CameraSettings
import com.chronophoto.data.db.entity.GpsLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MetadataBundle(
    val ambientLux: Float? = null,
    val gpsLocation: GpsLocation = GpsLocation(),
    val cameraSettings: CameraSettings = CameraSettings(),
)

class MetadataCollector(
    private val context: Context,
    private val collectAmbientLight: Boolean,
    private val collectLocation: Boolean,
    private val collectCameraSettings: Boolean,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _luxFlow = MutableStateFlow<Float?>(null)
    val luxFlow: StateFlow<Float?> = _luxFlow.asStateFlow()

    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow.asStateFlow()

    private var started = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            _luxFlow.value = event.values.firstOrNull()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _locationFlow.value = location
        }
        override fun onProviderDisabled(provider: String) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        if (collectAmbientLight) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sensor ->
                sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        if (collectLocation && hasFineLocation()) {
            val providers = locationManager?.getProviders(true).orEmpty()
            val provider = when {
                LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                runCatching {
                    locationManager?.requestLocationUpdates(provider, 1_000L, 0f, locationListener)
                    locationManager?.getLastKnownLocation(provider)?.let { _locationFlow.value = it }
                }
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { sensorManager?.unregisterListener(sensorListener) }
        runCatching { locationManager?.removeUpdates(locationListener) }
    }

    fun snapshot(jpegFile: File, lensFacing: Int?): MetadataBundle {
        val camera = if (collectCameraSettings) {
            readCameraSettingsFromExif(jpegFile).copy(lensFacing = lensFacing)
        } else {
            CameraSettings()
        }
        val location = _locationFlow.value
        val gps = if (collectLocation) location?.let {
            GpsLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                altitudeM = if (it.hasAltitude()) it.altitude else null,
                accuracyM = if (it.hasAccuracy()) it.accuracy else null,
            )
        } ?: GpsLocation() else GpsLocation()
        return MetadataBundle(
            ambientLux = if (collectAmbientLight) _luxFlow.value else null,
            gpsLocation = gps,
            cameraSettings = camera,
        )
    }

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}
