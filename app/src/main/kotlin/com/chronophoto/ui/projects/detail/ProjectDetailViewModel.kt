package com.chronophoto.ui.projects.detail

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: Project? = null,
    val photos: List<Photo> = emptyList(),
    val deleted: Boolean = false,
    val importing: Boolean = false,
    val importError: String? = null,
)

class ProjectDetailViewModel(
    private val projectId: Long,
    private val projectRepo: ProjectRepository,
    private val photoRepo: PhotoRepository,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)
    private val importStatus = MutableStateFlow(ImportStatus())

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        projectRepo.observeById(projectId),
        photoRepo.observeForProject(projectId),
        deleted,
        importStatus,
    ) { project, photos, isDeleted, status ->
        ProjectDetailUiState(
            project = project,
            photos = photos,
            deleted = isDeleted,
            importing = status.running,
            importError = status.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectDetailUiState(),
    )

    private data class ImportStatus(val running: Boolean = false, val error: String? = null)

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

    fun importPhoto(uri: Uri) {
        if (importStatus.value.running) return
        importStatus.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            runCatching { photoRepo.importFromUri(projectId, uri) }
                .onSuccess { importStatus.update { ImportStatus() } }
                .onFailure { e -> importStatus.update { ImportStatus(error = e.message ?: "Import failed") } }
        }
    }

    fun clearImportError() {
        importStatus.update { it.copy(error = null) }
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
