package com.santoso.tech.data.api

import com.santoso.tech.data.model.MediaStackResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Mediastack — requires API key from https://mediastack.com
 * Free plan: 100 calls/month. Best for supplemental coverage.
 */
interface MediaStackApiService {

    @GET("v1/news")
    suspend fun getNews(
        @Query("keywords") keywords: String,
        @Query("languages") languages: String = "en",
        @Query("categories") categories: String = "business,science,technology",
        @Query("sort") sort: String = "published_desc",
        @Query("limit") limit: Int = 20,
        @Query("access_key") accessKey: String
    ): MediaStackResponse
}
