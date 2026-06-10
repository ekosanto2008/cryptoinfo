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
