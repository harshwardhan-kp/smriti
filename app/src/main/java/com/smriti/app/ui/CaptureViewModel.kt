package com.smriti.app.ui

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.smriti.app.capture.CameraController
import com.smriti.app.capture.CapturePipeline
import com.smriti.app.capture.CaptureStage
import com.smriti.app.capture.PlatformAsr
import com.smriti.app.data.RecordDao
import com.smriti.app.data.SmritiDb
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()
    private val camera: CameraController = CameraController(app)
    private val asr: PlatformAsr = PlatformAsr(app)
    private val pipeline: CapturePipeline = CapturePipeline(app, dao, camera, asr)

    private val _stage = MutableStateFlow<CaptureStage?>(null)
    val stage: StateFlow<CaptureStage?> = _stage.asStateFlow()

    private val _lastRecordId = MutableStateFlow<Long?>(null)
    val lastRecordId: StateFlow<Long?> = _lastRecordId.asStateFlow()

    private var captureJob: Job? = null

    fun bindCamera(owner: LifecycleOwner, view: PreviewView) {
        camera.bind(owner, view)
    }

    fun capture(withVoice: Boolean) {
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            pipeline.run(withVoice).collect { currentStage ->
                _stage.value = currentStage
                if (currentStage is CaptureStage.Done) {
                    _lastRecordId.value = currentStage.recordId
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        camera.unbind()
    }
}