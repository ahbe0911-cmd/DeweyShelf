package ir.deweyshelf.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.deweyshelf.app.domain.DeweyBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeweyDatabaseTest {
    private lateinit var database: DeweyDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DeweyDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertUpdateDeleteRoundTrip() = runBlocking {
        val dao = database.bookDao()
        val id = dao.save(
            DeweyBook(
                title = "جغرافیای ایران",
                mainClass = 915,
                decimalPart = "694",
                authorLetter = "ب",
                authorNumber = "52",
            ).toEntity(),
        )

        var saved = dao.observeAll().first().single()
        assertEquals(id, saved.id)
        assertEquals("جغرافیای ایران", saved.title)

        dao.save(saved.copy(title = "جغرافیای نو"))
        saved = dao.observeAll().first().single()
        assertEquals("جغرافیای نو", saved.title)

        dao.delete(saved)
        assertEquals(0, dao.observeAll().first().size)
    }
}

