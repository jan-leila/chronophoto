package com.chronophoto.ui.projects.list

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

data class ProjectListUiState(
    val projects: List<Project> = emptyList(),
    val showArchived: Boolean = false,
)

class ProjectListViewModel(
    private val repo: ProjectRepository,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<ProjectListUiState> =
        combine(repo.observeAll(), showArchived) { all, archived ->
            ProjectListUiState(
                projects = if (archived) all else all.filter { it.archivedAt == null },
                showArchived = archived,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectListUiState(),
        )

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { ProjectListViewModel(ProjectRepository.from(context)) }
        }
    }
}
