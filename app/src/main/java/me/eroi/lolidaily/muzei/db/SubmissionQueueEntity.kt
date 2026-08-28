package me.eroi.lolidaily.muzei.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "submission_queue")
data class SubmissionQueueEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "queue_group") val queueGroup: String,
    @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "artist_name") val artistName: String = "",
    @ColumnInfo(name = "artist_url") val artistUrl: String = "",
    /** JSON-serialised list of [me.eroi.lolidaily.muzei.model.SlimCharacter]. */
    @ColumnInfo(name = "characters") val characters: String = "[]",
    @ColumnInfo(name = "comment") val comment: String = "",
    @ColumnInfo(name = "anonymous") val anonymous: Boolean = false,
    @ColumnInfo(name = "image_file_name") val imageFileName: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
)

internal const val SUBMISSION_QUEUE_GENERAL = "general"
internal const val SUBMISSION_QUEUE_ES = "es"

internal fun submissionQueueGroupForTag(tag: String): String? =
    when (tag) {
        "LC0", "LC YJ" -> SUBMISSION_QUEUE_GENERAL
        "LC ES" -> SUBMISSION_QUEUE_ES
        else -> null
    }
