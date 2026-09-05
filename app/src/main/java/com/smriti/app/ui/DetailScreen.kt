package com.smriti.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import com.smriti.app.ui.components.BracketLabel
import com.smriti.app.ui.components.ChipKind
import com.smriti.app.ui.components.DashedRule
import com.smriti.app.ui.components.DisplayHeading
import com.smriti.app.ui.components.HairlineRule
import com.smriti.app.ui.components.NodeSquare
import com.smriti.app.ui.components.SectionLabel
import com.smriti.app.ui.components.SmritiChip
import com.smriti.app.ui.components.SmritiOutlineButton
import com.smriti.app.ui.theme.S
import com.smriti.app.ui.theme.SmritiType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

private val editorTextFieldColors
    @Composable
    get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = S.Ink,
        unfocusedTextColor = S.Ink,
        focusedBorderColor = S.Red,
        unfocusedBorderColor = S.Hairline,
        cursorColor = S.Red,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListChipEditor(
    title: String,
    items: List<String>,
    placeholder: String,
    kind: ChipKind,
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

    Spacer(modifier = Modifier.height(S.lg))
    SectionLabel(title.lowercase())
    Spacer(modifier = Modifier.height(S.sm))

    if (items.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(S.sm),
            verticalArrangement = Arrangement.spacedBy(S.sm)
        ) {
            items.forEachIndexed { index, item ->
                SmritiChip(
                    text = item,
                    kind = kind,
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove $item",
                            modifier = Modifier
                                .size(12.dp)
                                .clickable { onRemoveItem(index) },
                            tint = S.MutedSoft
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(S.sm))
    }

    OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        placeholder = {
            Text(
                text = placeholder.lowercase(),
                style = SmritiType.Body,
                color = S.MutedSoft
            )
        },
        singleLine = true,
        textStyle = SmritiType.Body,
        shape = S.r6,
        colors = editorTextFieldColors,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = placeholder,
                    tint = S.Red
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
    var showPhotoViewer by remember(recordId) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditing) {
                        DisplayHeading(
                            text = "editing",
                            italicWord = "edit",
                            style = SmritiType.DisplaySmall,
                            color = S.Ink
                        )
                    } else {
                        DisplayHeading(
                            text = "memory",
                            italicWord = "mem",
                            style = SmritiType.DisplaySmall,
                            color = S.Ink
                        )
                    }
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
                            tint = S.Ink
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
                                tint = S.Muted
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
                                tint = S.Red
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
                                    tint = S.Ink
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = S.Paper,
                    titleContentColor = S.Ink,
                    navigationIconContentColor = S.Ink,
                    actionIconContentColor = S.Ink
                )
            )
        },
        containerColor = S.Paper
    ) { innerPadding ->
        val currentRecord = record
        if (currentRecord == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = S.Red)
            }
        } else {
            val photoBitmap: ImageBitmap? = remember(currentRecord.photoPath) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(currentRecord.photoPath, options)
                    var sampleSize = 1
                    while (options.outWidth / (sampleSize * 2) >= 1080 && options.outHeight / (sampleSize * 2) >= 1080) {
                        sampleSize *= 2
                    }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = sampleSize
                    BitmapFactory.decodeFile(currentRecord.photoPath, options)?.asImageBitmap()
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
                    .padding(S.gutter)
            ) {
                if (photoBitmap != null) {
                    val ratio = remember(photoBitmap) {
                        (photoBitmap.width.toFloat() / photoBitmap.height.toFloat()).coerceAtLeast(0.75f)
                    }
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = "Captured Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(S.r6)
                            .then(if (isEditing) Modifier else Modifier.clickable { showPhotoViewer = true })
                    )
                    Spacer(modifier = Modifier.height(S.gutter))
                } else if (currentRecord.photoPath.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(S.md)
                    ) {
                        NodeSquare()
                        BracketLabel("voice note")
                    }
                    Spacer(modifier = Modifier.height(S.gutter))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(S.md),
                    verticalArrangement = Arrangement.spacedBy(S.xs)
                ) {
                    BracketLabel(formatRelativeTime(currentRecord.createdAt))
                    currentRecord.enrichmentModel?.let { BracketLabel(it) }
                }
                Spacer(modifier = Modifier.height(S.sm))

                if (isEditing && currentDraft != null) {
                    OutlinedTextField(
                        value = currentDraft.title,
                        onValueChange = { draft = currentDraft.copy(title = it) },
                        placeholder = {
                            Text(
                                text = "title",
                                style = SmritiType.Display,
                                color = S.MutedSoft
                            )
                        },
                        singleLine = true,
                        textStyle = SmritiType.Display,
                        shape = S.r6,
                        colors = editorTextFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(S.md))

                    OutlinedTextField(
                        value = currentDraft.summary,
                        onValueChange = { draft = currentDraft.copy(summary = it) },
                        placeholder = {
                            Text(
                                text = "summary",
                                style = SmritiType.Body,
                                color = S.MutedSoft
                            )
                        },
                        singleLine = false,
                        minLines = 3,
                        textStyle = SmritiType.Body,
                        shape = S.r6,
                        colors = editorTextFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ListChipEditor(
                        title = "people",
                        items = currentDraft.people,
                        placeholder = "add person",
                        kind = ChipKind.Person,
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
                        title = "tags",
                        items = currentDraft.tags,
                        placeholder = "add tag",
                        kind = ChipKind.Tag,
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
                        title = "amounts",
                        items = currentDraft.amounts,
                        placeholder = "add amount",
                        kind = ChipKind.Amount,
                        onAddItem = { item ->
                            draft = currentDraft.copy(amounts = currentDraft.amounts + item)
                        },
                        onRemoveItem = { index ->
                            draft = currentDraft.copy(
                                amounts = currentDraft.amounts.filterIndexed { i, _ -> i != index }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(S.lg))
                    SectionLabel("tasks")
                    Spacer(modifier = Modifier.height(S.sm))

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
                                .padding(vertical = S.sm),
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
                                    checkedColor = S.Red,
                                    checkmarkColor = S.White,
                                    uncheckedColor = S.Hairline
                                )
                            )
                            OutlinedTextField(
                                value = task.text,
                                onValueChange = { newText ->
                                    val updated = currentDraft.tasks.toMutableList()
                                    updated[index] = task.copy(text = newText)
                                    draft = currentDraft.copy(tasks = updated)
                                },
                                placeholder = {
                                    Text(
                                        text = "task",
                                        style = SmritiType.Body,
                                        color = S.MutedSoft
                                    )
                                },
                                singleLine = true,
                                textStyle = SmritiType.Body,
                                shape = S.r6,
                                colors = editorTextFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = S.xs)
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
                                    tint = S.MutedSoft
                                )
                            }
                        }
                        HairlineRule()
                    }

                    Spacer(modifier = Modifier.height(S.sm))
                    SmritiOutlineButton(
                        label = "add task",
                        onClick = {
                            draft = currentDraft.copy(
                                tasks = currentDraft.tasks + TaskDraft(id = 0L, text = "", done = false)
                            )
                            shouldFocusNewTask = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = currentRecord.title.ifBlank { "untitled" },
                        style = SmritiType.Display,
                        color = S.Ink
                    )

                    when (currentRecord.enrichmentState) {
                        "PENDING", "RUNNING" -> {
                            Spacer(modifier = Modifier.height(S.sm))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(S.sm)
                            ) {
                                ThinkingDots()
                                BracketLabel(if (currentRecord.enrichmentState == "RUNNING") "understanding" else "queued")
                            }
                            Spacer(modifier = Modifier.height(S.sm))
                            ShimmerLine(widthFraction = 0.85f)
                            Spacer(modifier = Modifier.height(S.sm))
                            ShimmerLine(widthFraction = 0.55f)
                        }
                        "FAILED" -> {
                            Spacer(modifier = Modifier.height(S.sm))
                            BracketLabel("could not extract")
                        }
                        else -> {
                            if (currentRecord.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(S.sm))
                                Text(
                                    text = currentRecord.summary,
                                    style = SmritiType.Body,
                                    color = S.Muted
                                )
                            }
                        }
                    }

                    if (people.isNotEmpty() || tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(S.gutter))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(S.sm),
                            verticalArrangement = Arrangement.spacedBy(S.sm)
                        ) {
                            people.forEach { person ->
                                SmritiChip(text = person, kind = ChipKind.Person)
                            }
                            tags.forEach { tag ->
                                SmritiChip(text = tag, kind = ChipKind.Tag)
                            }
                        }
                    }

                    if (amounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(S.lg))
                        SectionLabel("amounts")
                        Spacer(modifier = Modifier.height(S.sm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(S.sm),
                            verticalArrangement = Arrangement.spacedBy(S.sm)
                        ) {
                            amounts.forEach { amount ->
                                SmritiChip(text = amount, kind = ChipKind.Amount)
                            }
                        }
                    }

                    if (tasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(S.lg))
                        SectionLabel("tasks")
                        Spacer(modifier = Modifier.height(S.sm))
                        tasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = S.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.done,
                                    onCheckedChange = { done -> vm.toggleTask(task.id, done) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = S.Red,
                                        checkmarkColor = S.White,
                                        uncheckedColor = S.Hairline
                                    )
                                )
                                Text(
                                    text = task.text,
                                    style = SmritiType.Body,
                                    color = if (task.done) S.MutedSoft else S.Ink,
                                    textDecoration = if (task.done) TextDecoration.LineThrough else null
                                )
                            }
                            HairlineRule()
                        }
                    }
                }

                if (currentRecord.ocrText.isNotBlank() || currentRecord.transcript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(S.lg))
                    DashedRule()
                    Spacer(modifier = Modifier.height(S.lg))
                }

                CollapsibleRawSection(
                    title = "what the camera read",
                    content = currentRecord.ocrText
                )

                CollapsibleRawSection(
                    title = "what you said",
                    content = currentRecord.transcript
                )

                Spacer(modifier = Modifier.height(S.section))
            }

            if (showPhotoViewer && currentRecord.photoPath.isNotBlank()) {
                PhotoViewer(
                    photoPath = currentRecord.photoPath,
                    onDismiss = { showPhotoViewer = false }
                )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = S.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(title.lowercase())
            Text(
                text = if (expanded) "−" else "+",
                style = SmritiType.MetaMedium,
                color = S.Red,
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) "Collapse" else "Expand"
                }
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(S.md))
            HairlineRule()
            Spacer(modifier = Modifier.height(S.md))
            Text(
                text = content,
                style = SmritiType.Mono,
                color = S.Muted
            )
        }
    }
}