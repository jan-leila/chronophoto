package com.chronophoto.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

data class CameraSettings(
    val exposureTimeNs: Long? = null,
    val iso: Int? = null,
    val focalLengthMm: Float? = null,
    val apertureFNumber: Float? = null,
    val whiteBalanceK: Int? = null,
    val whiteBalanceMode: String? = null,
    val lensFacing: Int? = null,
)

data class GpsLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("projectId", "sequence")],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val capturedAt: Long? = null,
    val sequence: Int,
    val filePath: String,
    val thumbnailPath: String? = null,
    @Embedded(prefix = "cam_") val cameraSettings: CameraSettings = CameraSettings(),
    val ambientLux: Float? = null,
    @Embedded(prefix = "gps_") val gpsLocation: GpsLocation = GpsLocation(),
)
