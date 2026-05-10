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

    suspend fun getById(id: Long): Project? = projectDao.getById(id)

    suspend fun create(
        name: String,
        description: String?,
        storeTimestamp: Boolean,
        storeCameraSettings: Boolean,
        storeAmbientBrightness: Boolean,
        storeGps: Boolean,
        reminderIntervalAmount: Int? = null,
        reminderIntervalUnit: String? = null,
        reminderAnchorMillis: Long? = null,
        reminderRefireMinutes: Int? = null,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val reminderActive = reminderIntervalAmount != null &&
            reminderIntervalUnit != null &&
            reminderAnchorMillis != null
        val project = Project(
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            storeTimestamp = storeTimestamp,
            storeCameraSettings = storeCameraSettings,
            storeAmbientBrightness = storeAmbientBrightness,
            storeGps = storeGps,
            reminderEnabled = reminderActive,
            reminderIntervalAmount = if (reminderActive) reminderIntervalAmount else null,
            reminderIntervalUnit = if (reminderActive) reminderIntervalUnit else null,
            reminderAnchorMillis = if (reminderActive) reminderAnchorMillis else null,
            reminderRefireMinutes = if (reminderActive) reminderRefireMinutes else null,
        )
        return projectDao.insert(project)
    }

    suspend fun setReminder(
        projectId: Long,
        intervalAmount: Int?,
        intervalUnit: String?,
        anchorMillis: Long?,
        refireMinutes: Int?,
    ): Project? {
        val current = projectDao.getById(projectId) ?: return null
        val active = intervalAmount != null && intervalUnit != null && anchorMillis != null
        val updated = current.copy(
            reminderEnabled = active,
            reminderIntervalAmount = if (active) intervalAmount else null,
            reminderIntervalUnit = if (active) intervalUnit else null,
            reminderAnchorMillis = if (active) anchorMillis else null,
            reminderRefireMinutes = if (active) refireMinutes else null,
            reminderActiveFireMillis = if (!active) null else current.reminderActiveFireMillis,
        )
        projectDao.update(updated)
        return updated
    }

    suspend fun updateProjectFields(
        projectId: Long,
        name: String,
        description: String?,
        storeTimestamp: Boolean,
        storeCameraSettings: Boolean,
        storeAmbientBrightness: Boolean,
        storeGps: Boolean,
    ): Project? {
        val current = projectDao.getById(projectId) ?: return null
        val updated = current.copy(
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            storeTimestamp = storeTimestamp,
            storeCameraSettings = storeCameraSettings,
            storeAmbientBrightness = storeAmbientBrightness,
            storeGps = storeGps,
        )
        projectDao.update(updated)
        return updated
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
