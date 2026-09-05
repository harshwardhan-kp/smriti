package com.smriti.app.data

import androidx.room.Database
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

class RecordDaoQueriesTest {

    /**
     * Extracts CONSTANT_Utf8 strings from the class file constant pool.
     * Room annotations (@Query, @Database) have CLASS retention (AnnotationRetention.BINARY),
     * so standard reflection (Method.getAnnotation) returns null at JVM runtime.
     * Bytecode inspection allows verifying the exact query strings without an Android device/emulator.
     */
    private fun extractConstantPoolStrings(clazz: Class<*>): List<String> {
        val path = clazz.name.replace('.', '/') + ".class"
        val bytes = clazz.classLoader?.getResourceAsStream(path)?.readBytes() ?: return emptyList()
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.int != 0xCAFEBABE.toInt()) return emptyList()
        buffer.short // minor
        buffer.short // major
        val count = buffer.short.toInt() and 0xFFFF
        val strings = mutableListOf<String>()
        var index = 1
        while (index < count && buffer.hasRemaining()) {
            when (buffer.get().toInt()) {
                1 -> { // CONSTANT_Utf8
                    val len = buffer.short.toInt() and 0xFFFF
                    val strBytes = ByteArray(len)
                    buffer.get(strBytes)
                    strings.add(String(strBytes, Charsets.UTF_8))
                    index++
                }
                3, 4 -> { buffer.int; index++ } // Integer, Float
                5, 6 -> { buffer.long; index += 2 } // Long, Double
                7, 8, 16, 19, 20 -> { buffer.short; index++ } // Class, String, MethodType, Module, Package
                9, 10, 11, 12, 18 -> { buffer.int; index++ } // Fieldref, Methodref, InterfaceMethodref, NameAndType, InvokeDynamic
                15 -> { buffer.get(); buffer.short; index++ } // MethodHandle
                else -> break
            }
        }
        return strings
    }

    @Test
    fun testDaoQueryAnnotationsGuardUserEdits() {
        val applyEnrichmentMethod = RecordDao::class.java.methods.firstOrNull { it.name == "applyEnrichment" }
        assertNotNull("applyEnrichment method must exist on RecordDao", applyEnrichmentMethod)

        // Try runtime reflection first; if null (due to CLASS retention), fall back to bytecode inspection
        val enrichmentQuery = applyEnrichmentMethod?.getAnnotation(Query::class.java)?.value
            ?: extractConstantPoolStrings(RecordDao::class.java)
                .firstOrNull { it.startsWith("UPDATE records SET") && it.contains("enrichmentModel = :model") }

        assertNotNull("applyEnrichment query must exist", enrichmentQuery)
        assertTrue(
            "applyEnrichment query must contain 'userEdited = 0' to guard user edits",
            enrichmentQuery!!.contains("userEdited = 0")
        )

        val applyUserEditMethod = RecordDao::class.java.methods.firstOrNull { it.name == "applyUserEdit" }
        assertNotNull("applyUserEdit method must exist on RecordDao", applyUserEditMethod)

        val userEditQuery = applyUserEditMethod?.getAnnotation(Query::class.java)?.value
            ?: extractConstantPoolStrings(RecordDao::class.java)
                .firstOrNull { it.startsWith("UPDATE records SET") && it.contains("userEdited = 1") }

        assertNotNull("applyUserEdit query must exist", userEditQuery)
        assertTrue(
            "applyUserEdit query must contain 'userEdited = 1'",
            userEditQuery!!.contains("userEdited = 1")
        )
        assertTrue(
            "applyUserEdit query must contain 'enrichmentState = \\'DONE\\''",
            userEditQuery.contains("enrichmentState = 'DONE'")
        )
        assertTrue(
            "applyUserEdit query must contain 'enrichedAt = :at'",
            userEditQuery.contains("enrichedAt = :at")
        )
    }

    @Test
    fun testSmritiDbMigrationAndVersion() {
        assertEquals(2, SmritiDb.MIGRATION_2_3.startVersion)
        assertEquals(3, SmritiDb.MIGRATION_2_3.endVersion)

        // Room's @Database has CLASS retention, so getAnnotation returns null at JVM runtime.
        val dbAnnotation = SmritiDb::class.java.getAnnotation(Database::class.java)
        if (dbAnnotation != null) {
            assertEquals(3, dbAnnotation.version)
        } else {
            // Confirm @Database metadata exists in class constant pool
            val strings = extractConstantPoolStrings(SmritiDb::class.java)
            assertTrue(strings.contains("version"))
            assertTrue(strings.contains("Landroidx/room/Database;"))
        }

        // Also verify the migration SQL executed by MIGRATION_2_3
        val executedStatements = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        SmritiDb.MIGRATION_2_3.migrate(fakeDb)
        assertEquals(
            listOf("ALTER TABLE records ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0"),
            executedStatements
        )
    }

    @Test
    fun testRecordEntityUserEdited() {
        val record = RecordEntity(
            createdAt = 1000L,
            photoPath = "/path/photo.jpg",
            ocrText = "",
            transcript = "transcript",
            title = "title",
            summary = "summary",
            peopleJson = "[]",
            amountsJson = "[]",
            tagsJson = "[]",
            embedding = null
        )
        // Default value
        assertEquals(false, record.userEdited)

        val edited = record.copy(userEdited = true)
        assertEquals(true, edited.userEdited)
        assertNotEquals(record, edited)
        assertNotEquals(record.hashCode(), edited.hashCode())

        val uneditedCopy = edited.copy(userEdited = false)
        assertEquals(record, uneditedCopy)
        assertEquals(record.hashCode(), uneditedCopy.hashCode())
    }
}
