package ir.ketabyar.shelf.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.ketabyar.shelf.core.BookSection

@Entity(tableName = "books", indices = [Index(value = ["registrationNumber"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val section: BookSection,
    val title: String,
    val authorFirstName: String = "",
    val authorLastName: String = "",
    val subject: String = "",
    val registrationNumber: String,
    val mainClass: String = "",
    val classDecimal: String = "",
    val language: String = "",
    val languageCode: String = "",
    val literaturePeriod: String = "",
    val workType: String = "",
    val authorLetter: String = "",
    val authorNumber: String = "",
    val workMark: String = "",
    val titleLetter: String = "",
    val volume: String = "",
    val edition: String = "",
    val year: String = "",
    val notes: String = "",
    val shelfName: String = "",
    val rowNumber: Int? = null,
    val needsReview: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_log")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val action: String,
    val snapshot: String,
    val timestamp: Long = System.currentTimeMillis()
)

