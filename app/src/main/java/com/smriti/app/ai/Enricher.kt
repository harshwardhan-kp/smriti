package com.smriti.app.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.smriti.app.data.Converters
import com.smriti.app.data.SmritiDb
import com.smriti.app.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Enricher {

    private const val TAG = "SmritiEnrich"
    private const val DEBOUNCE_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debounceLock = Any()
    private var debounceJob: Job? = null
    private val drainMutex = Mutex()

    /**
     * Debounced trigger, safe to call repeatedly. Repeated calls within the ~20s window
     * collapse into one run.
     */
    fun request(context: Context) {
        val appContext = context.applicationContext
        synchronized(debounceLock) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(DEBOUNCE_MS)
                drain(appContext)
            }
        }
    }

    /**
     * Process every PENDING record. Guarded with a Mutex so two drains never run concurrently.
     */
    suspend fun drain(context: Context) = withContext(Dispatchers.IO) {
        drainMutex.withLock {
            val appContext = context.applicationContext
            val dao = SmritiDb.get(appContext).recordDao()
            dao.resetRunningToPending()
            val pending = dao.pendingEnrichment()
            if (pending.isEmpty()) {
                return@withLock
            }

            Log.i(TAG, "Pending enrichment: ${pending.size} record(s)")

            val backend = try {
                BackendFactory.create(appContext).getOrThrow()
            } catch (t: Throwable) {
                Log.e(TAG, "Cannot load backend for enrichment: ${t.message}", t)
                null
            }

            val extractor = backend?.let { Extractor(it) }
            val gson = Gson()

            for (record in pending) {
                val startMs = System.currentTimeMillis()
                val ocr = try {
                    Ocr.read(java.io.File(record.photoPath), appContext)
                } catch (t: Throwable) {
                    ""
                }
                Log.i(TAG, "record ${record.id} ocr: ${ocr.length} chars")
                dao.setOcrText(record.id, ocr)

                if (backend == null || extractor == null) {
                    continue
                }

                try {
                    dao.markEnrichment(record.id, "RUNNING", attemptDelta = 1, error = null)
                    val structured = extractor.extract(ocrText = "", transcript = record.transcript)
                    val finalTitle = titleFor(structured.title, record.transcript, "")
                    val embedding = embedFor(
                        context = appContext,
                        title = finalTitle,
                        summary = structured.summary,
                        ocrText = "",
                        transcript = record.transcript
                    )
                    val now = System.currentTimeMillis()

                    dao.applyEnrichment(
                        id = record.id,
                        title = finalTitle,
                        summary = structured.summary,
                        people = gson.toJson(structured.people),
                        amounts = gson.toJson(structured.amounts),
                        tags = gson.toJson(structured.tags),
                        embedding = embedding,
                        ocr = ocr,
                        at = now,
                        model = backend.label
                    )

                    val tasks = structured.actions
                        .filter { it.text.isNotBlank() }
                        .map { action ->
                            TaskEntity(
                                recordId = record.id,
                                text = action.text,
                                dueDateMillis = parseDue(action.due)
                            )
                        }
                    if (tasks.isNotEmpty()) {
                        dao.insertTasks(tasks)
                    }

                    val elapsedMs = System.currentTimeMillis() - startMs
                    Log.i(TAG, "Record ${record.id} enriched in ${elapsedMs}ms (state: DONE, model: ${backend.label})")
                } catch (t: Throwable) {
                    val elapsedMs = System.currentTimeMillis() - startMs
                    dao.markEnrichment(record.id, "PENDING", attemptDelta = 0, error = t.message)
                    Log.e(TAG, "Record ${record.id} enrichment failed in ${elapsedMs}ms (state: PENDING): ${t.message}", t)
                }
            }
        }
    }

    /**
     * Shared title formatting logic. Collapses whitespace, trims, cuts on word boundary at 60 chars.
     */
    fun titleFor(modelTitle: String, transcript: String, ocrText: String = ""): String {
        val source = modelTitle.ifBlank { transcript }.ifBlank { ocrText }
        val flat = source.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) {
            val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            return "Untitled capture · $clock"
        }
        if (flat.length <= 60) return flat
        val cut = flat.take(60)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > 30) cut.take(lastSpace) + "…" else cut + "…"
    }

    /**
     * Shared embedding logic. Embeds title + summary + transcript + ocrText using [EmbedderHolder].
     */
    suspend fun embedFor(
        context: Context,
        title: String,
        summary: String,
        ocrText: String = "",
        transcript: String
    ): ByteArray? = try {
        val embedder = EmbedderHolder.get(context)
        val text = listOf(title, summary, transcript, ocrText)
            .filter { it.isNotBlank() }
            .joinToString(" \n ")
            .take(1000)
        if (embedder == null || text.isBlank()) null
        else Converters().fromFloatArray(embedder.embed(text))
    } catch (_: Throwable) {
        null
    }

    /**
     * Shared due-date parsing logic. Accepts ISO-8601 (YYYY-MM-DD) and nothing else.
     */
    fun parseDue(due: String?): Long? {
        if (due.isNullOrBlank() || due.equals("null", ignoreCase = true)) return null
        return try {
            LocalDate.parse(due.trim())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Throwable) {
            null
        }
    }
}
