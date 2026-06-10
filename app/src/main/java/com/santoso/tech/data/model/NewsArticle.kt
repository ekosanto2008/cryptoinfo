package com.santoso.tech.data.model

data class NewsArticle(
    val id: String,              // SHA-256 hash dari URL
    val title: String,
    val description: String?,
    val url: String,
    val imageUrl: String?,
    val sourceName: String,
    val publishedAt: Long,       // epoch millis
    val category: String = "Latest",
    val isBookmarked: Boolean = false
)
