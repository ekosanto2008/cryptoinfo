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

import com.santoso.tech.data.repository.Currency
import com.santoso.tech.data.repository.SettingsRepository
enum class MarketTab { TOP_LIST, ALL_COIN }

// Exchange rate will be fetched dynamically


sealed class MarketUiState {
    object Loading : MarketUiState()
    data class Success(
        val tickers: List<Ticker>,
        val favorites: List<String>,
        val currency: Currency,
        val themeMode: com.santoso.tech.data.repository.ThemeMode,
        val tab: MarketTab,
        val totalCount: Int
    ) : MarketUiState()
    data class Error(val message: String) : MarketUiState()
}

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val favoriteRepository: FavoriteRepository,
    private val webSocketManager: WebSocketManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    // Master ticker map: instId -> Ticker (all known tickers)
    private val _tickerMap = MutableStateFlow<Map<String, Ticker>>(emptyMap())
    // List of instIds for current tab
    private val _currentInstIds = MutableStateFlow<List<String>>(emptyList())
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _tab = MutableStateFlow(MarketTab.TOP_LIST)

    // Throttle UI updates from WebSocket — collect at most every 500ms
    private var pendingUiUpdate = false
    private var throttleJob: Job? = null

    init {
        fetchRealtimeIdrRate()
        fetchAllInstrumentsBackground()
        webSocketManager.connect()
        loadTab(MarketTab.TOP_LIST)
        observeFavorites()
        
        viewModelScope.launch {
            settingsRepository.currencyFlow.collect {
                emitUiNow()
            }
        }
        observeWebSocket()
    }

    private fun fetchAllInstrumentsBackground() {
        viewModelScope.launch {
            repository.fetchInstruments("SPOT")
                .catch { /* ignore */ }
                .collect { allInstruments ->
                    val instruments = allInstruments.filter { it.instId.endsWith("-USDT") }
                    val ids = instruments.map { it.instId }
                    // Populate placeholder map with all possible instruments
                    val map = _tickerMap.value.toMutableMap()
                    ids.forEach { id ->
                        if (!map.containsKey(id)) {
                            map[id] = MarketRepository.placeholderTicker(id)
                        }
                    }
                    _tickerMap.value = map
                }
        }
    }

    private fun fetchRealtimeIdrRate() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = java.net.URL("https://api.exchangerate-api.com/v4/latest/USD").readText()
                val rate = org.json.JSONObject(json).getJSONObject("rates").getDouble("IDR")
                currentIdrRate = rate
                emitUiNow()
            } catch (e: Exception) {
                // Keep default if fails
            }
        }
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
                    updateWebSocketSubscriptions()
                }
                MarketTab.ALL_COIN -> {
                    var loaded = false
                    repository.fetchAll("SPOT")
                        .catch { /* ignore */ }
                        .collect { allTickers ->
                            // Filter only USDT pairs since our price formatting assumes USD base
                            val tickers = allTickers.filter { it.instId.endsWith("-USDT") }
                            if (tickers.isNotEmpty()) {
                                loaded = true
                                val map = _tickerMap.value.toMutableMap()
                                tickers.forEach { map[it.instId] = it }
                                _tickerMap.value = map
                                _currentInstIds.value = tickers.map { it.instId }
                                emitUiNow()
                                updateWebSocketSubscriptions()
                            }
                        }

                    if (!loaded) {
                        repository.fetchInstruments("SPOT")
                            .catch { /* ignore */ }
                            .collect { allInstruments ->
                                // Filter only USDT pairs
                                val instruments = allInstruments.filter { it.instId.endsWith("-USDT") }
                                if (instruments.isNotEmpty()) {
                                    loaded = true
                                    val ids = instruments.map { it.instId }
                                    initializeTickerPlaceholders(ids)
                                    _currentInstIds.value = ids
                                    emitUiNow()
                                    updateWebSocketSubscriptions()
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
        updateWebSocketSubscriptions()
    }

    fun toggleThemeMode() {
        settingsRepository.toggleThemeMode()
    }

    private fun updateWebSocketSubscriptions() {
        val q = _searchQuery.value.trim()
        val ids = _currentInstIds.value
        val map = _tickerMap.value

        val wsIds = if (q.isNotEmpty()) {
            val searchResults = map.values.filter { 
                val baseCoin = it.instId.substringBefore("-")
                baseCoin.contains(q, true) && it.instId.endsWith("-USDT") 
            }
            searchResults.sortedByDescending { it.volCcy24h.toDoubleOrNull() ?: 0.0 }.take(60).map { it.instId }
        } else {
            ids.take(60)
        }
        
        webSocketManager.replaceTickerSubscriptions(wsIds)
    }

    fun toggleCurrency() {
        settingsRepository.toggleCurrency()
    }

    fun fetchTickers() {
        loadTab(_tab.value)
    }

    private fun emitUiNow() {
        val map = _tickerMap.value
        val ids = _currentInstIds.value
        val q = _searchQuery.value.trim()
        val f = _favorites.value
        val tab = _tab.value
        val currency = settingsRepository.currencyFlow.value
        val theme = settingsRepository.themeModeFlow.value

        val tickers = if (q.isNotEmpty()) {
            map.values.filter { 
                val baseCoin = it.instId.substringBefore("-")
                baseCoin.contains(q, true) && it.instId.endsWith("-USDT") 
            }
        } else {
            ids.mapNotNull { map[it] }
        }

        // Sort: favorites first; Top List preserves CMC-like order, All Coin by volume (USDT)
        val sorted = if (tab == MarketTab.TOP_LIST && q.isEmpty()) {
            // Top List: keep hardcoded order (mirrors CMC market cap ranking)
            tickers.sortedWith(
                compareByDescending<Ticker> { f.contains(it.instId) }
                    .thenBy { ids.indexOf(it.instId) }
            )
        } else {
            // All Coin or Searching: sort by 24h trading volume in USDT (proxy for market cap)
            tickers.sortedWith(
                compareByDescending<Ticker> { f.contains(it.instId) }
                    .thenByDescending { it.volCcy24h.toDoubleOrNull() ?: 0.0 }
            )
        }

        _uiState.value = MarketUiState.Success(
            tickers = sorted,
            favorites = f,
            currency = currency,
            themeMode = theme,
            tab = tab,
            totalCount = tickers.size
        )
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.disconnect()
    }

    companion object {
        var currentIdrRate = 16_300.0 // Default fallback

        fun convertPrice(usdPrice: String, currency: Currency): String {
            val price = usdPrice.toDoubleOrNull() ?: return usdPrice
            if (price == 0.0) return if (currency == Currency.USD) "$0.00" else "Rp 0"
            return when (currency) {
                Currency.USD -> "$${formatNumber(price, false)}"
                Currency.IDR -> "Rp ${formatNumber(price * currentIdrRate, true)}"
            }
        }

        fun formatNumber(value: Double, isIdr: Boolean = false): String {
            if (isIdr) {
                val format = java.text.NumberFormat.getInstance(java.util.Locale("id", "ID"))
                format.maximumFractionDigits = 0
                return format.format(value)
            }
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
