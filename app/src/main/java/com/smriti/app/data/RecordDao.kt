package com.smriti.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<RecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecord(id: Long): RecordEntity?

    @Query("SELECT * FROM tasks ORDER BY dueDateMillis IS NULL ASC, dueDateMillis ASC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE recordId = :recordId")
    fun observeTasksForRecord(recordId: Long): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setTaskDone(id: Long, done: Boolean)

    @Query("SELECT * FROM records WHERE embedding IS NOT NULL")
    suspend fun allRecordsWithEmbedding(): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun countRecords(): Int

    @Query("SELECT * FROM records WHERE embedding IS NULL ORDER BY createdAt DESC")
    suspend fun recordsMissingEmbedding(): List<RecordEntity>

    @Query("UPDATE records SET embedding = :embedding WHERE id = :id")
    suspend fun setEmbedding(id: Long, embedding: ByteArray?)
}