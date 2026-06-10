package com.santoso.tech.data.repository

import com.santoso.tech.data.api.OkxApiService
import com.santoso.tech.data.model.CandleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CandleRepository @Inject constructor(
    private val apiService: OkxApiService
) {
    fun fetchCandles(instId: String, bar: String = "1D", limit: Int = 60): Flow<List<CandleData>> = flow {
        try {
            val response = apiService.getCandles(instId, bar, limit)
            if (response.code == "0" && response.data.isNotEmpty()) {
                val candles = response.data.mapNotNull { row ->
                    // OKX candle row: [ts, open, high, low, close, vol, ...]
                    if (row.size < 5) return@mapNotNull null
                    CandleData(
                        timestamp = row[0].toLongOrNull() ?: 0L,
                        open = row[1].toFloatOrNull() ?: 0f,
                        high = row[2].toFloatOrNull() ?: 0f,
                        low = row[3].toFloatOrNull() ?: 0f,
                        close = row[4].toFloatOrNull() ?: 0f,
                        volume = row[5].toFloatOrNull() ?: 0f
                    )
                }.reversed() // OKX returns newest first, reverse for chart
                emit(candles)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
