package com.chronophoto.data.repo

import android.content.Context
import android.net.Uri
import com.chronophoto.data.capture.MetadataBundle
import com.chronophoto.data.capture.readCameraSettingsFromExif
import com.chronophoto.data.capture.readCapturedAtFromExif
import com.chronophoto.data.capture.readGpsFromExif
import com.chronophoto.data.db.AppDatabase
import com.chronophoto.data.db.dao.PhotoDao
import com.chronophoto.data.db.dao.ProjectDao
import com.chronophoto.data.db.entity.CameraSettings
import com.chronophoto.data.db.entity.GpsLocation
import com.chronophoto.data.db.entity.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class PhotoRepository(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val projectDao: ProjectDao,
    private val baseDir: File,
) {

    fun observeForProject(projectId: Long): Flow<List<Photo>> =
        photoDao.observeByProject(projectId)

    fun observeById(id: Long): Flow<Photo?> = photoDao.observeById(id)

    fun observePrevious(projectId: Long, sequence: Int): Flow<Photo?> =
        photoDao.observePrevious(projectId, sequence)

    suspend fun getById(id: Long): Photo? = photoDao.getById(id)

    suspend fun getLatest(projectId: Long): Photo? = photoDao.getLatest(projectId)

    fun observeLatest(projectId: Long): Flow<Photo?> = photoDao.observeLatest(projectId)

    suspend fun nextSequence(projectId: Long): Int = photoDao.maxSequence(projectId) + 1

    fun fileFor(projectId: Long, sequence: Int): File {
        val dir = File(baseDir, "projects/$projectId").apply { mkdirs() }
        return File(dir, "photo_${sequence.toString().padStart(5, '0')}.jpg")
    }

    suspend fun recordCapture(
        projectId: Long,
        sequence: Int,
        file: File,
        capturedAt: Long? = System.currentTimeMillis(),
        metadata: MetadataBundle = MetadataBundle(),
    ): Long {
        val project = projectDao.getById(projectId)
            ?: error("Project $projectId not found")
        return photoDao.insert(
            Photo(
                projectId = projectId,
                sequence = sequence,
                filePath = file.absolutePath,
                capturedAt = if (project.storeTimestamp) capturedAt else null,
                cameraSettings = if (project.storeCameraSettings) metadata.cameraSettings else CameraSettings(),
                ambientLux = if (project.storeAmbientBrightness) metadata.ambientLux else null,
                gpsLocation = if (project.storeGps) metadata.gpsLocation else GpsLocation(),
            ),
        )
    }

    suspend fun importFromUri(projectId: Long, source: Uri): Long = withContext(Dispatchers.IO) {
        val sequence = nextSequence(projectId)
        val target = fileFor(projectId, sequence)
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open $source")

        val capturedAt = readCapturedAtFromExif(target)
        val metadata = MetadataBundle(
            ambientLux = null,
            gpsLocation = readGpsFromExif(target),
            cameraSettings = readCameraSettingsFromExif(target),
        )
        recordCapture(
            projectId = projectId,
            sequence = sequence,
            file = target,
            capturedAt = capturedAt,
            metadata = metadata,
        )
    }

    suspend fun delete(photo: Photo) {
        runCatching { File(photo.filePath).delete() }
        photoDao.delete(photo)
    }

    companion object {
        fun from(context: Context): PhotoRepository {
            val app = context.applicationContext
            val db = AppDatabase.get(app)
            return PhotoRepository(
                context = app,
                photoDao = db.photoDao(),
                projectDao = db.projectDao(),
                baseDir = app.filesDir,
            )
        }
    }
}
