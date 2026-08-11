package ir.deweyshelf.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.deweyshelf.app.domain.DeweyBook
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "books",
    indices = [Index(value = ["createdAt"])],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val mainClass: Int,
    val decimalPart: String,
    val authorLetter: String,
    val authorNumber: String,
    val workMark: String,
    val volume: Int?,
    val copyNumber: Int?,
    val publicationYear: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(books: List<BookEntity>)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}

@Database(
    entities = [BookEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DeweyDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var instance: DeweyDatabase? = null

        fun create(context: Context): DeweyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DeweyDatabase::class.java,
                "dewey-shelf.db",
            ).build().also { instance = it }
        }
    }
}

fun BookEntity.toDomain(): DeweyBook = DeweyBook(
    id = id,
    title = title,
    mainClass = mainClass,
    decimalPart = decimalPart,
    authorLetter = authorLetter,
    authorNumber = authorNumber,
    workMark = workMark,
    volume = volume,
    copyNumber = copyNumber,
    publicationYear = publicationYear,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DeweyBook.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    mainClass = mainClass,
    decimalPart = decimalPart,
    authorLetter = authorLetter,
    authorNumber = authorNumber,
    workMark = workMark,
    volume = volume,
    copyNumber = copyNumber,
    publicationYear = publicationYear,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

