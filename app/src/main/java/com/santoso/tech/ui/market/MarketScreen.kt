package com.santoso.tech.ui.market

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.ui.common.CoinLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    viewModel: MarketViewModel,
    onPairClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    val darkBg = Color(0xFF0D1117)
    val accentBlue = Color(0xFF58A6FF)

    // Reset isRefreshing when data arrives
    LaunchedEffect(uiState) {
        if (uiState !is MarketUiState.Loading) isRefreshing = false
    }

    Scaffold(
        containerColor = darkBg,
        topBar = {
            if (isSearching) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.onSearchQueryChange(it)
                    },
                    onCloseClick = {
                        isSearching = false
                        searchQuery = ""
                        viewModel.onSearchQueryChange("")
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "CryptoInfo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = accentBlue
                            )
                            Text(
                                "Powered by OKX · Live",
                                fontSize = 11.sp,
                                color = Color(0xFF3FB950)
                            )
                        }
                    },
                    actions = {
                        val currency = if (uiState is MarketUiState.Success)
                            (uiState as MarketUiState.Success).currency else Currency.USD
                        CurrencyToggle(
                            current = currency,
                            onToggle = { viewModel.toggleCurrency() }
                        )
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D1117),
                        titleContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.fetchTickers()
            },
            state = pullState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(darkBg)
        ) {
            when (val state = uiState) {
                is MarketUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = accentBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memuat data pasar...", color = Color.Gray, fontSize = 14.sp)
                    }
                }
                is MarketUiState.Success -> {
                    MarketList(
                        tickers = state.tickers,
                        favorites = state.favorites,
                        currency = state.currency,
                        tab = state.tab,
                        totalCount = state.totalCount,
                        onPairClick = onPairClick,
                        onTabChange = { viewModel.switchTab(it) }
                    )
                }
                is MarketUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚠️", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tarik ke bawah untuk refresh", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyToggle(current: Currency, onToggle: () -> Unit) {
    val isIDR = current == Currency.IDR
    val bgColor by animateColorAsState(
        targetValue = if (isIDR) Color(0xFF1E6B3D) else Color(0xFF1A3A6B),
        animationSpec = tween(300), label = "currencyColor"
    )
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (isIDR) "IDR" else "USD",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF0D1117))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCloseClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(4.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Cari koin...", color = Color.Gray, fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161B22),
                unfocusedContainerColor = Color(0xFF161B22),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF58A6FF)
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
        )
    }
}

@Composable
fun TabChips(
    selectedTab: MarketTab,
    onTabChange: (MarketTab) -> Unit
) {
    val accentBlue = Color(0xFF58A6FF)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        TabChip(
            label = "🔥 Top List",
            selected = selectedTab == MarketTab.TOP_LIST,
            onClick = { onTabChange(MarketTab.TOP_LIST) },
            selectedColor = accentBlue
        )
        TabChip(
            label = "📊 All Coin",
            selected = selectedTab == MarketTab.ALL_COIN,
            onClick = { onTabChange(MarketTab.ALL_COIN) },
            selectedColor = accentBlue
        )
    }
}

@Composable
fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedColor else Color(0xFF21262D),
        animationSpec = tween(200), label = "tabBg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
fun MarketList(
    tickers: List<Ticker>,
    favorites: List<String>,
    currency: Currency,
    tab: MarketTab,
    totalCount: Int,
    onPairClick: (String) -> Unit,
    onTabChange: (MarketTab) -> Unit
) {
    val listState = rememberLazyListState()
    var visibleCount by remember { mutableStateOf(30) }

    // Reset visible count when tab changes
    LaunchedEffect(tab) { visibleCount = 30 }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tab selector
        item {
            TabChips(selectedTab = tab, onTabChange = onTabChange)
        }

        // Info row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalCount Koin · Tarik untuk refresh",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3FB950))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Real-time", color = Color(0xFF3FB950), fontSize = 11.sp)
                }
            }
        }

        val visible = tickers.take(visibleCount)
        items(visible, key = { it.instId }) { ticker ->
            TickerCard(
                ticker = ticker,
                isFavorite = favorites.contains(ticker.instId),
                currency = currency,
                onClick = { onPairClick(ticker.instId) }
            )
        }

        if (visibleCount < tickers.size) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = { visibleCount += 30 },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58A6FF))
                    ) {
                        Text("Muat ${minOf(30, tickers.size - visibleCount)} lagi (${tickers.size - visibleCount} tersisa)")
                    }
                }
            }
        }
    }
}

@Composable
fun TickerCard(
    ticker: Ticker,
    isFavorite: Boolean,
    currency: Currency,
    onClick: () -> Unit
) {
    val baseCoin = ticker.instId.split("-").firstOrNull() ?: "BTC"

    val open = ticker.open24h.toDoubleOrNull() ?: 0.0
    val last = ticker.last.toDoubleOrNull() ?: 0.0
    val changePercent = if (open != 0.0) ((last - open) / open) * 100 else 0.0
    val isPositive = changePercent >= 0
    val changeColor = if (isPositive) Color(0xFF00C853) else Color(0xFFD32F2F)
    val changeBg = if (isPositive) Color(0xFF00C85318) else Color(0xFFD32F2F18)

    // Show loading state for coins with no price yet
    val hasPrice = last > 0.0

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CoinLogo(symbol = baseCoin)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ticker.instId.split("-").firstOrNull() ?: ticker.instId,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        if (isFavorite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(text = ticker.instId, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hasPrice) {
                        Text(
                            text = "Vol: ${MarketViewModel.formatNumber(ticker.vol24h.toDoubleOrNull() ?: 0.0)}",
                            fontSize = 11.sp,
                            color = Color(0xFF6E7681)
                        )
                    } else {
                        Text(
                            text = "Menunggu data...",
                            fontSize = 11.sp,
                            color = Color(0xFF6E7681)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (hasPrice) {
                    Text(
                        text = MarketViewModel.convertPrice(ticker.last, currency),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(changeBg).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${if (isPositive) "+" else ""}${String.format("%.2f%%", changePercent)}",
                            color = changeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Pulsing placeholder
                    Text(
                        text = "···",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF6E7681)
                    )
                }
            }
        }
    }
}
