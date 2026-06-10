package com.santoso.tech.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import kotlin.math.abs

/**
 * Generates a consistent, visually pleasing color for a given coin symbol.
 */
fun coinColor(symbol: String): Color {
    val hash = abs(symbol.hashCode())
    val hue = (hash % 360).toFloat()
    return Color.hsl(hue, 0.6f, 0.45f)
}

/**
 * Coin logo with automatic fallback to a colored circle with the coin's initial letter.
 * Uses atomiclabs CDN as primary source.
 */
@Composable
fun CoinLogo(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 32.dp
) {
    val lowerSymbol = symbol.lowercase()
    val logoUrl = "https://cdn.jsdelivr.net/gh/atomiclabs/cryptocurrency-icons/128/color/$lowerSymbol.png"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF21262D)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = logoUrl,
            contentDescription = "$symbol logo",
            modifier = Modifier.size(iconSize),
            contentScale = ContentScale.Fit,
            loading = {
                // Show letter while loading
                LetterAvatar(symbol = symbol, size = size)
            },
            error = {
                // Show letter fallback on error
                LetterAvatar(symbol = symbol, size = size)
            }
        )
    }
}

@Composable
fun LetterAvatar(symbol: String, size: Dp) {
    val color = coinColor(symbol)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}
