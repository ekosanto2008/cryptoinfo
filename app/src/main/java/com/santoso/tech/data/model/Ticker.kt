package com.santoso.tech.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Ticker(
    val instId: String,       // Instrument ID, e.g. BTC-USDT
    val last: String,         // Last traded price
    val lastSz: String,       // Last traded size
    val askPx: String,        // Best ask price
    val askSz: String,        // Best ask size
    val bidPx: String,        // Best bid price
    val bidSz: String,        // Best bid size
    val open24h: String,      // Open price in the last 24h
    val high24h: String,      // Highest price in the last 24h
    val low24h: String,       // Lowest price in the last 24h
    val vol24h: String,       // 24h trading volume, with a unit of currency
    val volCcy24h: String,    // 24h trading volume, with a unit of base currency
    val ts: String            // Ticker data generation time, Unix timestamp format in milliseconds, e.g. 1597026383085
)
