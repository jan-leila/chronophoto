package com.chronophoto.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val archivedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val reminderMinuteOfDay: Int? = null,
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val storeTimestamp: Boolean = true,
    @ColumnInfo(defaultValue = "1") val storeCameraSettings: Boolean = true,
    @ColumnInfo(defaultValue = "1") val storeAmbientBrightness: Boolean = true,
    @ColumnInfo(defaultValue = "0") val storeGps: Boolean = false,
    val referencePhotoId: Long? = null,
)
