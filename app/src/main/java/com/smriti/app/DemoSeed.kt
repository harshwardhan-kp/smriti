package com.smriti.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.gson.Gson
import com.smriti.app.data.RecordEntity
import com.smriti.app.data.SmritiDb
import com.smriti.app.data.TaskEntity
import java.io.File
import java.io.FileOutputStream

/**
 * the recall demo is worthless with an empty database — asking
 * "what did I commit to this week?" needs something to find. This seeds a realistic corpus for
 * rehearsal. It is a demo affordance, not test fixtures, and it must be obvious in the UI that
 * these are seeded, so every seeded record carries the tag "seed".
 */
object DemoSeed {

    private val gson = Gson()
    private const val DAY_MS = 86_400_000L

    private val seedTitles = listOf(
        "Sprint sync — API cutover",
        "Delivery challan — Sharma Traders",
        "Invoice 4471 — Anand Electricals",
        "Compressor nameplate — Unit B",
        "Site note — third floor pour",
        "Lab log — batch 22",
        "Whiteboard — hiring loop",
        "Hindi site note",
        "Meter reading — DG set"
    )

    suspend fun seed(context: Context, force: Boolean = false): Int {
        val db = SmritiDb.get(context)
        if (!force && db.recordDao().countRecords() > 0) return 0

        val now = System.currentTimeMillis()

        var added = 0

        // 1. Sprint sync — API cutover
        run {
            val idx = 1
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 0 * DAY_MS
            val title = seedTitles[0]
            val people = listOf("Rohit", "Priya")
            val tags = listOf("seed", "api", "sprint")
            val ocrText = "Sprint Sync — 28 Aug\nRohit: ship auth API by Friday\nPriya: frontend cutover /api/v2\nOrder 200 units - Sharma Traders\nBlocked: staging certs"
            val transcript = "Sprint sync, Rohit will ship the auth API by Friday and Priya will handle the frontend cutover."
            val summary = "Sprint sync: Rohit ships auth API by Friday, Priya handles cutover, need 200 units from Sharma."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(people),
                amountsJson = gson.toJson(emptyList<Any>()),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Rohit ships the auth API", dueDateMillis = now + 4 * DAY_MS),
                TaskEntity(recordId = id, text = "Order 200 more units", dueDateMillis = null)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 2. Delivery challan — Sharma Traders
        run {
            val idx = 2
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 1 * DAY_MS
            val title = seedTitles[1]
            val tags = listOf("seed", "delivery", "vendor")
            val amounts = listOf(mapOf("value" to 200, "currency" to "units", "label" to "steel brackets"))
            val ocrText = "SHARMA TRADERS\nDelivery Challan No. DC-1184\nDate: 29/08/2026\nItem: Steel Brackets - 200 units\nLR No: 88471  Vehicle: MH12 AB 1234\nReceiver: Smriti Works"
            val transcript = "Got the delivery challan from Sharma Traders, two hundred steel brackets delivered."
            val summary = "Delivery challan DC-1184 from Sharma Traders for 200 steel brackets."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(emptyList<String>()),
                amountsJson = gson.toJson(amounts),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Verify challan against PO", dueDateMillis = now + 2 * DAY_MS)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 3. Invoice 4471 — Anand Electricals
        run {
            val idx = 3
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 1 * DAY_MS
            val title = seedTitles[2]
            val tags = listOf("seed", "invoice", "finance")
            val amounts = listOf(mapOf("value" to 84500.0, "currency" to "INR", "label" to "invoice total"))
            val ocrText = "ANAND ELECTRICALS\nInvoice No: 4471 Date: 27/08/2026\nGSTIN: 27AAECA1234F1Z5\nCGST 9% SGST 9%\nTotal: Rs 84,500.00  Due: 15 days\nUPI: anand@okicici  IFSC: SBIN0001234"
            val transcript = "Invoice forty-four seventy-one from Anand Electricals for eighty-four thousand five hundred rupees."
            val summary = "Invoice 4471 from Anand Electricals for Rs 84,500 pending payment."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(emptyList<String>()),
                amountsJson = gson.toJson(amounts),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Pay invoice 4471", dueDateMillis = now + 6 * DAY_MS)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 4. Compressor nameplate — Unit B
        run {
            val idx = 4
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 2 * DAY_MS
            val title = seedTitles[3]
            val tags = listOf("seed", "maintenance")
            val ocrText = "KIRLOSKAR KCX-22\nS.No: KCX22-88471-B\nRating: 22 kW  415V  3Ph  50Hz\nRPM 2980  Pressure 10 bar\nMfg: 03/2024  Made in India"
            val transcript = "Compressor nameplate on unit B, Kirloskar KCX twenty-two, twenty-two kilowatt."
            val summary = "Compressor Unit B nameplate: Kirloskar KCX-22, 22kW, S.No KCX22-88471-B."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(emptyList<String>()),
                amountsJson = gson.toJson(emptyList<Any>()),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            db.recordDao().insertRecord(record)
            added++
        }

        // 5. Site note — third floor pour
        run {
            val idx = 5
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 3 * DAY_MS
            val title = seedTitles[4]
            val people = listOf("Ganesh")
            val tags = listOf("seed", "site")
            val ocrText = "Site Note - 26 Aug\nThird floor slab pour - delayed\nReason: overnight rain, shuttering wet\nNext window: tomorrow 7am\nContact: Ganesh 98230 11234"
            val transcript = "Third floor pour got delayed because of rain, Ganesh says we will try tomorrow morning."
            val summary = "Third floor pour delayed by rain; Ganesh to reschedule for tomorrow 7am."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(people),
                amountsJson = gson.toJson(emptyList<Any>()),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Reschedule pour after rain", dueDateMillis = now + 1 * DAY_MS)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 6. Lab log — batch 22
        run {
            val idx = 6
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 4 * DAY_MS
            val title = seedTitles[5]
            val tags = listOf("seed", "lab")
            val ocrText = "Lab Log Book — Batch 22\nDate 24/08 pH 7.2 Titration 0.42N\nObservation: precipitate at 60C\nAction: repeat titration\nChemist: signed"
            val transcript = "Lab log for batch twenty-two, titration was off, need to repeat it."
            val summary = "Lab batch 22 needs titration repeated due to precipitate at 60C."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(emptyList<String>()),
                amountsJson = gson.toJson(emptyList<Any>()),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Repeat titration for batch 22", dueDateMillis = null)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 7. Whiteboard — hiring loop
        run {
            val idx = 7
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 4 * DAY_MS
            val title = seedTitles[6]
            val people = listOf("Meera")
            val tags = listOf("seed", "hiring")
            val ocrText = "WHITEBOARD 25/08\nHiring Loop - Backend Engineer\nRound 1: DSA - Meera\nRound 2: System Design - Rohit\nRubric: missing  JD: 3-5 yrs Go/Java"
            val transcript = "Whiteboard hiring loop for backend engineer, Meera is taking the first round, need to send rubric."
            val summary = "Hiring loop for backend engineer; Meera to handle DSA round, rubric pending."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(people),
                amountsJson = gson.toJson(emptyList<Any>()),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Send Meera the interview rubric", dueDateMillis = now + 3 * DAY_MS)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 8. Hindi site note
        run {
            val idx = 8
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 5 * DAY_MS
            val title = seedTitles[7]
            val tags = listOf("seed", "site", "hindi")
            val ocrText = "साइट नोट 23/08\nसीमेंट 50 बैग - देरी से आया\nट्रक पंक्चर - खराडी रोड\nसंपर्क: गणेश  98230 11234"
            val transcript = "कल सीमेंट के पचास बैग आने वाले थे लेकिन ट्रक पंक्चर होने से लेट हो गए।"
            val summary = "Cement delivery delayed: 50 bags late due to truck puncture on Kharadi road."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(emptyList<String>()),
                amountsJson = gson.toJson(emptyList<Any>()),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            val id = db.recordDao().insertRecord(record)
            val tasks = listOf(
                TaskEntity(recordId = id, text = "Follow up on cement delivery", dueDateMillis = now + 2 * DAY_MS)
            )
            db.recordDao().insertTasks(tasks)
            added++
        }

        // 9. Meter reading — DG set
        run {
            val idx = 9
            val photoPath = photoPlaceholder(context, idx)
            val createdAt = now - 6 * DAY_MS
            val title = seedTitles[8]
            val tags = listOf("seed", "maintenance")
            val amounts = listOf(mapOf("value" to 1284.0, "currency" to "hours", "label" to "runtime"))
            val ocrText = "DG SET - CUMMINS 62.5 kVA\nMeter: 01284 hrs\nDate: 22/08/2026\nDiesel: 42 L  Load: 38%\nNext service: 1500 hrs"
            val transcript = "DG set meter reading is twelve eighty-four hours."
            val summary = "DG set Cummins 62.5kVA at 1284 hours runtime, next service at 1500 hours."
            val record = RecordEntity(
                createdAt = createdAt,
                photoPath = photoPath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = gson.toJson(emptyList<String>()),
                amountsJson = gson.toJson(amounts),
                tagsJson = gson.toJson(tags),
                embedding = null
            )
            db.recordDao().insertRecord(record)
            added++
        }

        return added
    }

    suspend fun clear(context: Context): Int {
        val db = SmritiDb.get(context)
        val sqlite = db.openHelper.writableDatabase
        var count = 0
        sqlite.query("SELECT COUNT(*) FROM records WHERE tagsJson LIKE '%seed%'").use { cursor ->
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        }
        sqlite.execSQL("DELETE FROM tasks WHERE recordId IN (SELECT id FROM records WHERE tagsJson LIKE '%seed%')")
        sqlite.execSQL("DELETE FROM records WHERE tagsJson LIKE '%seed%'")
        return count
    }

    fun photoPlaceholder(context: Context, index: Int): String {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "seed_$index.jpg")
        if (file.exists()) return file.absolutePath

        val colors = intArrayOf(
            Color.rgb(107, 122, 143),
            Color.rgb(122, 107, 143),
            Color.rgb(107, 143, 122),
            Color.rgb(143, 122, 107),
            Color.rgb(143, 107, 122),
            Color.rgb(122, 143, 107),
            Color.rgb(107, 143, 143),
            Color.rgb(143, 143, 107),
            Color.rgb(117, 107, 143)
        )
        val bg = colors[Math.abs(index) % colors.size]

        val title = when {
            index in 1..seedTitles.size -> seedTitles[index - 1]
            index in seedTitles.indices -> seedTitles[index]
            else -> "Seed $index"
        }

        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }

        val lines = mutableListOf<String>()
        var pos = 0
        while (pos < title.length) {
            val end = minOf(pos + 26, title.length)
            lines.add(title.substring(pos, end))
            pos = end
        }

        val lineHeight = 36f
        val totalHeight = lines.size * lineHeight
        var y = (640f - totalHeight) / 2f + 28f
        for (line in lines) {
            val textWidth = paint.measureText(line)
            val x = (480f - textWidth) / 2f
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        bitmap.recycle()
        return file.absolutePath
    }
}
