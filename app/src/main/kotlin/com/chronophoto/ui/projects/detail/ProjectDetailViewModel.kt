package com.chronophoto.ui.projects.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronophoto.data.db.entity.Project
import com.chronophoto.data.repo.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: Project? = null,
    val deleted: Boolean = false,
)

class ProjectDetailViewModel(
    private val projectId: Long,
    private val repo: ProjectRepository,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<ProjectDetailUiState> =
        combine(repo.observeById(projectId), deleted) { project, isDeleted ->
            ProjectDetailUiState(project = project, deleted = isDeleted)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectDetailUiState(),
        )

    fun archive() {
        viewModelScope.launch { repo.archive(projectId) }
    }

    fun unarchive() {
        viewModelScope.launch { repo.unarchive(projectId) }
    }

    fun delete() {
        viewModelScope.launch {
            uiState.value.project?.let { repo.delete(it) }
            deleted.value = true
        }
    }

    companion object {
        fun factory(context: Context, projectId: Long) = viewModelFactory {
            initializer {
                ProjectDetailViewModel(projectId, ProjectRepository.from(context))
            }
        }
    }
}
