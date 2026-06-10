package com.santoso.tech.data.repository

import android.util.Log
import com.santoso.tech.BuildConfig
import com.santoso.tech.data.api.GNewsApiService
import com.santoso.tech.data.api.NewsApiService
import com.santoso.tech.data.local.dao.NewsBookmarkDao
import com.santoso.tech.data.local.entity.NewsBookmark
import com.santoso.tech.data.model.NewsArticle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

enum class NewsCategory(val label: String, val keywords: String) {
    LATEST("Latest", "cryptocurrency OR crypto OR bitcoin"),
    BITCOIN("Bitcoin", "bitcoin OR BTC"),
    ETHEREUM("Ethereum", "ethereum OR ETH"),
    ALTCOIN("Altcoin", "altcoin OR altcoins OR solana OR cardano"),
    MARKET("Market", "crypto market OR crypto price OR cryptocurrency market"),
    REGULATION("Regulation", "crypto regulation OR SEC cryptocurrency OR crypto law"),
    DEFI("DeFi", "defi OR decentralized finance OR uniswap OR aave"),
    EXCHANGE("Exchange", "crypto exchange OR binance OR coinbase OR OKX")
}

@Singleton
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService,
    private val gNewsApiService: GNewsApiService,
    private val newsBookmarkDao: NewsBookmarkDao
) {

    companion object {
        private const val TAG = "NewsRepository"
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val ISO_FORMAT_WITH_TZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }

    // ─── Bookmark Operations ──────────────────────────────────────────────────

    fun getBookmarks(): Flow<List<NewsArticle>> = flow {
        newsBookmarkDao.getAllBookmarks().collect { bookmarks ->
            emit(bookmarks.map { it.toArticle() })
        }
    }

    fun isBookmarked(url: String): Flow<Boolean> = newsBookmarkDao.isBookmarked(url)

    suspend fun addBookmark(article: NewsArticle) {
        newsBookmarkDao.insertBookmark(
            NewsBookmark(
                url = article.url,
                title = article.title,
                description = article.description,
                imageUrl = article.imageUrl,
                sourceName = article.sourceName,
                publishedAt = article.publishedAt,
                category = article.category
            )
        )
    }

    suspend fun removeBookmark(url: String) {
        newsBookmarkDao.deleteBookmark(url)
    }

    // ─── Fetch News ───────────────────────────────────────────────────────────

    fun getNews(category: NewsCategory, searchQuery: String = ""): Flow<Result<List<NewsArticle>>> = flow {
        val query = if (searchQuery.isNotBlank()) searchQuery else category.keywords

        val allArticles = mutableListOf<NewsArticle>()

        coroutineScope {
            // Source 1: CoinDesk RSS (always available, no key)
            val rssDeferred = async { fetchCoinDeskRss() }

            // Source 2: NewsAPI (if key available)
            val newsApiDeferred = if (BuildConfig.NEWS_API_KEY.isNotBlank()) {
                async { fetchFromNewsApi(query) }
            } else null

            // Source 3: GNews (if key available)
            val gNewsDeferred = if (BuildConfig.GNEWS_API_KEY.isNotBlank()) {
                async { fetchFromGNews(query) }
            } else null

            val rssArticles = rssDeferred.await()
            allArticles.addAll(rssArticles)

            newsApiDeferred?.await()?.let { allArticles.addAll(it) }
            gNewsDeferred?.await()?.let { allArticles.addAll(it) }
        }

        // Filter by search query if provided
        val filtered = if (searchQuery.isNotBlank()) {
            allArticles.filter { article ->
                article.title.contains(searchQuery, ignoreCase = true) ||
                article.description?.contains(searchQuery, ignoreCase = true) == true ||
                article.sourceName.contains(searchQuery, ignoreCase = true)
            }
        } else {
            allArticles
        }

        // Dedup by URL, sort by date desc
        val result = filtered
            .distinctBy { it.url }
            .filter { it.title.isNotBlank() && it.url.isNotBlank() && it.title != "[Removed]" }
            .sortedByDescending { it.publishedAt }
            .map { it.copy(category = category.label) }

        emit(Result.success(result))
    }

    // ─── CoinDesk RSS ─────────────────────────────────────────────────────────

    private suspend fun fetchCoinDeskRss(): List<NewsArticle> {
        return try {
            val url = URL("https://feeds.feedburner.com/CoinDesk")
            val connection = url.openConnection()
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val xml = connection.getInputStream().bufferedReader().readText()
            parseCoinDeskRss(xml)
        } catch (e: Exception) {
            Log.w(TAG, "CoinDesk RSS failed: ${e.message}")
            // Fallback to CoinDesk direct RSS
            try {
                val url = URL("https://www.coindesk.com/arc/outboundfeeds/rss/")
                val connection = url.openConnection().apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val xml = connection.getInputStream().bufferedReader().readText()
                parseCoinDeskRss(xml)
            } catch (e2: Exception) {
                Log.w(TAG, "CoinDesk fallback RSS also failed: ${e2.message}")
                emptyList()
            }
        }
    }

    private fun parseCoinDeskRss(xml: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        try {
            // Simple regex-based XML parser for RSS (no extra library needed)
            val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
            val titleRegex = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>")
            val linkRegex = Regex("<link>(.*?)</link>")
            val descRegex = Regex("<description>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</description>", RegexOption.DOT_MATCHES_ALL)
            val pubDateRegex = Regex("<pubDate>(.*?)</pubDate>")
            val mediaRegex = Regex("<media:content[^>]+url=\"([^\"]+)\"")
            val enclosureRegex = Regex("<enclosure[^>]+url=\"([^\"]+)\"")

            itemRegex.findAll(xml).forEach { itemMatch ->
                val item = itemMatch.groupValues[1]
                val title = titleRegex.find(item)?.groupValues?.get(1)?.trim()
                    ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">") ?: return@forEach
                val link = linkRegex.find(item)?.groupValues?.get(1)?.trim() ?: return@forEach
                val desc = descRegex.find(item)?.groupValues?.get(1)?.trim()
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")
                    ?.take(200)
                val pubDate = pubDateRegex.find(item)?.groupValues?.get(1)?.trim()
                val imageUrl = mediaRegex.find(item)?.groupValues?.get(1)
                    ?: enclosureRegex.find(item)?.groupValues?.get(1)

                val publishedAt = parseRssDate(pubDate)

                articles.add(
                    NewsArticle(
                        id = link.sha256(),
                        title = title,
                        description = desc,
                        url = link,
                        imageUrl = imageUrl,
                        sourceName = "CoinDesk",
                        publishedAt = publishedAt
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "RSS parse error: ${e.message}")
        }
        return articles
    }

    // ─── NewsAPI ──────────────────────────────────────────────────────────────

    private suspend fun fetchFromNewsApi(query: String): List<NewsArticle> {
        return try {
            val response = newsApiService.getEverything(
                query = query,
                apiKey = BuildConfig.NEWS_API_KEY
            )
            response.articles.mapNotNull { article ->
                if (article.url.isBlank() || article.title.isBlank()) return@mapNotNull null
                NewsArticle(
                    id = article.url.sha256(),
                    title = article.title,
                    description = article.description,
                    url = article.url,
                    imageUrl = article.urlToImage,
                    sourceName = article.source.name.ifBlank { "NewsAPI" },
                    publishedAt = parseIsoDate(article.publishedAt)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewsAPI failed: ${e.message}")
            emptyList()
        }
    }

    // ─── GNews ────────────────────────────────────────────────────────────────

    private suspend fun fetchFromGNews(query: String): List<NewsArticle> {
        return try {
            val response = gNewsApiService.searchNews(
                query = query,
                token = BuildConfig.GNEWS_API_KEY
            )
            response.articles.mapNotNull { article ->
                if (article.url.isBlank() || article.title.isBlank()) return@mapNotNull null
                NewsArticle(
                    id = article.url.sha256(),
                    title = article.title,
                    description = article.description,
                    url = article.url,
                    imageUrl = article.image,
                    sourceName = article.source.name.ifBlank { "GNews" },
                    publishedAt = parseIsoDate(article.publishedAt)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "GNews failed: ${e.message}")
            emptyList()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun parseIsoDate(dateStr: String): Long {
        return try {
            ISO_FORMAT.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                ISO_FORMAT_WITH_TZ.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun parseRssDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        val formats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        )
        return formats.firstNotNullOfOrNull { fmt ->
            try { fmt.parse(dateStr)?.time } catch (e: Exception) { null }
        } ?: System.currentTimeMillis()
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun NewsBookmark.toArticle() = NewsArticle(
        id = url.sha256(),
        title = title,
        description = description,
        url = url,
        imageUrl = imageUrl,
        sourceName = sourceName,
        publishedAt = publishedAt,
        category = category,
        isBookmarked = true
    )
}
