package com.santoso.tech.data.repository

import android.util.Log
import com.santoso.tech.BuildConfig
import com.santoso.tech.data.api.GNewsApiService
import com.santoso.tech.data.api.MediaStackApiService
import com.santoso.tech.data.api.NewsApiService
import com.santoso.tech.data.api.NewsDataApiService
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
    LATEST("Latest",        "cryptocurrency OR crypto OR bitcoin"),
    BITCOIN("Bitcoin",      "bitcoin OR BTC"),
    ETHEREUM("Ethereum",    "ethereum OR ETH"),
    ALTCOIN("Altcoin",      "altcoin OR solana OR cardano OR XRP"),
    MARKET("Market",        "crypto market OR cryptocurrency price"),
    REGULATION("Regulation","crypto regulation OR SEC cryptocurrency"),
    DEFI("DeFi",            "defi OR decentralized finance OR uniswap"),
    EXCHANGE("Exchange",    "crypto exchange OR binance OR coinbase OR OKX")
}

@Singleton
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService,
    private val gNewsApiService: GNewsApiService,
    private val newsDataApiService: NewsDataApiService,
    private val mediaStackApiService: MediaStackApiService,
    private val newsBookmarkDao: NewsBookmarkDao
) {
    companion object {
        private const val TAG = "NewsRepository"
        private val ISO_Z   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        private val ISO_TZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        private val ISO_MS  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        private val NEWSDATA_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
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
                url        = article.url,
                title      = article.title,
                description = article.description,
                imageUrl   = article.imageUrl,
                sourceName = article.sourceName,
                publishedAt = article.publishedAt,
                category   = article.category
            )
        )
    }

    suspend fun removeBookmark(url: String) = newsBookmarkDao.deleteBookmark(url)

    // ─── Fetch & Aggregate ────────────────────────────────────────────────────

    fun getNews(category: NewsCategory, searchQuery: String = ""): Flow<Result<List<NewsArticle>>> = flow {
        val query = if (searchQuery.isNotBlank()) searchQuery else category.keywords

        val allArticles = mutableListOf<NewsArticle>()

        coroutineScope {
            // 1. CoinDesk RSS (always)
            val rss1 = async { fetchCoinDeskRss() }

            // 2. NewsAPI.org
            val newsApi = if (BuildConfig.NEWS_API_KEY.isNotBlank())
                async { fetchFromNewsApi(query) } else null

            // 3. GNews.io
            val gNews = if (BuildConfig.GNEWS_API_KEY.isNotBlank())
                async { fetchFromGNews(query) } else null

            // 4. NewsData.io
            val newsData = if (BuildConfig.NEWSDATA_API_KEY.isNotBlank())
                async { fetchFromNewsData(query) } else null

            // 5. MediaStack
            val mediaStack = if (BuildConfig.MEDIASTACK_API_KEY.isNotBlank())
                async { fetchFromMediaStack(query) } else null

            allArticles.addAll(rss1.await())
            newsApi?.await()?.let { allArticles.addAll(it) }
            gNews?.await()?.let { allArticles.addAll(it) }
            newsData?.await()?.let { allArticles.addAll(it) }
            mediaStack?.await()?.let { allArticles.addAll(it) }
        }

        // Filter by search query if provided
        val filtered = if (searchQuery.isNotBlank()) {
            allArticles.filter { a ->
                a.title.contains(searchQuery, ignoreCase = true) ||
                a.description?.contains(searchQuery, ignoreCase = true) == true ||
                a.sourceName.contains(searchQuery, ignoreCase = true)
            }
        } else allArticles

        val result = filtered
            .distinctBy { it.url }
            .filter { it.title.isNotBlank() && it.url.isNotBlank() && it.title != "[Removed]" }
            .sortedByDescending { it.publishedAt }
            .map { it.copy(category = category.label) }

        emit(Result.success(result))
    }

    // ─── Source 1: CoinDesk RSS ───────────────────────────────────────────────

    private suspend fun fetchCoinDeskRss(): List<NewsArticle> {
        val urls = listOf(
            "https://www.coindesk.com/arc/outboundfeeds/rss/",
            "https://feeds.feedburner.com/CoinDesk"
        )
        for (rssUrl in urls) {
            try {
                val conn = URL(rssUrl).openConnection().apply {
                    connectTimeout = 8000
                    readTimeout    = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0 CryptoInfoApp/1.0")
                }
                val xml = conn.getInputStream().bufferedReader().readText()
                val articles = parseCoinDeskRss(xml)
                if (articles.isNotEmpty()) return articles
            } catch (e: Exception) {
                Log.w(TAG, "RSS $rssUrl failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseCoinDeskRss(xml: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val itemRgx    = Regex("<item>(.*?)</item>",      RegexOption.DOT_MATCHES_ALL)
        val titleRgx   = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>")
        val linkRgx    = Regex("<link>(.*?)</link>")
        val descRgx    = Regex("<description>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</description>", RegexOption.DOT_MATCHES_ALL)
        val dateRgx    = Regex("<pubDate>(.*?)</pubDate>")
        val mediaRgx   = Regex("<media:content[^>]+url=\"([^\"]+)\"")
        val enclosRgx  = Regex("<enclosure[^>]+url=\"([^\"]+)\"")

        itemRgx.findAll(xml).forEach { m ->
            val item  = m.groupValues[1]
            val title = titleRgx.find(item)?.groupValues?.get(1)?.cleanHtml() ?: return@forEach
            val link  = linkRgx.find(item)?.groupValues?.get(1)?.trim() ?: return@forEach
            val desc  = descRgx.find(item)?.groupValues?.get(1)?.cleanHtml()?.take(250)
            val date  = dateRgx.find(item)?.groupValues?.get(1)?.trim()
            val img   = mediaRgx.find(item)?.groupValues?.get(1)
                        ?: enclosRgx.find(item)?.groupValues?.get(1)
            articles.add(NewsArticle(
                id = link.sha256(), title = title, description = desc,
                url = link, imageUrl = img, sourceName = "CoinDesk",
                publishedAt = parseRssDate(date)
            ))
        }
        return articles
    }

    // ─── Source 2: NewsAPI.org ────────────────────────────────────────────────

    private suspend fun fetchFromNewsApi(query: String): List<NewsArticle> = try {
        newsApiService.getEverything(query = query, apiKey = BuildConfig.NEWS_API_KEY)
            .articles.mapNotNull { a ->
                if (a.url.isBlank() || a.title.isBlank()) return@mapNotNull null
                NewsArticle(
                    id = a.url.sha256(), title = a.title, description = a.description,
                    url = a.url, imageUrl = a.urlToImage,
                    sourceName = a.source.name.ifBlank { "NewsAPI" },
                    publishedAt = parseIsoDate(a.publishedAt)
                )
            }
    } catch (e: Exception) { Log.w(TAG, "NewsAPI: ${e.message}"); emptyList() }

    // ─── Source 3: GNews.io ───────────────────────────────────────────────────

    private suspend fun fetchFromGNews(query: String): List<NewsArticle> = try {
        gNewsApiService.searchNews(query = query, token = BuildConfig.GNEWS_API_KEY)
            .articles.mapNotNull { a ->
                if (a.url.isBlank() || a.title.isBlank()) return@mapNotNull null
                NewsArticle(
                    id = a.url.sha256(), title = a.title, description = a.description,
                    url = a.url, imageUrl = a.image,
                    sourceName = a.source.name.ifBlank { "GNews" },
                    publishedAt = parseIsoDate(a.publishedAt)
                )
            }
    } catch (e: Exception) { Log.w(TAG, "GNews: ${e.message}"); emptyList() }

    // ─── Source 4: NewsData.io ────────────────────────────────────────────────

    private suspend fun fetchFromNewsData(query: String): List<NewsArticle> = try {
        newsDataApiService.getNews(query = query.take(512), apiKey = BuildConfig.NEWSDATA_API_KEY)
            .results.mapNotNull { a ->
                if (a.link.isBlank() || a.title.isBlank()) return@mapNotNull null
                NewsArticle(
                    id = a.link.sha256(), title = a.title, description = a.description,
                    url = a.link, imageUrl = a.imageUrl,
                    sourceName = (a.sourceName ?: a.sourceId).ifBlank { "NewsData" },
                    publishedAt = parseNewsDataDate(a.pubDate)
                )
            }
    } catch (e: Exception) { Log.w(TAG, "NewsData: ${e.message}"); emptyList() }

    // ─── Source 5: MediaStack ─────────────────────────────────────────────────

    private suspend fun fetchFromMediaStack(query: String): List<NewsArticle> = try {
        // MediaStack free plan requires http (not https)
        val simpleQuery = query.split(" OR ").firstOrNull()?.trim() ?: "crypto"
        mediaStackApiService.getNews(
            keywords  = simpleQuery,
            accessKey = BuildConfig.MEDIASTACK_API_KEY
        ).data.mapNotNull { a ->
            if (a.url.isBlank() || a.title.isBlank()) return@mapNotNull null
            NewsArticle(
                id = a.url.sha256(), title = a.title, description = a.description,
                url = a.url, imageUrl = a.image,
                sourceName = a.source.ifBlank { "MediaStack" },
                publishedAt = parseIsoDate(a.publishedAt)
            )
        }
    } catch (e: Exception) { Log.w(TAG, "MediaStack: ${e.message}"); emptyList() }

    // ─── Date Parsers ─────────────────────────────────────────────────────────

    private fun parseIsoDate(s: String): Long {
        if (s.isBlank()) return System.currentTimeMillis()
        return listOf(ISO_Z, ISO_MS, ISO_TZ).firstNotNullOfOrNull { fmt ->
            try { fmt.parse(s)?.time } catch (e: Exception) { null }
        } ?: System.currentTimeMillis()
    }

    private fun parseRssDate(s: String?): Long {
        if (s.isNullOrBlank()) return System.currentTimeMillis()
        return listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        ).firstNotNullOfOrNull { fmt ->
            try { fmt.parse(s)?.time } catch (e: Exception) { null }
        } ?: System.currentTimeMillis()
    }

    private fun parseNewsDataDate(s: String): Long {
        if (s.isBlank()) return System.currentTimeMillis()
        return try { NEWSDATA_FMT.parse(s)?.time ?: System.currentTimeMillis() }
        catch (e: Exception) { parseIsoDate(s) }
    }

    // ─── String helpers ───────────────────────────────────────────────────────

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun String.cleanHtml(): String = this
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'")
        .trim()

    private fun NewsBookmark.toArticle() = NewsArticle(
        id = url.sha256(), title = title, description = description,
        url = url, imageUrl = imageUrl, sourceName = sourceName,
        publishedAt = publishedAt, category = category, isBookmarked = true
    )
}
