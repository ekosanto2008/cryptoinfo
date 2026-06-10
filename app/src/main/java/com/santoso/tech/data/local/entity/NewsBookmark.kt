package com.santoso.tech.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_bookmarks")
data class NewsBookmark(
    @PrimaryKey val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val sourceName: String,
    val publishedAt: Long,
    val category: String,
    val savedAt: Long = System.currentTimeMillis()
)
