package me.eroi.lolidaily.muzei.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.eroi.lolidaily.muzei.model.Card
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDateInstrumentedTest {
    @Test
    fun bookmarkDateSurvivesRefreshAndChangesOnlyAfterRemoval() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val dao = database.cachedArtworkDao()
                val initial =
                    EntityMapper.cardToEntity(
                        Card(imgUrl = "https://example.com/image", artistName = "Original"),
                        "token",
                        "2026-10-26",
                    ).copy(downloadedAt = 1234L)
                dao.upsert(initial)
                dao.setBookmarked("token", 1)
                assertEquals("2026-10-26", dao.getByToken("token")?.bookmarkDate)
                assertEquals(1234L, dao.getByToken("token")?.downloadedAt)
                assertEquals("2026-10-26", dao.getByToken("token")?.date)

                dao.setBookmarked("token", 1)
                assertEquals("2026-10-26", dao.getByToken("token")?.bookmarkDate)

                dao.refreshMetadata(listOf(initial.copy(artistName = "Updated", date = "2026-10-29", downloadedAt = 30000L)))
                val refreshed = requireNotNull(dao.getByToken("token"))
                assertEquals("Updated", refreshed.artistName)
                assertEquals(1, refreshed.bookmarked)
                assertEquals("2026-10-26", refreshed.bookmarkDate)
                assertEquals(30000L, refreshed.downloadedAt)
                assertEquals("2026-10-26", EntityMapper.entityToCardFields(refreshed).bookmarkDate)
                dao.setBookmarked("token", 1)
                assertEquals("2026-10-26", dao.getByToken("token")?.bookmarkDate)

                dao.setBookmarked("token", 0)
                assertEquals("", dao.getByToken("token")?.bookmarkDate)
                dao.refreshMetadata(listOf(initial.copy(bookmarked = 1, date = "2026-10-30", downloadedAt = 40000L)))
                assertEquals(0, dao.getByToken("token")?.bookmarked)
                assertEquals(40000L, dao.getByToken("token")?.downloadedAt)

                dao.setBookmarked("token", 1)
                assertEquals("2026-10-30", dao.getByToken("token")?.bookmarkDate)
                assertEquals(40000L, dao.getByToken("token")?.downloadedAt)
                assertEquals("2026-10-25", EntityMapper.cardToEntity(Card(imgUrl = "new"), "new", "2026-10-25", 1).bookmarkDate)
            } finally {
                database.close()
            }
        }
}
