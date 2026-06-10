package com.santoso.tech.data.api

import com.santoso.tech.data.model.NewsApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NewsAPI.org — requires free API key from https://newsapi.org
 * Supports up to 100 requests/day on free plan.
 */
interface NewsApiService {

    @GET("v2/everything")
    suspend fun getEverything(
        @Query("q") query: String,
        @Query("language") language: String = "en",
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("pageSize") pageSize: Int = 20,
        @Query("apiKey") apiKey: String
    ): NewsApiResponse

    @GET("v2/top-headlines")
    suspend fun getTopHeadlines(
        @Query("category") category: String = "business",
        @Query("q") query: String = "crypto",
        @Query("language") language: String = "en",
        @Query("pageSize") pageSize: Int = 20,
        @Query("apiKey") apiKey: String
    ): NewsApiResponse
}
