package com.santoso.tech.di

import com.santoso.tech.data.api.GNewsApiService
import com.santoso.tech.data.api.MediaStackApiService
import com.santoso.tech.data.api.NewsApiService
import com.santoso.tech.data.api.NewsDataApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NewsNetworkModule {

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    // ─── NewsAPI.org ──────────────────────────────────────────────────────────
    @Provides @Singleton @Named("newsApi")
    fun provideNewsApiRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        buildRetrofit("https://newsapi.org/", okHttpClient, json)

    @Provides @Singleton
    fun provideNewsApiService(@Named("newsApi") retrofit: Retrofit): NewsApiService =
        retrofit.create(NewsApiService::class.java)

    // ─── GNews.io ─────────────────────────────────────────────────────────────
    @Provides @Singleton @Named("gNews")
    fun provideGNewsRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        buildRetrofit("https://gnews.io/api/", okHttpClient, json)

    @Provides @Singleton
    fun provideGNewsApiService(@Named("gNews") retrofit: Retrofit): GNewsApiService =
        retrofit.create(GNewsApiService::class.java)

    // ─── NewsData.io ──────────────────────────────────────────────────────────
    @Provides @Singleton @Named("newsData")
    fun provideNewsDataRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        buildRetrofit("https://newsdata.io/", okHttpClient, json)

    @Provides @Singleton
    fun provideNewsDataApiService(@Named("newsData") retrofit: Retrofit): NewsDataApiService =
        retrofit.create(NewsDataApiService::class.java)

    // ─── MediaStack ───────────────────────────────────────────────────────────
    @Provides @Singleton @Named("mediaStack")
    fun provideMediaStackRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        buildRetrofit("http://api.mediastack.com/", okHttpClient, json)

    @Provides @Singleton
    fun provideMediaStackApiService(@Named("mediaStack") retrofit: Retrofit): MediaStackApiService =
        retrofit.create(MediaStackApiService::class.java)
}
