package me.eroi.lolidaily.muzei.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubmissionQueueStoreTest {
    @Test
    fun publishedEntryClearsOnlyItsQueueThroughThatEntry() {
        val entries =
            listOf(
                entry(id = 1, queueGroup = SUBMISSION_QUEUE_GENERAL, sourceKey = "general-1"),
                entry(id = 2, queueGroup = SUBMISSION_QUEUE_ES, sourceKey = "es-1"),
                entry(id = 3, queueGroup = SUBMISSION_QUEUE_GENERAL, sourceKey = "general-2"),
                entry(id = 4, queueGroup = SUBMISSION_QUEUE_ES, sourceKey = "es-2"),
                entry(id = 5, queueGroup = SUBMISSION_QUEUE_GENERAL, sourceKey = "general-3"),
            )

        val cutoffs =
            findSubmissionQueueCutoffs(
                entries = entries,
                published =
                    setOf(
                        PublishedSubmissionKey(
                            queueGroup = SUBMISSION_QUEUE_GENERAL,
                            sourceKey = "general-2",
                        ),
                    ),
            )

        assertEquals(3L, cutoffs[SubmissionQueueScope("alice", SUBMISSION_QUEUE_GENERAL)])
        assertFalse(cutoffs.containsKey(SubmissionQueueScope("alice", SUBMISSION_QUEUE_ES)))
    }

    @Test
    fun latestPublishedEntryWinsIndependentlyPerQueueAndOwner() {
        val entries =
            listOf(
                entry(id = 1, queueGroup = SUBMISSION_QUEUE_GENERAL, sourceKey = "general-1"),
                entry(id = 2, queueGroup = SUBMISSION_QUEUE_ES, sourceKey = "es-1"),
                entry(id = 3, queueGroup = SUBMISSION_QUEUE_GENERAL, sourceKey = "general-2"),
                entry(
                    id = 4,
                    ownerUsername = "bob",
                    queueGroup = SUBMISSION_QUEUE_GENERAL,
                    sourceKey = "bob-general",
                ),
                entry(id = 5, queueGroup = SUBMISSION_QUEUE_GENERAL, sourceKey = "general-3"),
            )
        val published =
            setOf(
                PublishedSubmissionKey(SUBMISSION_QUEUE_GENERAL, "general-1"),
                PublishedSubmissionKey(SUBMISSION_QUEUE_GENERAL, "general-3"),
                PublishedSubmissionKey(SUBMISSION_QUEUE_ES, "es-1"),
                PublishedSubmissionKey(SUBMISSION_QUEUE_GENERAL, "bob-general"),
            )

        val cutoffs = findSubmissionQueueCutoffs(entries, published)

        assertEquals(5L, cutoffs[SubmissionQueueScope("alice", SUBMISSION_QUEUE_GENERAL)])
        assertEquals(2L, cutoffs[SubmissionQueueScope("alice", SUBMISSION_QUEUE_ES)])
        assertEquals(4L, cutoffs[SubmissionQueueScope("bob", SUBMISSION_QUEUE_GENERAL)])
    }

    private fun entry(
        id: Long,
        ownerUsername: String = "alice",
        queueGroup: String,
        sourceKey: String,
    ) = SubmissionQueueEntity(
        id = id,
        ownerUsername = ownerUsername,
        queueGroup = queueGroup,
        tag = if (queueGroup == SUBMISSION_QUEUE_ES) "LC ES" else "LC0",
        sourceUrl = "https://example.com/$sourceKey",
        sourceKey = sourceKey,
        imageFileName = "$id.jpg",
        submittedAt = id,
    )
}
