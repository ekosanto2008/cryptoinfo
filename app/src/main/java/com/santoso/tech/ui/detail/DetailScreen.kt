package com.santoso.tech.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santoso.tech.data.model.CandleData
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.ui.common.CandlestickChart
import com.santoso.tech.ui.common.CoinLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val darkBg = Color(0xFF0D1117)

    Scaffold(
        containerColor = darkBg,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (uiState is DetailUiState.Success)
                        (uiState as DetailUiState.Success).ticker.instId
                    else "Detail Koin"
                    Text(text = title, color = Color.White, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        val isFavorite = (uiState as DetailUiState.Success).isFavorite
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFC107) else Color.Gray
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(darkBg)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF58A6FF)
                    )
                }
                is DetailUiState.Success -> {
                    TickerDetail(
                        ticker = state.ticker,
                        candles = state.candles,
                        selectedTimeframe = state.selectedTimeframe,
                        onTimeframeChange = { viewModel.fetchCandles(it) }
                    )
                }
                is DetailUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TickerDetail(
    ticker: Ticker,
    candles: List<CandleData>,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit
) {
    val baseCoin = ticker.instId.split("-").firstOrNull() ?: "BTC"

    val open = ticker.open24h.toDoubleOrNull() ?: 0.0
    val last = ticker.last.toDoubleOrNull() ?: 0.0
    val changePercent = if (open != 0.0) ((last - open) / open) * 100 else 0.0
    val isPositive = changePercent >= 0
    val changeColor = if (isPositive) Color(0xFF00C853) else Color(0xFFD32F2F)

    val cardColor = Color(0xFF161B22)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CoinLogo(symbol = baseCoin, size = 56.dp, iconSize = 40.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = ticker.instId,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = baseCoin.uppercase(),
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF21262D))
                Spacer(modifier = Modifier.height(16.dp))

                // Price
                Text(
                    text = "$${ticker.last}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "${if (isPositive) "▲" else "▼"} ${String.format("%.2f%%", changePercent)} (24j)",
                        color = changeColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Statistik 24 Jam",
                    color = Color(0xFF58A6FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Tertinggi", ticker.high24h, Modifier.weight(1f))
                    StatItem("Terendah", ticker.low24h, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Volume (koin)", ticker.vol24h, Modifier.weight(1f))
                    StatItem("Volume (quote)", ticker.volCcy24h, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Bid", ticker.bidPx, Modifier.weight(1f))
                    StatItem("Ask", ticker.askPx, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chart card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Candlestick Chart",
                        color = Color(0xFF58A6FF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timeframe selector
                val timeframes = listOf("15m", "1H", "4H", "1D", "1W")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    timeframes.forEach { tf ->
                        val isSelected = tf == selectedTimeframe
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTimeframeChange(tf) },
                            label = {
                                Text(
                                    tf,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color.Gray
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF58A6FF),
                                containerColor = Color(0xFF21262D)
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CandlestickChart(
                    candles = candles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
