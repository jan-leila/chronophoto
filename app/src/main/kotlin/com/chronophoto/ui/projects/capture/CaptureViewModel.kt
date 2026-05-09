package com.chronophoto.ui.projects.capture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronophoto.data.repo.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CaptureUiState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

data class CaptureTarget(val sequence: Int, val file: File)

class CaptureViewModel(
    private val projectId: Long,
    private val repo: PhotoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    suspend fun prepareCapture(): CaptureTarget {
        val seq = repo.nextSequence(projectId)
        val file = repo.fileFor(projectId, seq)
        return CaptureTarget(seq, file)
    }

    fun recordCapture(target: CaptureTarget) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.recordCapture(projectId, target.sequence, target.file) }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.message ?: "Save failed") }
                }
        }
    }

    fun onCaptureError(message: String?) {
        _state.update { it.copy(saving = false, error = message ?: "Capture failed") }
    }

    companion object {
        fun factory(context: Context, projectId: Long) = viewModelFactory {
            initializer { CaptureViewModel(projectId, PhotoRepository.from(context)) }
        }
    }
}
