package com.santoso.tech.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChartCandle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)
