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
import com.santoso.tech.ui.common.ErrorScreen
import com.santoso.tech.ui.common.ShimmerTickerCard
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santoso.tech.data.model.CandleData
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.data.repository.Currency
import com.santoso.tech.ui.common.CandlestickChart
import com.santoso.tech.ui.common.CoinLogo
import com.santoso.tech.ui.market.CurrencyToggle
import com.santoso.tech.ui.market.MarketViewModel
import com.santoso.tech.ui.common.ErrorScreen
import com.santoso.tech.ui.common.ShimmerTickerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currency by viewModel.currencyFlow.collectAsState()
    val bgColor = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (uiState is DetailUiState.Success)
                        (uiState as DetailUiState.Success).ticker.instId
                    else "Detail Koin"
                    Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        CurrencyToggle(current = currency, onToggle = { viewModel.toggleCurrency() })
                        val isFavorite = (uiState as DetailUiState.Success).isFavorite
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(bgColor)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShimmerTickerCard()
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                is DetailUiState.Success -> {
                    TickerDetail(
                        ticker = state.ticker,
                        currency = currency,
                        candlesJson = state.candlesJson,
                        latestCandleJson = state.latestCandleJson,
                        selectedTimeframe = state.selectedTimeframe,
                        onTimeframeChange = { viewModel.fetchChartData(it) }
                    )
                }
                is DetailUiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.refreshData() }
                    )
                }
            }
        }
    }
}

@Composable
fun TickerDetail(
    ticker: Ticker,
    currency: Currency,
    candlesJson: String?,
    latestCandleJson: String?,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit
) {
    val baseCoin = ticker.instId.split("-").firstOrNull() ?: "BTC"

    val open = ticker.open24h.toDoubleOrNull() ?: 0.0
    val last = ticker.last.toDoubleOrNull() ?: 0.0
    val changePercent = if (open != 0.0) ((last - open) / open) * 100 else 0.0
    val isPositive = changePercent >= 0
    val changeColor = if (isPositive) Color(0xFF00C853) else Color(0xFFD32F2F)

    val cardColor = MaterialTheme.colorScheme.surface

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
            elevation = CardDefaults.cardElevation(2.dp)
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = baseCoin.uppercase(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF21262D))
                Spacer(modifier = Modifier.height(16.dp))

                // Price
                Text(
                    text = MarketViewModel.convertPrice(ticker.last, currency),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Tertinggi", MarketViewModel.convertPrice(ticker.high24h, currency), Modifier.weight(1f))
                    StatItem("Terendah", MarketViewModel.convertPrice(ticker.low24h, currency), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Volume (koin)", MarketViewModel.formatNumber(ticker.vol24h.toDoubleOrNull() ?: 0.0), Modifier.weight(1f))
                    StatItem("Volume (quote)", MarketViewModel.formatNumber(ticker.volCcy24h.toDoubleOrNull() ?: 0.0), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Bid", MarketViewModel.convertPrice(ticker.bidPx, currency), Modifier.weight(1f))
                    StatItem("Ask", MarketViewModel.convertPrice(ticker.askPx, currency), Modifier.weight(1f))
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
                        color = MaterialTheme.colorScheme.primary,
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
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                RealtimeTradingChart(
                    candlesJson = candlesJson,
                    latestCandleJson = latestCandleJson,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealtimeTradingChart(
    candlesJson: String?,
    latestCandleJson: String?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                
                // Set background to dark slate matching the app theme
                setBackgroundColor(android.graphics.Color.parseColor("#161B22"))

                // Load the HTML file from assets
                loadUrl("file:///android_asset/chart.html")
            }
        },
        update = { webView ->
            if (candlesJson != null) {
                // Evaluate javascript to set initial candles
                webView.evaluateJavascript("javascript:setCandles('$candlesJson');", null)
            }
            if (latestCandleJson != null) {
                // Evaluate javascript to update real-time candle
                webView.evaluateJavascript("javascript:updateCandle('$latestCandleJson');", null)
            }
        }
    )
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
