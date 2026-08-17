package ir.ketabyar.shelf.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books") fun observeAll(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books WHERE section = :section") fun observeSection(section: String): Flow<List<BookEntity>>
    @Query("SELECT * FROM books WHERE registrationNumber = :number LIMIT 1") suspend fun byRegistration(number: String): BookEntity?
    @Insert suspend fun insert(book: BookEntity): Long
    @Update suspend fun update(book: BookEntity)
    @Delete suspend fun delete(book: BookEntity)
    @Insert suspend fun audit(entry: AuditEntity)
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 1") suspend fun lastAudit(): AuditEntity?
}

@Database(entities = [BookEntity::class, AuditEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() { abstract fun bookDao(): BookDao }

class Converters {
    @TypeConverter fun section(value: ir.ketabyar.shelf.core.BookSection) = value.name
    @TypeConverter fun section(value: String) = ir.ketabyar.shelf.core.BookSection.valueOf(value)
}

