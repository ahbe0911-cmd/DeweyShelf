package ir.deweyshelf.app.domain

import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<DeweyBook>>
    suspend fun save(book: DeweyBook): Long
    suspend fun delete(book: DeweyBook)
    suspend fun deleteAll()
    suspend fun restore(books: List<DeweyBook>)
    suspend fun replaceAll(books: List<DeweyBook>)
}

