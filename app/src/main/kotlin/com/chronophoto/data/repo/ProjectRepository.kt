package com.chronophoto.data.repo

import android.content.Context
import com.chronophoto.data.db.AppDatabase
import com.chronophoto.data.db.dao.ProjectDao
import com.chronophoto.data.db.entity.Project
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    fun observeActive(): Flow<List<Project>> = projectDao.observeActive()

    fun observeAll(): Flow<List<Project>> = projectDao.observeAll()

    fun observeById(id: Long): Flow<Project?> = projectDao.observeById(id)

    suspend fun create(
        name: String,
        description: String?,
        storeTimestamp: Boolean,
        storeCameraSettings: Boolean,
        storeAmbientBrightness: Boolean,
        storeGps: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val project = Project(
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            storeTimestamp = storeTimestamp,
            storeCameraSettings = storeCameraSettings,
            storeAmbientBrightness = storeAmbientBrightness,
            storeGps = storeGps,
        )
        return projectDao.insert(project)
    }

    suspend fun update(project: Project) = projectDao.update(project)

    suspend fun archive(id: Long, now: Long = System.currentTimeMillis()) =
        projectDao.setArchived(id, now)

    suspend fun unarchive(id: Long) = projectDao.setArchived(id, null)

    suspend fun delete(project: Project) = projectDao.delete(project)

    companion object {
        fun from(context: Context): ProjectRepository =
            ProjectRepository(AppDatabase.get(context).projectDao())
    }
}
