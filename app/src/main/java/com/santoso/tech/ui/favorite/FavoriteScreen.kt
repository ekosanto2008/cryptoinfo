package com.santoso.tech.ui.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.ui.common.CoinLogo
import com.santoso.tech.ui.market.Currency
import com.santoso.tech.ui.market.CurrencyToggle
import com.santoso.tech.ui.market.MarketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    viewModel: FavoriteViewModel,
    onCoinClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val darkBg = Color(0xFF0D1117)
    val accentYellow = Color(0xFFFFC107)
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(uiState) {
        if (uiState !is FavoriteUiState.Loading) isRefreshing = false
    }

    Scaffold(
        containerColor = darkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = accentYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Favorit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = accentYellow
                            )
                            Text(
                                "Koin pilihan Anda · Live",
                                fontSize = 11.sp,
                                color = Color(0xFF3FB950)
                            )
                        }
                    }
                },
                actions = {
                    val currency = if (uiState is FavoriteUiState.Success)
                        (uiState as FavoriteUiState.Success).currency else Currency.USD
                    CurrencyToggle(current = currency, onToggle = { viewModel.toggleCurrency() })
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            state = pullState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(darkBg)
        ) {
            when (val state = uiState) {
                is FavoriteUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = accentYellow)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memuat favorit...", color = Color.Gray, fontSize = 14.sp)
                    }
                }

                is FavoriteUiState.Empty -> {
                    EmptyFavoriteView()
                }

                is FavoriteUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${state.tickers.size} Koin Favorit · Tarik untuk refresh",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF3FB950))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Real-time", color = Color(0xFF3FB950), fontSize = 11.sp)
                                }
                            }
                        }

                        items(state.tickers, key = { it.instId }) { ticker ->
                            FavoriteCoinCard(
                                ticker = ticker,
                                currency = state.currency,
                                onClick = { onCoinClick(ticker.instId) },
                                onRemove = { viewModel.removeFavorite(ticker.instId) }
                            )
                        }
                    }
                }

                is FavoriteUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚠️", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFavoriteView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⭐", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Belum Ada Favorit",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Buka halaman Pasar, tap koin yang diinginkan,\nlalu tekan ★ di pojok kanan atas untuk menambahkan ke favorit.",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun FavoriteCoinCard(
    ticker: Ticker,
    currency: Currency,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val baseCoin = ticker.instId.split("-").firstOrNull() ?: "BTC"

    val open = ticker.open24h.toDoubleOrNull() ?: 0.0
    val last = ticker.last.toDoubleOrNull() ?: 0.0
    val changePercent = if (open != 0.0) ((last - open) / open) * 100 else 0.0
    val isPositive = changePercent >= 0
    val changeColor = if (isPositive) Color(0xFF00C853) else Color(0xFFD32F2F)
    val changeBg = if (isPositive) Color(0xFF00C85318) else Color(0xFFD32F2F18)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo + name
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CoinLogo(symbol = baseCoin)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ticker.instId.split("-").firstOrNull() ?: ticker.instId,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                    }
                    Text(ticker.instId, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Vol: ${MarketViewModel.formatNumber(ticker.vol24h.toDoubleOrNull() ?: 0.0)}",
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681)
                    )
                }
            }

            // Price + change + remove
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        MarketViewModel.convertPrice(ticker.last, currency),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(changeBg).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${if (isPositive) "+" else ""}${String.format("%.2f%%", changePercent)}",
                            color = changeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus favorit",
                        tint = Color(0xFF6E7681),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
