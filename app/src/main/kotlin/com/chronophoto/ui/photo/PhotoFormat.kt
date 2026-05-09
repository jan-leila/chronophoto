package com.chronophoto.ui.photo

import android.hardware.camera2.CameraMetadata
import android.location.Location
import com.chronophoto.data.db.entity.GpsLocation
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

internal object PhotoFormat {

    fun date(epochMs: Long?): String =
        if (epochMs == null) "—"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

    fun iso(value: Int?): String = value?.toString() ?: "—"

    fun isoDiff(now: Int?, prev: Int?): String? {
        if (now == null || prev == null || now == prev) return null
        val delta = now - prev
        return "${signed(delta)} from previous"
    }

    fun exposure(ns: Long?): String {
        val seconds = ns?.let { it / 1_000_000_000.0 } ?: return "—"
        return when {
            seconds >= 1.0 -> "%.2fs".format(Locale.US, seconds)
            seconds <= 0.0 -> "—"
            else -> "1/${(1.0 / seconds).roundToInt()}s"
        }
    }

    fun exposureDiff(now: Long?, prev: Long?): String? {
        if (now == null || prev == null || now == prev || prev <= 0L) return null
        val stops = ln(now.toDouble() / prev.toDouble()) / ln(2.0)
        if (abs(stops) < 0.05) return null
        return "%+.1f stops vs previous".format(Locale.US, stops)
    }

    fun focalLength(mm: Float?): String = mm?.let { "%.1f mm".format(Locale.US, it) } ?: "—"

    fun focalLengthDiff(now: Float?, prev: Float?): String? {
        if (now == null || prev == null || abs(now - prev) < 0.05f) return null
        return "%+.1f mm from previous".format(Locale.US, now - prev)
    }

    fun aperture(f: Float?): String = f?.let { "f/%.1f".format(Locale.US, it) } ?: "—"

    fun whiteBalance(mode: String?): String = mode ?: "—"

    fun whiteBalanceDiff(now: String?, prev: String?): String? {
        if (now == null || prev == null || now == prev) return null
        return "was $prev"
    }

    fun lensFacing(value: Int?): String = when (value) {
        CameraMetadata.LENS_FACING_FRONT -> "front"
        CameraMetadata.LENS_FACING_BACK -> "back"
        CameraMetadata.LENS_FACING_EXTERNAL -> "external"
        null -> "—"
        else -> "unknown"
    }

    fun lux(value: Float?): String = value?.let { "%.1f lux".format(Locale.US, it) } ?: "—"

    fun luxDiff(now: Float?, prev: Float?): String? {
        if (now == null || prev == null || abs(now - prev) < 0.5f) return null
        return "%+.1f lux from previous".format(Locale.US, now - prev)
    }

    fun gpsLatLng(loc: GpsLocation): String {
        val lat = loc.latitude
        val lng = loc.longitude
        if (lat == null || lng == null) return "—"
        return "%.6f, %.6f".format(Locale.US, lat, lng)
    }

    fun gpsDiff(now: GpsLocation, prev: GpsLocation): String? {
        val nLat = now.latitude ?: return null
        val nLng = now.longitude ?: return null
        val pLat = prev.latitude ?: return null
        val pLng = prev.longitude ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(pLat, pLng, nLat, nLng, out)
        val meters = out[0]
        if (meters < 1f) return null
        return if (meters >= 1_000f) "%.2f km from previous".format(Locale.US, meters / 1_000f)
        else "%.0f m from previous".format(Locale.US, meters)
    }

    fun altitude(m: Double?): String = m?.let { "%.1f m".format(Locale.US, it) } ?: "—"

    fun accuracy(m: Float?): String = m?.let { "±%.0f m".format(Locale.US, it) } ?: "—"

    private fun signed(value: Int): String = if (value >= 0) "+$value" else "$value"
}
