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