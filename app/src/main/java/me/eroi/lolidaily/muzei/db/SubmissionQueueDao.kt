package me.eroi.lolidaily.muzei.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionQueueDao {
    @Insert
    suspend fun insert(entity: SubmissionQueueEntity): Long

    @Delete
    suspend fun delete(entity: SubmissionQueueEntity)

    @Query(
        "SELECT * FROM submission_queue " +
            "WHERE owner_username = :ownerUsername ORDER BY id ASC",
    )
    fun observeByOwner(ownerUsername: String): Flow<List<SubmissionQueueEntity>>

    @Query("SELECT * FROM submission_queue ORDER BY id ASC")
    suspend fun getAll(): List<SubmissionQueueEntity>

    @Query("SELECT * FROM submission_queue WHERE published_date < :date ORDER BY id ASC")
    suspend fun getPublishedBefore(date: String): List<SubmissionQueueEntity>

    @Query("DELETE FROM submission_queue WHERE published_date < :date")
    suspend fun deletePublishedBefore(date: String)

    @Query(
        "UPDATE submission_queue SET published_date = :date " +
            "WHERE owner_username = :ownerUsername AND queue_group = :queueGroup " +
            "AND id <= :cutoffId AND published_date IS NULL",
    )
    suspend fun markPublishedThrough(
        ownerUsername: String,
        queueGroup: String,
        cutoffId: Long,
        date: String,
    )
}
