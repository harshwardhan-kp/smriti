package com.smriti.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Embedder
import com.smriti.app.ai.Recall
import com.smriti.app.ai.RecallAnswer
import com.smriti.app.capture.Asr
import com.smriti.app.capture.AsrFactory
import com.smriti.app.data.Converters
import com.smriti.app.data.RecordDao
import com.smriti.app.data.SmritiDb
import com.smriti.app.ui.components.BracketLabelLive
import com.smriti.app.ui.components.DisplayHeading
import com.smriti.app.ui.components.HairlineRule
import com.smriti.app.ui.components.SectionLabel
import com.smriti.app.ui.components.SmritiButton
import com.smriti.app.ui.theme.S
import com.smriti.app.ui.theme.SmritiType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AskViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()
    // Flavor-selected: PlatformAsr offline, Groq Whisper in devcloud.
    private val asr: Asr = AsrFactory.create(app)
    private val converters: Converters = Converters()

    private val _answer = MutableStateFlow<RecallAnswer?>(null)
    val answer: StateFlow<RecallAnswer?> = _answer.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var recall: Recall? = null

    private suspend fun getOrCreateRecall(): Recall? = withContext(Dispatchers.IO) {
        recall?.let { return@withContext it }
        val context = getApplication<Application>()
        val backend = BackendFactory.create(context).getOrNull() ?: return@withContext null
        val embedder = Embedder.create(context).getOrNull()
        val newRecall = Recall(dao, backend, embedder, converters)
        recall = newRecall
        newRecall
    }

    fun ask(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val recallInstance = getOrCreateRecall()
                if (recallInstance != null) {
                    val result = withContext(Dispatchers.IO) {
                        recallInstance.ask(q)
                    }
                    _answer.value = result
                } else {
                    _answer.value = RecallAnswer(
                        answer = "Unable to initialize on-device AI engine.",
                        evidenceRecordId = null,
                        evidencePhotoPath = null,
                        usedRecordIds = emptyList()
                    )
                }
            } catch (e: Exception) {
                _answer.value = RecallAnswer(
                    answer = "Failed to recall: ${e.message ?: "Unknown error"}",
                    evidenceRecordId = null,
                    evidencePhotoPath = null,
                    usedRecordIds = emptyList()
                )
            } finally {
                _busy.value = false
            }
        }
    }

    fun listenVoice(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val transcript = asr.transcribe()
                if (transcript.isNotBlank()) {
                    onResult(transcript)
                }
            } catch (e: Exception) {
                // Ignore errors during voice input
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    vm: AskViewModel = viewModel()
) {
    var query by remember { mutableStateOf("") }
    val answer by vm.answer.collectAsState()
    val busy by vm.busy.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    DisplayHeading(
                        text = "ask",
                        italicWord = "ask",
                        style = SmritiType.DisplaySmall,
                        color = S.Ink
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = S.Ink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = S.Paper,
                    titleContentColor = S.Ink,
                    navigationIconContentColor = S.Ink
                )
            )
        },
        containerColor = S.Paper
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = S.gutter)
        ) {
            Spacer(modifier = Modifier.height(S.gutter))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "what did i commit to this week?",
                        style = SmritiType.Body,
                        color = S.MutedSoft
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { vm.listenVoice { query = it } }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = S.Muted
                        )
                    }
                },
                shape = S.r6,
                textStyle = SmritiType.Body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = S.Ink,
                    unfocusedTextColor = S.Ink,
                    focusedBorderColor = S.Red,
                    unfocusedBorderColor = S.Hairline,
                    cursorColor = S.Red,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(S.md))

            SmritiButton(
                label = "ask",
                onClick = { vm.ask(query) },
                primary = true,
                enabled = !busy && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            if (busy) {
                Spacer(modifier = Modifier.height(S.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(S.sm)
                ) {
                    ThinkingDots()
                    BracketLabelLive("thinking")
                }
            }

            answer?.let { ans ->
                Spacer(modifier = Modifier.height(S.lg))
                HairlineRule()
                Spacer(modifier = Modifier.height(S.lg))

                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("answer")
                    Spacer(modifier = Modifier.height(S.sm))
                    Text(
                        text = ans.answer,
                        style = SmritiType.Body,
                        color = S.Ink
                    )
                }

                if (ans.evidencePhotoPath != null) {
                    Spacer(modifier = Modifier.height(S.lg))
                    SectionLabel("evidence")
                    Spacer(modifier = Modifier.height(S.sm))

                    val evidenceBitmap: ImageBitmap? = remember(ans.evidencePhotoPath) {
                        try {
                            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(ans.evidencePhotoPath, boundsOpts)
                            val sampleSize = run {
                                val maxDim = 1024
                                var s = 1
                                val w = boundsOpts.outWidth
                                val h = boundsOpts.outHeight
                                while ((w / s) > maxDim || (h / s) > maxDim) {
                                    s *= 2
                                }
                                s
                            }
                            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            BitmapFactory.decodeFile(ans.evidencePhotoPath, decodeOpts)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ans.evidenceRecordId?.let { onOpenRecord(it) }
                            }
                            .padding(vertical = S.gutter)
                    ) {
                        if (evidenceBitmap != null) {
                            Image(
                                bitmap = evidenceBitmap,
                                contentDescription = "Evidence Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .clip(S.r6)
                            )
                            Spacer(modifier = Modifier.height(S.sm))
                        }
                        Text(
                            text = "tap to view full record",
                            style = SmritiType.BodySmall,
                            color = S.Muted
                        )
                        Spacer(modifier = Modifier.height(S.gutter))
                        HairlineRule()
                    }
                }
            }

            Spacer(modifier = Modifier.height(S.section))
        }
    }
}