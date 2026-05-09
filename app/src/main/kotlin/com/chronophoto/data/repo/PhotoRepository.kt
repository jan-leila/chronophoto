package com.chronophoto.data.repo

import android.content.Context
import com.chronophoto.data.db.AppDatabase
import com.chronophoto.data.db.dao.PhotoDao
import com.chronophoto.data.db.dao.ProjectDao
import com.chronophoto.data.db.entity.Photo
import kotlinx.coroutines.flow.Flow
import java.io.File

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val projectDao: ProjectDao,
    private val baseDir: File,
) {

    fun observeForProject(projectId: Long): Flow<List<Photo>> =
        photoDao.observeByProject(projectId)

    suspend fun getLatest(projectId: Long): Photo? = photoDao.getLatest(projectId)

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
    ): Long {
        val project = projectDao.getById(projectId)
            ?: error("Project $projectId not found")
        val timestamp = if (project.storeTimestamp) capturedAt else null
        return photoDao.insert(
            Photo(
                projectId = projectId,
                sequence = sequence,
                filePath = file.absolutePath,
                capturedAt = timestamp,
            ),
        )
    }

    suspend fun delete(photo: Photo) {
        runCatching { File(photo.filePath).delete() }
        photoDao.delete(photo)
    }

    companion object {
        fun from(context: Context): PhotoRepository {
            val db = AppDatabase.get(context)
            return PhotoRepository(
                photoDao = db.photoDao(),
                projectDao = db.projectDao(),
                baseDir = context.filesDir,
            )
        }
    }
}
