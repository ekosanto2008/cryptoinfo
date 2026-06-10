package com.santoso.tech.data.model

data class CandleData(
    val timestamp: Long,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Float
)
