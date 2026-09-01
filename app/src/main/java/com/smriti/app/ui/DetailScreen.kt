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