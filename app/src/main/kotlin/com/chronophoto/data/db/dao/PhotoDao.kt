package com.chronophoto.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronophoto.data.db.entity.Photo
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY sequence ASC")
    fun observeByProject(projectId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY sequence DESC LIMIT 1")
    suspend fun getLatest(projectId: Long): Photo?

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY sequence DESC LIMIT 1")
    fun observeLatest(projectId: Long): Flow<Photo?>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): Photo?

    @Query("SELECT * FROM photos WHERE id = :id")
    fun observeById(id: Long): Flow<Photo?>

    @Query("SELECT * FROM photos WHERE projectId = :projectId AND sequence < :sequence ORDER BY sequence DESC LIMIT 1")
    suspend fun getPrevious(projectId: Long, sequence: Int): Photo?

    @Query("SELECT * FROM photos WHERE projectId = :projectId AND sequence < :sequence ORDER BY sequence DESC LIMIT 1")
    fun observePrevious(projectId: Long, sequence: Int): Flow<Photo?>

    @Query("SELECT COUNT(*) FROM photos WHERE projectId = :projectId")
    suspend fun countForProject(projectId: Long): Int

    @Query("SELECT COALESCE(MAX(sequence), -1) FROM photos WHERE projectId = :projectId")
    suspend fun maxSequence(projectId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: Photo): Long

    @Delete
    suspend fun delete(photo: Photo)

    @Query("DELETE FROM photos WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: Long)
}
