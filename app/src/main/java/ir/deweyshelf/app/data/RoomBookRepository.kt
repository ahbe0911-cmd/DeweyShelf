package ir.deweyshelf.app.data

import androidx.room.withTransaction
import ir.deweyshelf.app.domain.BookRepository
import ir.deweyshelf.app.domain.DeweyBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBookRepository(
    private val database: DeweyDatabase,
) : BookRepository {
    private val dao = database.bookDao()

    override fun observeBooks(): Flow<List<DeweyBook>> =
        dao.observeAll().map { entities -> entities.map(BookEntity::toDomain) }

    override suspend fun save(book: DeweyBook): Long = dao.save(book.toEntity())

    override suspend fun delete(book: DeweyBook) = dao.delete(book.toEntity())

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun restore(books: List<DeweyBook>) {
        dao.saveAll(books.map(DeweyBook::toEntity))
    }

    override suspend fun replaceAll(books: List<DeweyBook>) {
        database.withTransaction {
            dao.deleteAll()
            dao.saveAll(books.map { it.copy(id = 0).toEntity() })
        }
    }
}

