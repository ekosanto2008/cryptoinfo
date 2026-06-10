package com.santoso.tech.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santoso.tech.data.model.CandleData
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CandlestickChart(
    candles: List<CandleData>,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color(0xFF0D1117), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Memuat chart...", color = Color.Gray, fontSize = 13.sp)
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()

    // Animation
    var animStart by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animStart) 1f else 0f,
        animationSpec = tween(durationMillis = 800), label = "candleAnim"
    )
    LaunchedEffect(candles) { animStart = true }

    val greenColor = Color(0xFF00C853)
    val redColor = Color(0xFFD32F2F)
    val gridColor = Color(0xFF21262D)
    val textColor = Color(0xFF8B949E)

    Box(
        modifier = modifier
            .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingLeft = 60f
            val paddingRight = 16f
            val paddingTop = 16f
            val paddingBottom = 40f

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            val maxHigh = candles.maxOf { it.high }
            val minLow = candles.minOf { it.low }
            val priceRange = maxHigh - minLow
            if (priceRange == 0f) return@Canvas

            // Draw horizontal grid lines
            val gridLines = 5
            repeat(gridLines + 1) { i ->
                val y = paddingTop + (chartHeight / gridLines) * i
                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 0.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
                // Price label
                val priceVal = maxHigh - (priceRange / gridLines) * i
                val labelText = formatPrice(priceVal)
                val measuredText = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(color = textColor, fontSize = 9.sp)
                )
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(2f, y - measuredText.size.height / 2)
                )
            }

            // Candle width
            val candleCount = candles.size
            val totalWidth = chartWidth
            val candleWidth = (totalWidth / candleCount) * 0.7f
            val candleSpacing = totalWidth / candleCount

            candles.forEachIndexed { index, candle ->
                val x = paddingLeft + index * candleSpacing + candleSpacing / 2
                val isGreen = candle.close >= candle.open

                val color = if (isGreen) greenColor else redColor

                // Y coordinates
                fun priceToY(price: Float): Float {
                    return paddingTop + ((maxHigh - price) / priceRange) * chartHeight * animProgress
                }

                val openY = priceToY(candle.open)
                val closeY = priceToY(candle.close)
                val highY = priceToY(candle.high)
                val lowY = priceToY(candle.low)

                // Draw wick (high-low line)
                drawLine(
                    color = color,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = 1.5f
                )

                // Draw body (open-close rectangle)
                val bodyTop = minOf(openY, closeY)
                val bodyHeight = maxOf(Math.abs(closeY - openY), 2f)

                drawRect(
                    color = color,
                    topLeft = Offset(x - candleWidth / 2, bodyTop),
                    size = Size(candleWidth, bodyHeight)
                )
            }

            // Draw date labels (show every ~10 candles)
            val step = maxOf(1, candleCount / 6)
            candles.forEachIndexed { index, candle ->
                if (index % step == 0) {
                    val x = paddingLeft + index * candleSpacing + candleSpacing / 2
                    val date = SimpleDateFormat("MMM dd", Locale.getDefault())
                        .format(Date(candle.timestamp))
                    val measuredText = textMeasurer.measure(
                        text = date,
                        style = TextStyle(color = textColor, fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(
                            x - measuredText.size.width / 2,
                            size.height - paddingBottom + 8f
                        )
                    )
                }
            }
        }
    }
}

private fun formatPrice(price: Float): String {
    return when {
        price >= 10_000 -> String.format("%.0f", price)
        price >= 1_000 -> String.format("%.1f", price)
        price >= 1 -> String.format("%.2f", price)
        else -> String.format("%.4f", price)
    }
}
