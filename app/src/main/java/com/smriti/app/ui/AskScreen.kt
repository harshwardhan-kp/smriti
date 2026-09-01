package com.smriti.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Embedder
import com.smriti.app.ai.Recall
import com.smriti.app.ai.RecallAnswer
import com.smriti.app.capture.PlatformAsr
import com.smriti.app.data.Converters
import com.smriti.app.data.RecordDao
import com.smriti.app.data.SmritiDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorCardBg = Color(0xFF181818)

class AskViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()
    private val asr: PlatformAsr = PlatformAsr(app)
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
                    Text(
                        text = "Ask Smriti",
                        color = ColorCream,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ColorCream
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorInk)
            )
        },
        containerColor = ColorInk
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "What did I commit to this week?",
                        color = ColorCream.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { vm.listenVoice { query = it } }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = ColorAmber
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ColorCream,
                    unfocusedTextColor = ColorCream,
                    focusedBorderColor = ColorAmber,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = ColorAmber
                ),
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { vm.ask(query) },
                enabled = !busy && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAmber,
                    contentColor = ColorInk,
                    disabledContainerColor = ColorAmber.copy(alpha = 0.3f),
                    disabledContentColor = ColorInk.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "ASK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (busy) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = ColorAmber,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Thinking on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorAmber,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            answer?.let { ans ->
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorCardBg),
                    border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ans.answer,
                            style = MaterialTheme.typography.bodyLarge,
                            color = ColorCream,
                            lineHeight = 24.sp
                        )
                    }
                }

                if (ans.evidencePhotoPath != null) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Evidence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorAmber
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ans.evidenceRecordId?.let { onOpenRecord(it) }
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (evidenceBitmap != null) {
                                Image(
                                    bitmap = evidenceBitmap,
                                    contentDescription = "Evidence Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to view full record",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorCream.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}