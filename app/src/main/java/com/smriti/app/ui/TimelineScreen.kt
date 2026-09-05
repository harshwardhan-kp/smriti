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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.TaskEntity
import com.smriti.app.ui.components.BracketLabel
import com.smriti.app.ui.components.BracketLabelLive
import com.smriti.app.ui.components.ChipKind
import com.smriti.app.ui.components.DisplayHeading
import com.smriti.app.ui.components.HairlineRule
import com.smriti.app.ui.components.SectionLabel
import com.smriti.app.ui.components.SmritiChip
import com.smriti.app.ui.theme.S
import com.smriti.app.ui.theme.SmritiType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    DisplayHeading(
                        text = "timeline",
                        italicWord = "time",
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
        if (records.isEmpty() && openTasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = S.gutter)
                    .padding(top = S.xl)
            ) {
                DisplayHeading(
                    text = "nothing captured yet",
                    italicWord = "nothing",
                    style = SmritiType.Display,
                    color = S.Ink
                )
                Spacer(modifier = Modifier.height(S.md))
                Text(
                    text = "point the camera at something and talk, or hold the mic and just talk.",
                    style = SmritiType.Body,
                    color = S.Muted
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(
                    modifier = Modifier.padding(start = S.gutter, end = S.gutter, bottom = S.gutter),
                    horizontalArrangement = Arrangement.spacedBy(S.md)
                ) {
                    BracketLabel("${records.size} memories")
                    BracketLabel("${openTasks.size} open")
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = S.gutter),
                    contentPadding = PaddingValues(bottom = S.section)
                ) {
                    if (openTasks.isNotEmpty()) {
                        stickyHeader {
                            Surface(
                                color = S.Paper,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = S.lg, bottom = S.md)
                                ) {
                                    SectionLabel("open tasks")
                                    Spacer(modifier = Modifier.height(S.sm))
                                    HairlineRule()
                                }
                            }
                        }

                        // Keys must be unique across the WHOLE LazyColumn, not per items() block. Tasks and
                        // records are separate tables with their own autoincrement ids, so task 1 and
                        // record 1 collided and Compose threw IllegalArgumentException("Key \"1\" was
                        // already used") on the first scroll that measured both sections.
                        items(openTasks, key = { "task-${it.id}" }) { task ->
                            TaskRow(
                                task = task,
                                onToggle = { done -> vm.toggleTask(task.id, done) }
                            )
                        }
                    }

                    if (records.isNotEmpty()) {
                        stickyHeader {
                            Surface(
                                color = S.Paper,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = S.lg, bottom = S.md)
                                ) {
                                    SectionLabel("memories")
                                    Spacer(modifier = Modifier.height(S.sm))
                                    HairlineRule()
                                }
                            }
                        }

                        items(records, key = { "record-${it.id}" }) { record ->
                            RecordCard(
                                record = record,
                                onClick = { onOpenRecord(record.id) }
                            )
                        }
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = S.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = onToggle,
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
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )

            task.dueDateMillis?.let { dueMillis ->
                val isOverdue = dueMillis < System.currentTimeMillis()
                Spacer(modifier = Modifier.width(S.sm))
                if (isOverdue) {
                    BracketLabelLive(text = formatDueDate(dueMillis))
                } else {
                    BracketLabel(text = formatDueDate(dueMillis))
                }
            }
        }
        HairlineRule()
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = S.gutter)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(S.gutter),
            verticalAlignment = Alignment.Top
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(S.r6)
                )
            } else if (record.photoPath.isBlank()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(S.RedTint.copy(alpha = 0.10f), S.r6),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice note",
                        tint = S.Red
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(S.PaperSunk, S.r6)
                        .border(BorderStroke(S.hairlineWidth, S.Hairline), S.r6)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                BracketLabel(formatRelativeTime(record.createdAt))

                Spacer(modifier = Modifier.height(S.sm))

                Text(
                    text = record.title.ifBlank { "untitled" },
                    style = SmritiType.DisplaySmall,
                    color = S.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                when (record.enrichmentState) {
                    "PENDING", "RUNNING" -> {
                        Spacer(modifier = Modifier.height(S.xs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(S.sm)
                        ) {
                            ThinkingDots()
                            BracketLabel(if (record.enrichmentState == "RUNNING") "understanding" else "queued")
                        }
                        Spacer(modifier = Modifier.height(S.sm))
                        ShimmerLine(widthFraction = 0.85f)
                        Spacer(modifier = Modifier.height(S.xs))
                        ShimmerLine(widthFraction = 0.55f)
                    }
                    "FAILED" -> {
                        Spacer(modifier = Modifier.height(S.xs))
                        BracketLabel("could not extract")
                    }
                    else -> {
                        if (record.summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(S.xs))
                            Text(
                                text = record.summary,
                                style = SmritiType.BodySmall,
                                color = S.Muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(S.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(S.sm),
                        verticalArrangement = Arrangement.spacedBy(S.sm)
                    ) {
                        tags.forEach { tag ->
                            SmritiChip(text = tag, kind = ChipKind.Tag)
                        }
                    }
                }
            }
        }

        HairlineRule()
    }
}