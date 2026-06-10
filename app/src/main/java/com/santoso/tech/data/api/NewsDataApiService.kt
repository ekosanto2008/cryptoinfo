package com.santoso.tech.data.api

import com.santoso.tech.data.model.NewsDataResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NewsData.io — requires API key from https://newsdata.io
 * Free plan: 200 credits/day.
 */
interface NewsDataApiService {

    @GET("api/1/news")
    suspend fun getNews(
        @Query("q") query: String,
        @Query("language") language: String = "en",
        @Query("category") category: String? = null,
        @Query("apikey") apiKey: String
    ): NewsDataResponse
}
