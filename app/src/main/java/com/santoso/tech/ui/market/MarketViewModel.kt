package com.santoso.tech.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.data.repository.FavoriteRepository
import com.santoso.tech.data.repository.MarketRepository
import com.santoso.tech.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Currency { USD, IDR }
enum class MarketTab { TOP_LIST, ALL_COIN }

// Approximate IDR/USD exchange rate
private const val IDR_RATE = 15_900.0

sealed class MarketUiState {
    object Loading : MarketUiState()
    data class Success(
        val tickers: List<Ticker>,
        val favorites: List<String>,
        val currency: Currency,
        val tab: MarketTab,
        val totalCount: Int
    ) : MarketUiState()
    data class Error(val message: String) : MarketUiState()
}

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val favoriteRepository: FavoriteRepository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    // Master ticker map: instId -> Ticker (all known tickers)
    private val _tickerMap = MutableStateFlow<Map<String, Ticker>>(emptyMap())
    // List of instIds for current tab
    private val _currentInstIds = MutableStateFlow<List<String>>(emptyList())
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _currency = MutableStateFlow(Currency.USD)
    private val _tab = MutableStateFlow(MarketTab.TOP_LIST)

    // Throttle UI updates from WebSocket — collect at most every 500ms
    private var pendingUiUpdate = false
    private var throttleJob: Job? = null

    init {
        webSocketManager.connect()
        loadTab(MarketTab.TOP_LIST)
        observeFavorites()
        observeWebSocket()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteRepository.getAllFavorites().collect { list ->
                _favorites.value = list.map { it.instId }
                emitUiNow()
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            webSocketManager.tickerFlow
                .collect { tick ->
                    // Update the master map silently
                    val updated = _tickerMap.value.toMutableMap()
                    updated[tick.instId] = tick
                    _tickerMap.value = updated
                    // Throttled UI update
                    scheduleUiUpdate()
                }
        }
    }

    /** Schedule a UI update. Coalesces rapid-fire WS updates into max 2 updates/sec. */
    private fun scheduleUiUpdate() {
        if (throttleJob?.isActive == true) {
            pendingUiUpdate = true
            return
        }
        throttleJob = viewModelScope.launch {
            emitUiNow()
            delay(500) // Wait 500ms before allowing next UI update
            if (pendingUiUpdate) {
                pendingUiUpdate = false
                emitUiNow()
            }
        }
    }

    fun switchTab(tab: MarketTab) {
        if (_tab.value == tab) return
        _tab.value = tab
        _searchQuery.value = ""
        loadTab(tab)
    }

    private fun loadTab(tab: MarketTab) {
        viewModelScope.launch {
            _uiState.value = MarketUiState.Loading

            when (tab) {
                MarketTab.TOP_LIST -> {
                    val ids = MarketRepository.TOP_PAIRS
                    initializeTickerPlaceholders(ids)
                    _currentInstIds.value = ids
                    emitUiNow()
                    webSocketManager.replaceTickerSubscriptions(ids)
                }
                MarketTab.ALL_COIN -> {
                    var loaded = false
                    repository.fetchAll("SPOT")
                        .catch { /* ignore */ }
                        .collect { tickers ->
                            if (tickers.isNotEmpty()) {
                                loaded = true
                                val map = _tickerMap.value.toMutableMap()
                                tickers.forEach { map[it.instId] = it }
                                _tickerMap.value = map
                                _currentInstIds.value = tickers.map { it.instId }
                                emitUiNow()
                                // Only subscribe top 60 to avoid ANR from too many WS updates
                                val wsIds = tickers.take(60).map { it.instId }
                                webSocketManager.replaceTickerSubscriptions(wsIds)
                            }
                        }

                    if (!loaded) {
                        repository.fetchInstruments("SPOT")
                            .catch { /* ignore */ }
                            .collect { instruments ->
                                if (instruments.isNotEmpty()) {
                                    loaded = true
                                    val ids = instruments.map { it.instId }
                                    initializeTickerPlaceholders(ids)
                                    _currentInstIds.value = ids
                                    emitUiNow()
                                    val wsIds = ids.take(60)
                                    webSocketManager.replaceTickerSubscriptions(wsIds)
                                }
                            }
                    }

                    if (!loaded) {
                        val ids = MarketRepository.TOP_PAIRS
                        initializeTickerPlaceholders(ids)
                        _currentInstIds.value = ids
                        emitUiNow()
                        webSocketManager.replaceTickerSubscriptions(ids)
                    }
                }
            }
        }
    }

    private fun initializeTickerPlaceholders(ids: List<String>) {
        val map = _tickerMap.value.toMutableMap()
        ids.forEach { id ->
            if (!map.containsKey(id)) {
                map[id] = MarketRepository.placeholderTicker(id)
            }
        }
        _tickerMap.value = map
    }

    fun onSearchQueryChange(q: String) {
        _searchQuery.value = q
        emitUiNow()
    }

    fun toggleCurrency() {
        _currency.value = if (_currency.value == Currency.USD) Currency.IDR else Currency.USD
        emitUiNow()
    }

    fun fetchTickers() {
        loadTab(_tab.value)
    }

    private fun emitUiNow() {
        val map = _tickerMap.value
        val ids = _currentInstIds.value
        val q = _searchQuery.value
        val f = _favorites.value
        val tab = _tab.value
        val currency = _currency.value

        val tickers = ids.mapNotNull { map[it] }
        val filtered = if (q.isEmpty()) tickers else tickers.filter {
            it.instId.contains(q, true)
        }

        // Sort: favorites first; Top List preserves CMC-like order, All Coin by volume (USDT)
        val sorted = if (tab == MarketTab.TOP_LIST) {
            // Top List: keep hardcoded order (mirrors CMC market cap ranking)
            filtered.sortedWith(
                compareByDescending<Ticker> { f.contains(it.instId) }
                    .thenBy { ids.indexOf(it.instId) }
            )
        } else {
            // All Coin: sort by 24h trading volume in USDT (proxy for market cap)
            filtered.sortedWith(
                compareByDescending<Ticker> { f.contains(it.instId) }
                    .thenByDescending { it.volCcy24h.toDoubleOrNull() ?: 0.0 }
            )
        }

        _uiState.value = MarketUiState.Success(
            tickers = sorted,
            favorites = f,
            currency = currency,
            tab = tab,
            totalCount = tickers.size
        )
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.disconnect()
    }

    companion object {
        fun convertPrice(usdPrice: String, currency: Currency): String {
            val price = usdPrice.toDoubleOrNull() ?: return usdPrice
            if (price == 0.0) return if (currency == Currency.USD) "$0.00" else "Rp 0"
            return when (currency) {
                Currency.USD -> "$${formatNumber(price)}"
                Currency.IDR -> "Rp ${formatNumber(price * IDR_RATE)}"
            }
        }

        fun formatNumber(value: Double): String {
            return when {
                value >= 1_000_000_000 -> String.format("%.2fB", value / 1_000_000_000)
                value >= 1_000_000 -> String.format("%.2fM", value / 1_000_000)
                value >= 1_000 -> String.format("%,.2f", value)
                value >= 1 -> String.format("%.4f", value)
                value > 0 -> String.format("%.8f", value)
                else -> "0.00"
            }
        }
    }
}
