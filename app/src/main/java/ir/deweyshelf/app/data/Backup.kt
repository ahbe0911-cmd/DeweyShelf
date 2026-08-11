package ir.deweyshelf.app.data

import android.content.ContentResolver
import android.net.Uri
import ir.deweyshelf.app.domain.DeweyBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupCodec {
    private const val SchemaVersion = 1

    fun encode(books: List<DeweyBook>): String {
        val items = JSONArray()
        books.forEach { book ->
            items.put(JSONObject().apply {
                put("title", book.title)
                put("mainClass", book.mainClass)
                put("decimalPart", book.decimalPart)
                put("authorLetter", book.authorLetter)
                put("authorNumber", book.authorNumber)
                put("workMark", book.workMark)
                put("volume", book.volume ?: JSONObject.NULL)
                put("copyNumber", book.copyNumber ?: JSONObject.NULL)
                put("publicationYear", book.publicationYear ?: JSONObject.NULL)
                put("createdAt", book.createdAt)
                put("updatedAt", book.updatedAt)
            })
        }
        return JSONObject().apply {
            put("schemaVersion", SchemaVersion)
            put("generatedAt", System.currentTimeMillis())
            put("books", items)
        }.toString(2)
    }

    fun decode(raw: String): List<DeweyBook> {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == SchemaVersion)
        val books = root.getJSONArray("books")
        return buildList {
            repeat(books.length()) { index ->
                val item = books.getJSONObject(index)
                val title = item.getString("title").trim()
                val mainClass = item.getInt("mainClass")
                val authorLetter = item.getString("authorLetter").trim()
                val authorNumber = item.getString("authorNumber").trim()
                require(title.isNotEmpty() && mainClass in 0..999 && authorLetter.isNotEmpty() && authorNumber.isNotEmpty())
                add(
                    DeweyBook(
                        title = title,
                        mainClass = mainClass,
                        decimalPart = item.optString("decimalPart"),
                        authorLetter = authorLetter,
                        authorNumber = authorNumber,
                        workMark = item.optString("workMark"),
                        volume = item.optionalPositiveInt("volume"),
                        copyNumber = item.optionalPositiveInt("copyNumber"),
                        publicationYear = item.optionalPositiveInt("publicationYear"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun JSONObject.optionalPositiveInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }
}

object BackupFileStore {
    suspend fun write(contentResolver: ContentResolver, uri: Uri, content: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(content) }
                    ?: error("Output stream is unavailable")
            }.isSuccess
        }

    suspend fun read(contentResolver: ContentResolver, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Input stream is unavailable")
            }.getOrNull()
        }
}

