package com.chronophoto.ui.projects.edit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronophoto.data.repo.ProjectRepository
import com.chronophoto.reminder.ReminderScheduler
import com.chronophoto.ui.components.DurationUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectEditState(
    val loaded: Boolean = false,
    val name: String = "",
    val description: String = "",
    val storeTimestamp: Boolean = true,
    val storeCameraSettings: Boolean = true,
    val storeAmbientBrightness: Boolean = true,
    val storeGps: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderIntervalAmount: Int? = null,
    val reminderIntervalUnit: DurationUnit? = null,
    val reminderAnchorMillis: Long? = null,
    val reminderRefireMinutes: Int? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = loaded && !saving && name.trim().isNotEmpty()
}

class ProjectEditViewModel(
    private val projectId: Long,
    private val repo: ProjectRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectEditState())
    val state: StateFlow<ProjectEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = repo.getById(projectId) ?: return@launch
            val unit = p.reminderIntervalUnit?.let { runCatching { DurationUnit.valueOf(it) }.getOrNull() }
            _state.update {
                it.copy(
                    loaded = true,
                    name = p.name,
                    description = p.description.orEmpty(),
                    storeTimestamp = p.storeTimestamp,
                    storeCameraSettings = p.storeCameraSettings,
                    storeAmbientBrightness = p.storeAmbientBrightness,
                    storeGps = p.storeGps,
                    reminderEnabled = p.reminderEnabled,
                    reminderIntervalAmount = p.reminderIntervalAmount,
                    reminderIntervalUnit = unit,
                    reminderAnchorMillis = p.reminderAnchorMillis,
                    reminderRefireMinutes = p.reminderRefireMinutes,
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }
    fun setStoreTimestamp(value: Boolean) = _state.update { it.copy(storeTimestamp = value) }
    fun setStoreCameraSettings(value: Boolean) = _state.update { it.copy(storeCameraSettings = value) }
    fun setStoreAmbientBrightness(value: Boolean) = _state.update { it.copy(storeAmbientBrightness = value) }
    fun setStoreGps(value: Boolean) = _state.update { it.copy(storeGps = value) }

    fun setReminder(
        enabled: Boolean,
        amount: Int?,
        unit: DurationUnit?,
        anchor: Long?,
        refire: Int?,
    ) = _state.update {
        it.copy(
            reminderEnabled = enabled,
            reminderIntervalAmount = amount,
            reminderIntervalUnit = unit,
            reminderAnchorMillis = anchor,
            reminderRefireMinutes = refire,
        )
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.updateProjectFields(
                projectId = projectId,
                name = current.name,
                description = current.description.ifBlank { null },
                storeTimestamp = current.storeTimestamp,
                storeCameraSettings = current.storeCameraSettings,
                storeAmbientBrightness = current.storeAmbientBrightness,
                storeGps = current.storeGps,
            )
            val updated = repo.setReminder(
                projectId = projectId,
                intervalAmount = if (current.reminderEnabled) current.reminderIntervalAmount else null,
                intervalUnit = if (current.reminderEnabled) current.reminderIntervalUnit?.name else null,
                anchorMillis = if (current.reminderEnabled) current.reminderAnchorMillis else null,
                refireMinutes = if (current.reminderEnabled) current.reminderRefireMinutes else null,
            )
            if (updated != null) {
                if (updated.reminderIntervalAmount != null && updated.reminderIntervalUnit != null && updated.reminderAnchorMillis != null) {
                    ReminderScheduler.schedule(appContext, updated)
                } else {
                    ReminderScheduler.cancel(appContext, projectId)
                }
            }
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        fun factory(context: Context, projectId: Long) = viewModelFactory {
            initializer {
                ProjectEditViewModel(
                    projectId = projectId,
                    repo = ProjectRepository.from(context),
                    appContext = context.applicationContext,
                )
            }
        }
    }
}
