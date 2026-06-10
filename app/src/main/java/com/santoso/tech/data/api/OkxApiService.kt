package com.santoso.tech.data.api

import com.santoso.tech.data.model.Instrument
import com.santoso.tech.data.model.OkxCandleResponse
import com.santoso.tech.data.model.OkxResponse
import com.santoso.tech.data.model.Ticker
import retrofit2.http.GET
import retrofit2.http.Query

interface OkxApiService {
    @GET("api/v5/market/tickers")
    suspend fun getTickers(
        @Query("instType") instType: String = "SPOT"
    ): OkxResponse<Ticker>

    @GET("api/v5/market/ticker")
    suspend fun getTicker(
        @Query("instId") instId: String
    ): OkxResponse<Ticker>

    @GET("api/v5/market/candles")
    suspend fun getCandles(
        @Query("instId") instId: String,
        @Query("bar") bar: String = "1D",
        @Query("limit") limit: Int = 60
    ): OkxCandleResponse

    @GET("api/v5/public/instruments")
    suspend fun getInstruments(
        @Query("instType") instType: String = "SPOT"
    ): OkxResponse<Instrument>
}
