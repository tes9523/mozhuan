package com.mozhuan.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query(
        """
        SELECT b.id, b.title, b.folderUri, b.createdAt, b.updatedAt,
               (SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id) AS chapterCount
        FROM books b
        ORDER BY b.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<BookSummaryRow>>

    @Query("UPDATE books SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)
}
