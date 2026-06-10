package com.santoso.tech.di

import com.santoso.tech.data.api.OkxApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val BASE_URL = "https://okx.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        val customDns = object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return try {
                    when {
                        hostname.contains("ws.okx.com") -> {
                            // Correct Cloudflare IP for ws.okx.com
                            listOf(InetAddress.getByName("104.18.43.174"))
                        }
                        hostname.contains("okx.com") -> {
                            // Cloudflare IP for okx.com
                            listOf(InetAddress.getByName("104.18.23.111"))
                        }
                        else -> {
                            okhttp3.Dns.SYSTEM.lookup(hostname)
                        }
                    }
                } catch (e: Exception) {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                }
            }
        }
            
        return OkHttpClient.Builder()
            .dns(customDns)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOkxApiService(retrofit: Retrofit): OkxApiService =
        retrofit.create(OkxApiService::class.java)
}
