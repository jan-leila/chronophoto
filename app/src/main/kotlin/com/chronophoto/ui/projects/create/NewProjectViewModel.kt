package com.chronophoto.ui.projects.create

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

data class NewProjectFormState(
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
    val saved: Long? = null,
) {
    val canSave: Boolean get() = !saving && name.trim().isNotEmpty()
}

class NewProjectViewModel(
    private val repo: ProjectRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(NewProjectFormState())
    val state: StateFlow<NewProjectFormState> = _state.asStateFlow()

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
            val id = repo.create(
                name = current.name,
                description = current.description.ifBlank { null },
                storeTimestamp = current.storeTimestamp,
                storeCameraSettings = current.storeCameraSettings,
                storeAmbientBrightness = current.storeAmbientBrightness,
                storeGps = current.storeGps,
                reminderIntervalAmount = if (current.reminderEnabled) current.reminderIntervalAmount else null,
                reminderIntervalUnit = if (current.reminderEnabled) current.reminderIntervalUnit?.name else null,
                reminderAnchorMillis = if (current.reminderEnabled) current.reminderAnchorMillis else null,
                reminderRefireMinutes = if (current.reminderEnabled) current.reminderRefireMinutes else null,
            )
            if (current.reminderEnabled) {
                val project = repo.getById(id)
                if (project != null) ReminderScheduler.schedule(appContext, project)
            }
            _state.update { it.copy(saving = false, saved = id) }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                NewProjectViewModel(
                    repo = ProjectRepository.from(context),
                    appContext = context.applicationContext,
                )
            }
        }
    }
}
