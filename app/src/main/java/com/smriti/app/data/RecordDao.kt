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

    @Query("SELECT * FROM records WHERE id = :id")
    fun observeRecord(id: Long): Flow<RecordEntity?>

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

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY dueDateMillis IS NULL ASC, dueDateMillis ASC")
    suspend fun openTasks(): List<TaskEntity>

    @Query("SELECT * FROM records WHERE embedding IS NULL ORDER BY createdAt DESC")
    suspend fun recordsMissingEmbedding(): List<RecordEntity>

    @Query("UPDATE records SET embedding = :embedding WHERE id = :id")
    suspend fun setEmbedding(id: Long, embedding: ByteArray?)

    @Query("SELECT * FROM records WHERE enrichmentState = 'PENDING' AND enrichmentAttempts < 3 ORDER BY createdAt ASC")
    suspend fun pendingEnrichment(): List<RecordEntity>

    @Query("UPDATE records SET enrichmentState = 'PENDING' WHERE enrichmentState = 'RUNNING'")
    suspend fun resetRunningToPending()

    @Query("UPDATE records SET enrichmentState = :state, enrichmentAttempts = enrichmentAttempts + :attemptDelta, enrichmentError = :error WHERE id = :id")
    suspend fun markEnrichment(id: Long, state: String, attemptDelta: Int, error: String?)

    @Query("UPDATE records SET ocrText = :ocr WHERE id = :id")
    suspend fun setOcrText(id: Long, ocr: String)

    @Query("UPDATE records SET title = :title, summary = :summary, peopleJson = :people, amountsJson = :amounts, tagsJson = :tags, embedding = :embedding, ocrText = :ocr, enrichmentState = 'DONE', enrichedAt = :at, enrichmentModel = :model, enrichmentError = NULL WHERE id = :id")
    suspend fun applyEnrichment(id: Long, title: String, summary: String, people: String, amounts: String, tags: String, embedding: ByteArray?, ocr: String, at: Long, model: String)

    @Query("SELECT COUNT(*) FROM records WHERE enrichmentState = 'PENDING'")
    suspend fun pendingCount(): Int
}