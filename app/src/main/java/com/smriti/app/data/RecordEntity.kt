package com.smriti.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAt: Long,
    val photoPath: String,
    val ocrText: String,
    val transcript: String,
    val title: String,
    val summary: String,
    val peopleJson: String,
    val amountsJson: String,
    val tagsJson: String,
    val embedding: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecordEntity

        if (id != other.id) return false
        if (createdAt != other.createdAt) return false
        if (photoPath != other.photoPath) return false
        if (ocrText != other.ocrText) return false
        if (transcript != other.transcript) return false
        if (title != other.title) return false
        if (summary != other.summary) return false
        if (peopleJson != other.peopleJson) return false
        if (amountsJson != other.amountsJson) return false
        if (tagsJson != other.tagsJson) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + photoPath.hashCode()
        result = 31 * result + ocrText.hashCode()
        result = 31 * result + transcript.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + peopleJson.hashCode()
        result = 31 * result + amountsJson.hashCode()
        result = 31 * result + tagsJson.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}