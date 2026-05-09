package com.chronophoto.data.capture

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.chronophoto.data.db.entity.CameraSettings
import com.chronophoto.data.db.entity.GpsLocation
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val TAG = "ExifReader"

private val EXIF_DATETIME = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getDefault()
}

fun readCameraSettingsFromExif(file: File): CameraSettings = file.openExif()
    ?.let(::readCameraSettings)
    ?: CameraSettings()

fun readCameraSettingsFromExif(input: InputStream): CameraSettings =
    runCatching { readCameraSettings(ExifInterface(input)) }.getOrElse {
        Log.w(TAG, "EXIF read failed", it)
        CameraSettings()
    }

fun readGpsFromExif(file: File): GpsLocation = file.openExif()
    ?.let(::readGps)
    ?: GpsLocation()

fun readCapturedAtFromExif(file: File): Long? = file.openExif()?.let(::readCapturedAt)

private fun File.openExif(): ExifInterface? = runCatching {
    ExifInterface(absolutePath)
}.getOrElse {
    Log.w(TAG, "EXIF open failed for $name", it)
    null
}

private fun readCameraSettings(exif: ExifInterface): CameraSettings {
    val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 }
        ?: @Suppress("DEPRECATION")
        exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, -1).takeIf { it > 0 }
    return CameraSettings(
        exposureTimeNs = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)
            .takeIf { it > 0 }
            ?.let { (it * 1_000_000_000).toLong() },
        iso = iso,
        focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)
            .takeIf { it > 0 }
            ?.toFloat(),
        apertureFNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0)
            .takeIf { it > 0 }
            ?.toFloat(),
        whiteBalanceMode = when (exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)) {
            "0" -> "auto"
            "1" -> "manual"
            else -> null
        },
    )
}

private fun readGps(exif: ExifInterface): GpsLocation {
    val latLong = exif.latLong ?: return GpsLocation()
    val altitude = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
    return GpsLocation(
        latitude = latLong[0],
        longitude = latLong[1],
        altitudeM = altitude,
        accuracyM = null,
    )
}

private fun readCapturedAt(exif: ExifInterface): Long? {
    val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        ?: return null
    return runCatching { EXIF_DATETIME.parse(raw)?.time }.getOrNull()
}
