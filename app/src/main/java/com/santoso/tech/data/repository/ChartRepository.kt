package com.santoso.tech.data.repository

import com.santoso.tech.data.api.OkxApiService
import com.santoso.tech.data.model.ChartCandle
import com.santoso.tech.websocket.OkxCandleWebSocketManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChartRepository @Inject constructor(
    private val apiService: OkxApiService,
    private val candleWsManager: OkxCandleWebSocketManager
) {

    suspend fun getInitialCandles(instId: String, bar: String): List<ChartCandle> {
        val response = apiService.getCandles(instId = instId, bar = bar, limit = 200)
        if (response.code != "0") {
            throw Exception(response.msg)
        }

        return response.data.map { row ->
            val timeMs = row[0].toLongOrNull() ?: 0L
            ChartCandle(
                time = timeMs / 1000,
                open = row[1].toDoubleOrNull() ?: 0.0,
                high = row[2].toDoubleOrNull() ?: 0.0,
                low = row[3].toDoubleOrNull() ?: 0.0,
                close = row[4].toDoubleOrNull() ?: 0.0
            )
        }.reversed() // OKX returns newest to oldest, TradingView expects oldest to newest
    }

    fun observeRealtimeCandles(instId: String, bar: String): Flow<ChartCandle> {
        candleWsManager.connect()
        candleWsManager.subscribe(instId, bar)
        return candleWsManager.candleFlow
    }

    fun unsubscribeRealtimeCandles(instId: String, bar: String) {
        candleWsManager.unsubscribe(instId, bar)
    }

    fun closeConnection() {
        candleWsManager.close()
    }
}
