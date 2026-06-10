package com.santoso.tech.data.repository

import com.santoso.tech.data.api.OkxApiService
import com.santoso.tech.data.model.Instrument
import com.santoso.tech.data.model.Ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val apiService: OkxApiService
) {
    /** Try REST /market/tickers. On failure, return empty list. */
    fun fetchAll(t: String): Flow<List<Ticker>> = flow {
        try {
            val response = apiService.getTickers(t)
            if (response.code == "0") {
                emit(response.data)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    /** Fetch instrument list from /public/instruments (not blocked by ISP). */
    fun fetchInstruments(t: String): Flow<List<Instrument>> = flow {
        try {
            val res = apiService.getInstruments(t)
            if (res.code == "0") {
                emit(res.data.filter { it.state == "live" })
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    fun fetchOne(id: String): Flow<Ticker> = flow {
        try {
            val response = apiService.getTicker(id)
            if (response.code == "0" && response.data.isNotEmpty()) {
                emit(response.data[0])
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        /** Top 60 most popular SPOT pairs — used as fallback & Top List tab. */
        val TOP_PAIRS = listOf(
            // Ordered by approximate CoinMarketCap market cap ranking
            "BTC-USDT", "ETH-USDT", "BNB-USDT", "SOL-USDT", "XRP-USDT", "DOGE-USDT", "ADA-USDT", "TRX-USDT",
            "AVAX-USDT", "LINK-USDT", "SUI-USDT", "TON-USDT", "SHIB-USDT", "DOT-USDT", "BCH-USDT", "LTC-USDT",
            "NEAR-USDT", "UNI-USDT", "APT-USDT", "ICP-USDT", "PEPE-USDT", "ETC-USDT", "HBAR-USDT", "MATIC-USDT",
            "XLM-USDT", "ATOM-USDT", "FIL-USDT", "RNDR-USDT", "OKB-USDT", "ARB-USDT",
            "INJ-USDT", "OP-USDT", "VET-USDT", "FET-USDT", "STX-USDT", "MKR-USDT", "GRT-USDT", "AAVE-USDT",
            "THETA-USDT", "IMX-USDT", "SEI-USDT", "TIA-USDT", "FTM-USDT", "FLOKI-USDT", "BONK-USDT", "WIF-USDT",
            "AR-USDT", "LDO-USDT", "RUNE-USDT", "JASMY-USDT", "ORDI-USDT", "GALA-USDT", "CHZ-USDT", "EOS-USDT",
            "SAND-USDT", "MANA-USDT", "AXS-USDT", "SNX-USDT", "FLOW-USDT", "NEO-USDT"
        )

        /** Create a placeholder Ticker from an instId (prices will be filled by WebSocket). */
        fun placeholderTicker(instId: String) = Ticker(
            instId = instId,
            last = "0",
            lastSz = "0",
            askPx = "0",
            askSz = "0",
            bidPx = "0",
            bidSz = "0",
            open24h = "0",
            high24h = "0",
            low24h = "0",
            vol24h = "0",
            volCcy24h = "0",
            ts = System.currentTimeMillis().toString()
        )
    }
}
