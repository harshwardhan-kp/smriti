FILE: app/src/main/java/com/smriti/app/ui/CaptureViewModel.kt
```kotlin
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
```
FILE: app/src/main/java/com/smriti/app/ui/CaptureScreen.kt
```kotlin
package com.smriti.app.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smriti.app.capture.CaptureStage

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorRedAlert = Color(0xFFE53935)

@Composable
fun CaptureScreen(
    onOpenTimeline: () -> Unit,
    onOpenAsk: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    vm: CaptureViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage by vm.stage.collectAsState()
    var isLongPressed by remember { mutableStateOf(false) }

    LaunchedEffect(stage) {
        val currentStage = stage
        if (currentStage is CaptureStage.Done) {
            onRecordSaved(currentStage.recordId)
        }
    }

    val isListening = isLongPressed || stage is CaptureStage.Listening

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    vm.bindCamera(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenAsk) {
                Text(
                    text = "Ask",
                    color = ColorCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = ColorInk.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "OFFLINE · ON-DEVICE",
                    color = ColorAmber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            TextButton(onClick = onOpenTimeline) {
                Text(
                    text = "Timeline",
                    color = ColorCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        stage?.let { currentStage ->
            val statusText = when (currentStage) {
                is CaptureStage.Photo -> "Capturing"
                is CaptureStage.Reading -> "Reading the image"
                is CaptureStage.Listening -> "Listening"
                is CaptureStage.Thinking -> "Understanding, on this phone"
                is CaptureStage.Done -> "Saved"
                is CaptureStage.Failed -> currentStage.reason
            }
            val isFailed = currentStage is CaptureStage.Failed

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
                shape = RoundedCornerShape(12.dp),
                color = ColorInk.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, if (isFailed) ColorRedAlert else ColorAmber.copy(alpha = 0.5f))
            ) {
                Text(
                    text = statusText,
                    color = if (isFailed) ColorRedAlert else ColorCream,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isListening) {
                    "LISTENING — release to stop"
                } else {
                    "Tap for a photo · Hold to add your voice"
                },
                color = if (isListening) ColorRedAlert else ColorCream,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .background(ColorInk.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .then(
                        if (isListening) {
                            Modifier.border(4.dp, ColorRedAlert, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(if (isListening) 6.dp else 0.dp)
                    .background(ColorAmber, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isLongPressed = false
                                }
                            },
                            onTap = {
                                vm.capture(false)
                            },
                            onLongPress = {
                                isLongPressed = true
                                vm.capture(true)
                            }
                        )
                    }
            )
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/ui/TimelineViewModel.kt
```kotlin
package com.smriti.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.SmritiDb
import com.smriti.app.data.TaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()

    val records: StateFlow<List<RecordEntity>> = dao.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTask(id: Long, done: Boolean) {
        viewModelScope.launch {
            dao.setTaskDone(id, done)
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/ui/TimelineScreen.kt
```kotlin
package com.smriti.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorRedAlert = Color(0xFFE53935)
private val ColorCardBg = Color(0xFF181818)

private fun formatRelativeTime(createdAt: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - createdAt
    if (diff < 0L) return "just now"
    val seconds = diff / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        seconds < 60L -> "just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60L -> "$minutes minutes ago"
        hours == 1L -> "1 hour ago"
        hours < 24L -> "$hours hours ago"
        days == 1L -> "yesterday"
        days < 30L -> "$days days ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(createdAt))
        }
    }
}

private fun formatDueDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun parseTags(tagsJson: String): List<String> {
    if (tagsJson.isBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson<List<String>>(tagsJson, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    vm: TimelineViewModel = viewModel()
) {
    val records by vm.records.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val openTasks = remember(tasks) { tasks.filter { !it.done } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Timeline",
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
        if (records.isEmpty() && openTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing captured yet. Point the camera at something and talk.",
                    color = ColorCream.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                if (openTasks.isNotEmpty()) {
                    stickyHeader {
                        Surface(
                            color = ColorInk,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Open tasks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorAmber,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }

                    items(openTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { done -> vm.toggleTask(task.id, done) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (records.isNotEmpty()) {
                    stickyHeader {
                        Surface(
                            color = ColorInk,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Timeline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorAmber,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }

                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            onClick = { onOpenRecord(record.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(ColorCardBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = ColorAmber,
                checkmarkColor = ColorInk,
                uncheckedColor = ColorCream.copy(alpha = 0.6f)
            )
        )

        Text(
            text = task.text,
            style = MaterialTheme.typography.bodyMedium,
            color = ColorCream,
            modifier = Modifier.weight(1f)
        )

        task.dueDateMillis?.let { dueMillis ->
            val isOverdue = dueMillis < System.currentTimeMillis()
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = if (isOverdue) ColorRedAlert.copy(alpha = 0.2f) else ColorAmber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (isOverdue) ColorRedAlert else ColorAmber.copy(alpha = 0.5f)),
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            ) {
                Text(
                    text = formatDueDate(dueMillis),
                    color = if (isOverdue) ColorRedAlert else ColorAmber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordCard(
    record: RecordEntity,
    onClick: () -> Unit
) {
    val thumbnailBitmap: ImageBitmap? = remember(record.photoPath) {
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4
            }
            BitmapFactory.decodeFile(record.photoPath, options)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    val tags = remember(record.tagsJson) {
        parseTags(record.tagsJson)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2B2B2B))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.title.ifBlank { "Untitled Record" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorCream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formatRelativeTime(record.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorCream.copy(alpha = 0.55f)
                    )
                }

                if (record.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorCream.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = ColorAmber.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = tag,
                                    color = ColorAmber,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/ui/DetailScreen.kt
```kotlin
package com.smriti.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.SmritiDb
import com.smriti.app.data.TaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorCardBg = Color(0xFF181818)
private val ColorChipBg = Color(0xFF222222)

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()

    private val _record = MutableStateFlow<RecordEntity?>(null)
    val record: StateFlow<RecordEntity?> = _record.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    fun load(recordId: Long) {
        viewModelScope.launch {
            _record.value = dao.getRecord(recordId)
        }
        viewModelScope.launch {
            dao.observeTasksForRecord(recordId).collect {
                _tasks.value = it
            }
        }
    }

    fun toggleTask(id: Long, done: Boolean) {
        viewModelScope.launch {
            dao.setTaskDone(id, done)
        }
    }
}

private fun parseStringList(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson<List<String>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    recordId: Long,
    onBack: () -> Unit
) {
    val vm: DetailViewModel = viewModel()

    LaunchedEffect(recordId) {
        vm.load(recordId)
    }

    val record by vm.record.collectAsState()
    val tasks by vm.tasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Memory Detail",
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
        val currentRecord = record
        if (currentRecord == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ColorAmber)
            }
        } else {
            val photoBitmap: ImageBitmap? = remember(currentRecord.photoPath) {
                try {
                    BitmapFactory.decodeFile(currentRecord.photoPath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            val people = remember(currentRecord.peopleJson) { parseStringList(currentRecord.peopleJson) }
            val tags = remember(currentRecord.tagsJson) { parseStringList(currentRecord.tagsJson) }
            val amounts = remember(currentRecord.amountsJson) { parseStringList(currentRecord.amountsJson) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = "Captured Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = currentRecord.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorCream
                )

                if (currentRecord.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentRecord.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorCream.copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )
                }

                if (people.isNotEmpty() || tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        people.forEach { person ->
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = ColorChipBg,
                                border = BorderStroke(1.dp, ColorCream.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "@ $person",
                                    color = ColorCream,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = ColorAmber.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "# $tag",
                                    color = ColorAmber,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (amounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Amounts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            amounts.forEach { amount ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "•",
                                        color = ColorAmber,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = amount,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ColorCream,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (tasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            tasks.forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.done,
                                        onCheckedChange = { done -> vm.toggleTask(task.id, done) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ColorAmber,
                                            checkmarkColor = ColorInk,
                                            uncheckedColor = ColorCream.copy(alpha = 0.6f)
                                        )
                                    )
                                    Text(
                                        text = task.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (task.done) ColorCream.copy(alpha = 0.45f) else ColorCream
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF262626))
                Spacer(modifier = Modifier.height(16.dp))

                CollapsibleRawSection(
                    title = "What the camera read",
                    content = currentRecord.ocrText
                )

                CollapsibleRawSection(
                    title = "What you said",
                    content = currentRecord.transcript
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CollapsibleRawSection(
    title: String,
    content: String
) {
    if (content.isBlank()) return
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        border = BorderStroke(1.dp, Color(0xFF282828)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ColorCream.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = ColorCream.copy(alpha = 0.5f)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF282828))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = ColorCream.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/ui/AskScreen.kt
```kotlin
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
import com.smriti.app.ai.Embedder
import com.smriti.app.ai.LlmHolder
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
        val engine = LlmHolder.get(context).getOrNull() ?: return@withContext null
        val embedder = Embedder.create(context).getOrNull() ?: return@withContext null
        val newRecall = Recall(dao, engine, embedder, converters)
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
                val transcript = asr.listen()
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
                            BitmapFactory.decodeFile(ans.evidencePhotoPath)?.asImageBitmap()
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
```
