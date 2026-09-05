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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smriti.app.ai.Enricher
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.SmritiDb
import com.smriti.app.data.TaskEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorCardBg = Color(0xFF181818)
private val ColorChipBg = Color(0xFF222222)

data class TaskDraft(val id: Long = 0L, val text: String, val done: Boolean)

private data class MemoryDraft(
    val title: String,
    val summary: String,
    val people: List<String>,
    val tags: List<String>,
    val amounts: List<String>,
    val tasks: List<TaskDraft>,
    val deletedTasks: List<TaskDraft> = emptyList()
)

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()

    private val _record = MutableStateFlow<RecordEntity?>(null)
    val record: StateFlow<RecordEntity?> = _record.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    private var recordJob: Job? = null
    private var tasksJob: Job? = null

    fun load(recordId: Long) {
        _record.value = null
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            dao.observeRecord(recordId).collect {
                _record.value = it
            }
        }
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch {
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

    fun save(
        recordId: Long,
        title: String,
        summary: String,
        people: List<String>,
        tags: List<String>,
        amounts: List<String>,
        tasks: List<TaskDraft>
    ) {
        viewModelScope.launch {
            val trimmedTitle = title.trim()
            val trimmedSummary = summary.trim()
            val cleanPeople = people.map { it.trim() }.filter { it.isNotEmpty() }
            val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
            val cleanAmounts = amounts.map { it.trim() }.filter { it.isNotEmpty() }

            val peopleJson = Gson().toJson(cleanPeople)
            val tagsJson = Gson().toJson(cleanTags)
            val amountsJson = Gson().toJson(cleanAmounts)

            val record = _record.value ?: dao.getRecord(recordId)
            val transcript = record?.transcript ?: ""

            val embedding = Enricher.embedFor(
                context = getApplication(),
                title = trimmedTitle,
                summary = trimmedSummary,
                ocrText = "",
                transcript = transcript
            )

            val at = System.currentTimeMillis()
            dao.applyUserEdit(
                id = recordId,
                title = trimmedTitle,
                summary = trimmedSummary,
                people = peopleJson,
                amounts = amountsJson,
                tags = tagsJson,
                embedding = embedding,
                at = at
            )

            val currentTasks = _tasks.value
            for (task in tasks) {
                val trimmedText = task.text.trim()
                if (task.id == 0L) {
                    if (trimmedText.isNotEmpty()) {
                        dao.insertTask(
                            TaskEntity(
                                recordId = recordId,
                                text = trimmedText,
                                done = task.done
                            )
                        )
                    }
                } else {
                    if (trimmedText.isEmpty()) {
                        dao.deleteTask(task.id)
                    } else {
                        val existingTask = currentTasks.firstOrNull { it.id == task.id }
                        dao.updateTask(task.id, trimmedText, existingTask?.dueDateMillis)
                        dao.setTaskDone(task.id, task.done)
                    }
                }
            }
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

private val editorTextFieldColors
    @Composable
    get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = ColorCream,
        unfocusedTextColor = ColorCream,
        focusedBorderColor = ColorAmber,
        unfocusedBorderColor = ColorCream.copy(alpha = 0.3f),
        cursorColor = ColorAmber,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedPlaceholderColor = ColorCream.copy(alpha = 0.4f),
        unfocusedPlaceholderColor = ColorCream.copy(alpha = 0.4f)
    )

private enum class ChipStyle { PERSON, TAG, AMOUNT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListChipEditor(
    title: String,
    items: List<String>,
    placeholder: String,
    style: ChipStyle,
    onAddItem: (String) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val onAdd = {
        val trimmed = inputText.trim()
        if (trimmed.isNotEmpty() && !items.contains(trimmed)) {
            onAddItem(trimmed)
        }
        inputText = ""
    }

    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = ColorAmber
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (items.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { index, item ->
                when (style) {
                    ChipStyle.PERSON -> {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = ColorChipBg,
                            border = BorderStroke(1.dp, ColorCream.copy(alpha = 0.25f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "@ $item",
                                    color = ColorCream,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clickable { onRemoveItem(index) }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $item",
                                        tint = ColorCream.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    ChipStyle.TAG -> {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = ColorAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "# $item",
                                    color = ColorAmber,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clickable { onRemoveItem(index) }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $item",
                                        tint = ColorAmber.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    ChipStyle.AMOUNT -> {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = ColorCardBg,
                            border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "• ",
                                    color = ColorAmber,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = item,
                                    color = ColorCream,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clickable { onRemoveItem(index) }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $item",
                                        tint = ColorAmber.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        colors = editorTextFieldColors,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = placeholder,
                    tint = ColorAmber
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAdd() })
    )
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

    var isEditing by remember(recordId) { mutableStateOf(false) }
    var draft by remember(recordId) { mutableStateOf<MemoryDraft?>(null) }

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
                    IconButton(onClick = {
                        if (isEditing) {
                            isEditing = false
                            draft = null
                        }
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ColorCream
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            isEditing = false
                            draft = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Discard changes",
                                tint = ColorCream
                            )
                        }
                        IconButton(onClick = {
                            val currentDraft = draft
                            val currentRecord = record
                            if (currentDraft != null && currentRecord != null) {
                                vm.save(
                                    recordId = currentRecord.id,
                                    title = currentDraft.title,
                                    summary = currentDraft.summary,
                                    people = currentDraft.people,
                                    tags = currentDraft.tags,
                                    amounts = currentDraft.amounts,
                                    tasks = currentDraft.tasks + currentDraft.deletedTasks
                                )
                            }
                            isEditing = false
                            draft = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save",
                                tint = ColorAmber
                            )
                        }
                    } else {
                        val currentRecord = record
                        if (currentRecord != null) {
                            IconButton(onClick = {
                                draft = MemoryDraft(
                                    title = currentRecord.title,
                                    summary = currentRecord.summary,
                                    people = parseStringList(currentRecord.peopleJson),
                                    tags = parseStringList(currentRecord.tagsJson),
                                    amounts = parseStringList(currentRecord.amountsJson),
                                    tasks = tasks.map { TaskDraft(id = it.id, text = it.text, done = it.done) }
                                )
                                isEditing = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = ColorCream
                                )
                            }
                        }
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

            val currentDraft = draft

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

                if (isEditing && currentDraft != null) {
                    OutlinedTextField(
                        value = currentDraft.title,
                        onValueChange = { draft = currentDraft.copy(title = it) },
                        placeholder = { Text("Title") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        colors = editorTextFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currentDraft.summary,
                        onValueChange = { draft = currentDraft.copy(summary = it) },
                        placeholder = { Text("Summary") },
                        singleLine = false,
                        minLines = 3,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        colors = editorTextFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ListChipEditor(
                        title = "People",
                        items = currentDraft.people,
                        placeholder = "Add person",
                        style = ChipStyle.PERSON,
                        onAddItem = { item ->
                            draft = currentDraft.copy(people = currentDraft.people + item)
                        },
                        onRemoveItem = { index ->
                            draft = currentDraft.copy(
                                people = currentDraft.people.filterIndexed { i, _ -> i != index }
                            )
                        }
                    )

                    ListChipEditor(
                        title = "Tags",
                        items = currentDraft.tags,
                        placeholder = "Add tag",
                        style = ChipStyle.TAG,
                        onAddItem = { item ->
                            draft = currentDraft.copy(tags = currentDraft.tags + item)
                        },
                        onRemoveItem = { index ->
                            draft = currentDraft.copy(
                                tags = currentDraft.tags.filterIndexed { i, _ -> i != index }
                            )
                        }
                    )

                    ListChipEditor(
                        title = "Amounts",
                        items = currentDraft.amounts,
                        placeholder = "Add amount",
                        style = ChipStyle.AMOUNT,
                        onAddItem = { item ->
                            draft = currentDraft.copy(amounts = currentDraft.amounts + item)
                        },
                        onRemoveItem = { index ->
                            draft = currentDraft.copy(
                                amounts = currentDraft.amounts.filterIndexed { i, _ -> i != index }
                            )
                        }
                    )

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
                            val newTaskFocusRequester = remember { FocusRequester() }
                            var shouldFocusNewTask by remember { mutableStateOf(false) }

                            LaunchedEffect(shouldFocusNewTask) {
                                if (shouldFocusNewTask) {
                                    delay(50)
                                    try {
                                        newTaskFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                    shouldFocusNewTask = false
                                }
                            }

                            currentDraft.tasks.forEachIndexed { index, task ->
                                val isLastItem = index == currentDraft.tasks.lastIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.done,
                                        onCheckedChange = { done ->
                                            val updated = currentDraft.tasks.toMutableList()
                                            updated[index] = task.copy(done = done)
                                            draft = currentDraft.copy(tasks = updated)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ColorAmber,
                                            checkmarkColor = ColorInk,
                                            uncheckedColor = ColorCream.copy(alpha = 0.6f)
                                        )
                                    )
                                    OutlinedTextField(
                                        value = task.text,
                                        onValueChange = { newText ->
                                            val updated = currentDraft.tasks.toMutableList()
                                            updated[index] = task.copy(text = newText)
                                            draft = currentDraft.copy(tasks = updated)
                                        },
                                        placeholder = { Text("Task") },
                                        singleLine = true,
                                        colors = editorTextFieldColors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .then(
                                                if (isLastItem && shouldFocusNewTask) {
                                                    Modifier.focusRequester(newTaskFocusRequester)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                    IconButton(
                                        onClick = {
                                            val removed = currentDraft.tasks[index]
                                            val updated = currentDraft.tasks.filterIndexed { i, _ -> i != index }
                                            val deleted = if (removed.id != 0L) {
                                                currentDraft.deletedTasks + removed.copy(text = "")
                                            } else {
                                                currentDraft.deletedTasks
                                            }
                                            draft = currentDraft.copy(tasks = updated, deletedTasks = deleted)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete task",
                                            tint = ColorCream.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    draft = currentDraft.copy(
                                        tasks = currentDraft.tasks + TaskDraft(id = 0L, text = "", done = false)
                                    )
                                    shouldFocusNewTask = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = ColorAmber)
                            ) {
                                Text(
                                    text = "＋ Add task",
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorAmber
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = currentRecord.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorCream
                    )

                    when (currentRecord.enrichmentState) {
                        "PENDING", "RUNNING" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThinkingDots()
                                Text(
                                    text = if (currentRecord.enrichmentState == "RUNNING") "Understanding…" else "Queued",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ColorCream.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ShimmerLine(widthFraction = 0.85f)
                            Spacer(modifier = Modifier.height(6.dp))
                            ShimmerLine(widthFraction = 0.55f)
                        }
                        "FAILED" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Could not extract",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorCream.copy(alpha = 0.6f)
                            )
                        }
                        else -> {
                            if (currentRecord.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currentRecord.summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ColorCream.copy(alpha = 0.9f),
                                    lineHeight = 22.sp
                                )
                            }
                        }
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