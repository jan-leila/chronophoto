package com.chronophoto.ui.photo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.data.db.entity.Project
import com.chronophoto.data.repo.PhotoRepository
import com.chronophoto.data.repo.ProjectRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhotoDetailUiState(
    val photo: Photo? = null,
    val previous: Photo? = null,
    val project: Project? = null,
    val loaded: Boolean = false,
    val deleted: Boolean = false,
)

class PhotoDetailViewModel(
    private val photoId: Long,
    private val photos: PhotoRepository,
    private val projects: ProjectRepository,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PhotoDetailUiState> = photos.observeById(photoId)
        .flatMapLatest { photo ->
            if (photo == null) {
                flowOf(PhotoDetailUiState(loaded = true))
            } else {
                combine(
                    photos.observePrevious(photo.projectId, photo.sequence),
                    projects.observeById(photo.projectId),
                    deleted,
                ) { prev, project, isDeleted ->
                    PhotoDetailUiState(
                        photo = photo,
                        previous = prev,
                        project = project,
                        loaded = true,
                        deleted = isDeleted,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PhotoDetailUiState(),
        )

    fun delete() {
        val photo = uiState.value.photo ?: return
        viewModelScope.launch {
            photos.delete(photo)
            deleted.value = true
        }
    }

    companion object {
        fun factory(context: Context, photoId: Long) = viewModelFactory {
            initializer {
                PhotoDetailViewModel(
                    photoId = photoId,
                    photos = PhotoRepository.from(context),
                    projects = ProjectRepository.from(context),
                )
            }
        }
    }
}
