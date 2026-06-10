package com.santoso.tech.data.local.dao

import androidx.room.*
import com.santoso.tech.data.local.entity.NewsBookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsBookmarkDao {

    @Query("SELECT * FROM news_bookmarks ORDER BY savedAt DESC")
    fun getAllBookmarks(): Flow<List<NewsBookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM news_bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: NewsBookmark)

    @Query("DELETE FROM news_bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String)
}
