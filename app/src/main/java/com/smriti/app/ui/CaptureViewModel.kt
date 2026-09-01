package com.smriti.app.ui

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.smriti.app.capture.AsrFactory
import com.smriti.app.capture.CameraController
import com.smriti.app.capture.CapturePipeline
import com.smriti.app.capture.CaptureStage
import com.smriti.app.capture.PushToTalk
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
    private val asr = AsrFactory.create(app)
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

    fun stopVoice() {
        (asr as? PushToTalk)?.stopListening()
    }

    /**
     * Clears a terminal stage after the UI has acted on it.
     *
     * `stage` is state, but "a record was saved" is an EVENT. Leaving `Done` parked in the
     * StateFlow meant that every time CaptureScreen recomposed — including when the user
     * pressed back to return to it — `LaunchedEffect(stage)` saw `Done` again and navigated
     * straight back to the detail screen. From the outside that looks exactly like a broken
     * back button: the press registers, and is instantly undone.
     *
     * Reported from device testing, 2026-09-01.
     */
    fun consumeTerminalStage() {
        val current = _stage.value
        if (current is CaptureStage.Done || current is CaptureStage.Failed) {
            _stage.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        camera.unbind()
    }
}
