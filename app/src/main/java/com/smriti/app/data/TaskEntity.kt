package com.smriti.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["recordId"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recordId: Long,
    val text: String,
    val dueDateMillis: Long? = null,
    val done: Boolean = false
)