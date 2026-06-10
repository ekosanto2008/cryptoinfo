package com.santoso.tech.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OkxResponse<T>(
    val code: String,
    val msg: String = "",
    val data: List<T> = emptyList()
)

@Serializable
data class OkxCandleResponse(
    val code: String,
    val msg: String = "",
    // Each candle: [ts, open, high, low, close, vol, volCcy, volCcyQuote, confirm]
    val data: List<List<String>> = emptyList()
)

@Serializable
data class Instrument(
    val instId: String,
    val baseCcy: String = "",
    val quoteCcy: String = "",
    val state: String = ""
)
