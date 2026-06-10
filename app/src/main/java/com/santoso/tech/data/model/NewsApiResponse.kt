package com.santoso.tech.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── NewsAPI.org response ────────────────────────────────────────────────────

@Serializable
data class NewsApiResponse(
    @SerialName("status") val status: String = "",
    @SerialName("totalResults") val totalResults: Int = 0,
    @SerialName("articles") val articles: List<NewsApiArticle> = emptyList()
)

@Serializable
data class NewsApiArticle(
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("url") val url: String = "",
    @SerialName("urlToImage") val urlToImage: String? = null,
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("source") val source: NewsApiSource = NewsApiSource()
)

@Serializable
data class NewsApiSource(
    @SerialName("name") val name: String = ""
)

// ─── GNews.io response ───────────────────────────────────────────────────────

@Serializable
data class GNewsResponse(
    @SerialName("totalArticles") val totalArticles: Int = 0,
    @SerialName("articles") val articles: List<GNewsArticle> = emptyList()
)

@Serializable
data class GNewsArticle(
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("url") val url: String = "",
    @SerialName("image") val image: String? = null,
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("source") val source: GNewsSource = GNewsSource()
)

@Serializable
data class GNewsSource(
    @SerialName("name") val name: String = ""
)

// ─── NewsData.io response ─────────────────────────────────────────────────────

@Serializable
data class NewsDataResponse(
    @SerialName("status") val status: String = "",
    @SerialName("totalResults") val totalResults: Int = 0,
    @SerialName("results") val results: List<NewsDataArticle> = emptyList()
)

@Serializable
data class NewsDataArticle(
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("link") val link: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("pubDate") val pubDate: String = "",
    @SerialName("source_id") val sourceId: String = "",
    @SerialName("source_name") val sourceName: String? = null
)

// ─── MediaStack response ──────────────────────────────────────────────────────

@Serializable
data class MediaStackResponse(
    @SerialName("data") val data: List<MediaStackArticle> = emptyList()
)

@Serializable
data class MediaStackArticle(
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("url") val url: String = "",
    @SerialName("image") val image: String? = null,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("source") val source: String = ""
)
