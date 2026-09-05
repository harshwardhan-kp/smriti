package com.smriti.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.SmritiDb
import com.smriti.app.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()

    val records: StateFlow<List<RecordEntity>> = dao.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun startSelection(id: Long) {
        _selection.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selection.value
        _selection.value = if (id in current) {
            current - id
        } else {
            current + id
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _selection.value.toList()
            if (ids.isEmpty()) return@launch
            val paths = dao.photoPathsFor(ids)
            dao.deleteTasksForRecords(ids)
            dao.deleteRecords(ids)
            clearSelection()
            withContext(Dispatchers.IO) {
                for (path in paths) {
                    if (path.isNotBlank()) {
                        runCatching { File(path).delete() }
                    }
                }
            }
        }
    }

    fun toggleTask(id: Long, done: Boolean) {
        viewModelScope.launch {
            dao.setTaskDone(id, done)
        }
    }
}