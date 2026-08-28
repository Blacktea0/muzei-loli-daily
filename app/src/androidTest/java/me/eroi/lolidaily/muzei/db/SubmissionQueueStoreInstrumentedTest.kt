package me.eroi.lolidaily.muzei.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.eroi.lolidaily.muzei.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubmissionQueueStoreInstrumentedTest {
    @Test
    fun publishedSubmissionClearsOnlyItsQueueThroughMatchedEntry() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val owner = "queue-test-${System.nanoTime()}"

            record(context, owner, "LC0", "https://example.com/general-1")
            record(context, owner, "LC ES", "https://example.com/es-1")
            record(context, owner, "LC YJ", "https://example.com/general-2")

            val recorded = SubmissionQueueStore.observe(context, owner).first()
            assertEquals(3, recorded.size)
            val generalFiles =
                recorded
                    .filter { it.queueGroup == SUBMISSION_QUEUE_GENERAL }
                    .map { SubmissionQueueStore.imageFile(context, it) }
            val esEntry = recorded.single { it.queueGroup == SUBMISSION_QUEUE_ES }
            val esFile = SubmissionQueueStore.imageFile(context, esEntry)
            assertTrue(generalFiles.all { it.exists() })
            assertTrue(esFile.exists())

            SubmissionQueueStore.reconcilePublishedSubmissions(
                context,
                listOf(
                    Card(
                        imgUrl = "https://example.com/image/general-2",
                        sourceUrl = "https://example.com/general-2",
                        tags = "LC0",
                    ),
                ),
            )

            val afterGeneralPublication = SubmissionQueueStore.observe(context, owner).first()
            assertEquals(listOf(SUBMISSION_QUEUE_ES), afterGeneralPublication.map { it.queueGroup })
            assertTrue(generalFiles.none { it.exists() })
            assertTrue(esFile.exists())

            SubmissionQueueStore.reconcilePublishedSubmissions(
                context,
                listOf(
                    Card(
                        imgUrl = "https://example.com/image/es-1",
                        sourceUrl = "https://example.com/es-1",
                        tags = "LC ES",
                    ),
                ),
            )

            assertTrue(SubmissionQueueStore.observe(context, owner).first().isEmpty())
            assertFalse(esFile.exists())
        }

    private suspend fun record(
        context: android.content.Context,
        owner: String,
        tag: String,
        sourceUrl: String,
    ) {
        SubmissionQueueStore
            .recordSuccessfulSubmission(
                context = context,
                ownerUsername = owner,
                tag = tag,
                sourceUrl = sourceUrl,
                imageBytes = byteArrayOf(1, 2, 3),
                imageMimeType = "image/jpeg",
            ).getOrThrow()
    }
}
