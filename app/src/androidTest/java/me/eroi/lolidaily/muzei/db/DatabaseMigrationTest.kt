package me.eroi.lolidaily.muzei.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migrate3To4PreservesArtworkAndCreatesSubmissionQueue() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                """
                INSERT INTO cached_artworks (
                    token, artist_name, source_url, artist_url, comment, tags, characters,
                    suggested_by_nickname, suggested_by_username, date, downloaded_at, bookmarked
                ) VALUES (
                    'token-1', 'Artist', 'https://example.com/source',
                    'https://example.com/artist', 'Comment', 'LC0', '[]',
                    NULL, NULL, '2026-08-27', 1234, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                4,
                true,
                DatabaseProvider.MIGRATION_3_4,
            ).use { database ->
                database.query("SELECT artist_name, bookmarked FROM cached_artworks WHERE token = 'token-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Artist", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                }

                database.execSQL(
                    """
                    INSERT INTO submission_queue (
                        owner_username, queue_group, tag, source_url, source_key,
                        artist_name, artist_url, characters, comment, anonymous,
                        image_file_name, submitted_at
                    ) VALUES (
                        'alice', 'general', 'LC0', 'https://example.com/source',
                        'url:https://example.com/source', 'Artist', 'https://example.com/artist',
                        '[]', 'Comment', 0, 'queue.jpg', 5678
                    )
                    """.trimIndent(),
                )
                database.query("SELECT COUNT(*) FROM submission_queue").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
    }

    private companion object {
        const val TEST_DATABASE = "room-migration-test"
    }
}
