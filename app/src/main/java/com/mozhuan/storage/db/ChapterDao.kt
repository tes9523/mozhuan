package com.mozhuan.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun observeByBook(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    suspend fun getByBook(bookId: String): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM chapters WHERE bookId = :bookId")
    suspend fun maxOrderIndex(bookId: String): Int

    @Query("UPDATE chapters SET wordCount = :wordCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStats(id: String, wordCount: Int, updatedAt: Long)

    @Query("UPDATE chapters SET fileUri = :fileUri WHERE id = :id")
    suspend fun updateFileUri(id: String, fileUri: String)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
