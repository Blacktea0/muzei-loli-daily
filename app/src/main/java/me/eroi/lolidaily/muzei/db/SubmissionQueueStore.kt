package me.eroi.lolidaily.muzei.db

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import me.eroi.lolidaily.muzei.api.link.SourceLinkParserRegistry
import me.eroi.lolidaily.muzei.api.link.stripTrackingParams
import kotlinx.serialization.builtins.ListSerializer
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.SlimCharacter
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.util.Log

private const val TAG = "SubmissionQueueStore"
private const val IMAGE_DIRECTORY = "submission_queue"

internal data class PublishedSubmissionKey(
    val queueGroup: String,
    val sourceKey: String,
)

internal data class SubmissionQueueScope(
    val ownerUsername: String,
    val queueGroup: String,
)

internal fun submissionSourceKey(sourceUrl: String): String {
    val withoutTracking = stripTrackingParams(sourceUrl.trim())
    val canonical =
        runCatching { SourceLinkParserRegistry.canonicalUrl(withoutTracking) }
            .getOrNull()
            ?: withoutTracking
    val match = runCatching { SourceLinkParserRegistry.match(canonical) }.getOrNull()
    return if (match != null) {
        "${match.type}:${match.resourceId}"
    } else {
        "url:$canonical"
    }
}

internal fun findSubmissionQueueCutoffs(
    entries: List<SubmissionQueueEntity>,
    published: Set<PublishedSubmissionKey>,
): Map<SubmissionQueueScope, Long> {
    val cutoffs = mutableMapOf<SubmissionQueueScope, Long>()
    for (entry in entries) {
        val key = PublishedSubmissionKey(entry.queueGroup, entry.sourceKey)
        if (key !in published) continue

        val scope = SubmissionQueueScope(entry.ownerUsername, entry.queueGroup)
        val currentCutoff = cutoffs[scope]
        if (currentCutoff == null || entry.id > currentCutoff) {
            cutoffs[scope] = entry.id
        }
    }
    return cutoffs
}

object SubmissionQueueStore {
    fun observe(
        context: Context,
        ownerUsername: String,
    ): Flow<List<SubmissionQueueEntity>> =
        DatabaseProvider
            .getInstance(context.applicationContext)
            .submissionQueueDao()
            .observeByOwner(ownerUsername)

    fun imageFile(
        context: Context,
        entity: SubmissionQueueEntity,
    ): File = File(imageDirectory(context.applicationContext), entity.imageFileName)

    suspend fun recordSuccessfulSubmission(
        context: Context,
        ownerUsername: String,
        tag: String,
        sourceUrl: String,
        imageBytes: ByteArray,
        imageMimeType: String,
        artistName: String = "",
        artistUrl: String = "",
        characters: List<SlimCharacter> = emptyList(),
        comment: String = "",
        anonymous: Boolean = false,
        submittedAt: Long = System.currentTimeMillis(),
    ): Result<Unit> {
        val applicationContext = context.applicationContext
        val normalizedOwner = ownerUsername.trim()
        val normalizedSourceUrl = sourceUrl.trim()
        val queueGroup = submissionQueueGroupForTag(tag)

        if (normalizedOwner.isEmpty()) {
            return Result.failure(IllegalArgumentException("Missing submission owner"))
        }
        if (normalizedSourceUrl.isEmpty()) {
            return Result.failure(IllegalArgumentException("Missing submission source URL"))
        }
        if (queueGroup == null) {
            return Result.failure(IllegalArgumentException("Unsupported submission tag: $tag"))
        }

        val directory = imageDirectory(applicationContext)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.failure(IllegalStateException("Unable to create submission queue directory"))
        }

        val extension = extensionForMimeType(imageMimeType)
        val imageFileName = "${UUID.randomUUID()}.$extension"
        val temporaryFile = File(directory, ".$imageFileName.tmp")
        val targetFile = File(directory, imageFileName)

        return try {
            temporaryFile.outputStream().use { output ->
                output.write(imageBytes)
                output.fd.sync()
            }
            if (!temporaryFile.renameTo(targetFile)) {
                temporaryFile.copyTo(targetFile, overwrite = false)
                temporaryFile.delete()
            }

            DatabaseProvider
                .getInstance(applicationContext)
                .submissionQueueDao()
                .insert(
                    SubmissionQueueEntity(
                        ownerUsername = normalizedOwner,
                        queueGroup = queueGroup,
                        tag = tag,
                        sourceUrl = normalizedSourceUrl,
                        sourceKey = submissionSourceKey(normalizedSourceUrl),
                        artistName = artistName.trim(),
                        artistUrl = artistUrl.trim(),
                        characters =
                            LoliApiClient.json.encodeToString(
                                ListSerializer(SlimCharacter.serializer()),
                                characters,
                            ),
                        comment = comment.trim(),
                        anonymous = anonymous,
                        imageFileName = imageFileName,
                        submittedAt = submittedAt,
                    ),
                )
            Log.d(TAG, "Recorded successful submission in $queueGroup queue")
            Result.success(Unit)
        } catch (e: CancellationException) {
            temporaryFile.delete()
            targetFile.delete()
            throw e
        } catch (e: Exception) {
            temporaryFile.delete()
            targetFile.delete()
            Log.e(TAG, "Failed to record successful submission", e)
            Result.failure(e)
        }
    }

    suspend fun reconcilePublishedSubmissions(
        context: Context,
        cards: List<Card>,
    ) {
        if (cards.isEmpty()) return

        val published = HashSet<PublishedSubmissionKey>(cards.size)
        for (card in cards) {
            val queueGroup = submissionQueueGroupForTag(card.tags) ?: continue
            if (card.sourceUrl.isBlank()) continue
            published += PublishedSubmissionKey(queueGroup, submissionSourceKey(card.sourceUrl))
        }
        if (published.isEmpty()) return

        val applicationContext = context.applicationContext
        val database = DatabaseProvider.getInstance(applicationContext)
        val dao = database.submissionQueueDao()
        val removed =
            database.withTransaction {
                val cutoffs = findSubmissionQueueCutoffs(dao.getAll(), published)
                if (cutoffs.isEmpty()) return@withTransaction emptyList()

                buildList {
                    for ((scope, cutoffId) in cutoffs) {
                        addAll(dao.getThrough(scope.ownerUsername, scope.queueGroup, cutoffId))
                        dao.deleteThrough(scope.ownerUsername, scope.queueGroup, cutoffId)
                    }
                }
            }

        for (entry in removed) {
            val file = imageFile(applicationContext, entry)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete published queue image ${entry.imageFileName}")
            }
        }
        if (removed.isNotEmpty()) {
            Log.d(TAG, "Removed ${removed.size} published submissions from local queues")
        }
    }

    private fun imageDirectory(context: Context): File = File(context.filesDir, IMAGE_DIRECTORY)

    private fun extensionForMimeType(mimeType: String): String =
        when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            else -> "jpg"
        }
}
