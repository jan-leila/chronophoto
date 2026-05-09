package com.chronophoto.ui.projects.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.data.db.entity.Project
import com.chronophoto.data.repo.PhotoRepository
import com.chronophoto.data.repo.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: Project? = null,
    val photos: List<Photo> = emptyList(),
    val deleted: Boolean = false,
)

class ProjectDetailViewModel(
    private val projectId: Long,
    private val projectRepo: ProjectRepository,
    private val photoRepo: PhotoRepository,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        projectRepo.observeById(projectId),
        photoRepo.observeForProject(projectId),
        deleted,
    ) { project, photos, isDeleted ->
        ProjectDetailUiState(project = project, photos = photos, deleted = isDeleted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectDetailUiState(),
    )

    fun archive() {
        viewModelScope.launch { projectRepo.archive(projectId) }
    }

    fun unarchive() {
        viewModelScope.launch { projectRepo.unarchive(projectId) }
    }

    fun delete() {
        viewModelScope.launch {
            uiState.value.project?.let { projectRepo.delete(it) }
            deleted.value = true
        }
    }

    companion object {
        fun factory(context: Context, projectId: Long) = viewModelFactory {
            initializer {
                ProjectDetailViewModel(
                    projectId = projectId,
                    projectRepo = ProjectRepository.from(context),
                    photoRepo = PhotoRepository.from(context),
                )
            }
        }
    }
}
