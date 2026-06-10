package com.santoso.tech.data.api

import com.santoso.tech.data.model.GNewsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GNews.io — requires free API key from https://gnews.io
 * Free plan: 100 requests/day.
 */
interface GNewsApiService {

    @GET("v4/search")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("lang") lang: String = "en",
        @Query("country") country: String = "any",
        @Query("max") max: Int = 20,
        @Query("sortby") sortBy: String = "publishedAt",
        @Query("token") token: String
    ): GNewsResponse

    @GET("v4/top-headlines")
    suspend fun getTopHeadlines(
        @Query("topic") topic: String = "business",
        @Query("lang") lang: String = "en",
        @Query("max") max: Int = 20,
        @Query("token") token: String
    ): GNewsResponse
}
