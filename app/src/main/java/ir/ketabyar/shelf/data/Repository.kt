package ir.ketabyar.shelf.data

import androidx.room.withTransaction
import ir.ketabyar.shelf.core.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookRepository @Inject constructor(private val db: AppDatabase) {
    val books: Flow<List<BookEntity>> = db.bookDao().observeAll()
    suspend fun isDuplicate(registration: String, excludeId: Long? = null): Boolean {
        val found = db.bookDao().byRegistration(PersianNormalizer.normalize(registration)) ?: return false
        return found.id != excludeId
    }
    suspend fun add(book: BookEntity): Long = db.withTransaction {
        val normalized = book.copy(registrationNumber = PersianNormalizer.normalize(book.registrationNumber))
        val id = db.bookDao().insert(normalized)
        db.bookDao().audit(AuditEntity(bookId = id, action = "CREATE", snapshot = normalized.toString()))
        id
    }
    suspend fun delete(book: BookEntity) = db.withTransaction {
        db.bookDao().audit(AuditEntity(bookId = book.id, action = "DELETE", snapshot = book.toString()))
        db.bookDao().delete(book)
    }
    suspend fun update(book: BookEntity) = db.withTransaction {
        val normalized = book.copy(registrationNumber = PersianNormalizer.normalize(book.registrationNumber), updatedAt = System.currentTimeMillis())
        db.bookDao().audit(AuditEntity(bookId = book.id, action = "UPDATE", snapshot = normalized.toString()))
        db.bookDao().update(normalized)
    }
    suspend fun deleteAll() = db.withTransaction {
        db.bookDao().deleteAll()
        db.bookDao().audit(AuditEntity(bookId = 0, action = "DELETE_ALL", snapshot = "all books"))
    }
}
