package me.eroi.lolidaily.muzei.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionQueueDao {
    @Insert
    suspend fun insert(entity: SubmissionQueueEntity): Long

    @Query(
        "SELECT * FROM submission_queue " +
            "WHERE owner_username = :ownerUsername ORDER BY id ASC",
    )
    fun observeByOwner(ownerUsername: String): Flow<List<SubmissionQueueEntity>>

    @Query("SELECT * FROM submission_queue ORDER BY id ASC")
    suspend fun getAll(): List<SubmissionQueueEntity>

    @Query(
        "SELECT * FROM submission_queue " +
            "WHERE owner_username = :ownerUsername AND queue_group = :queueGroup " +
            "AND id <= :cutoffId ORDER BY id ASC",
    )
    suspend fun getThrough(
        ownerUsername: String,
        queueGroup: String,
        cutoffId: Long,
    ): List<SubmissionQueueEntity>

    @Query(
        "DELETE FROM submission_queue " +
            "WHERE owner_username = :ownerUsername AND queue_group = :queueGroup " +
            "AND id <= :cutoffId",
    )
    suspend fun deleteThrough(
        ownerUsername: String,
        queueGroup: String,
        cutoffId: Long,
    )
}
