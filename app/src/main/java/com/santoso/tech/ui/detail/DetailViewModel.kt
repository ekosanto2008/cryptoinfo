package com.santoso.tech.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santoso.tech.data.model.CandleData
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.data.repository.ChartRepository
import com.santoso.tech.data.repository.FavoriteRepository
import com.santoso.tech.data.repository.MarketRepository
import com.santoso.tech.data.repository.SettingsRepository
import com.santoso.tech.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val ticker: Ticker,
        val isFavorite: Boolean,
        val selectedTimeframe: String = "1D",
        val candlesJson: String? = null,
        val latestCandleJson: String? = null
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val chartRepository: ChartRepository,
    private val favoriteRepository: FavoriteRepository,
    private val webSocketManager: WebSocketManager,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val instId: String = checkNotNull(savedStateHandle["instId"])

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    
    val currencyFlow = settingsRepository.currencyFlow
    fun toggleCurrency() = settingsRepository.toggleCurrency()

    private var currentTicker: Ticker? = null
    private var isFavorite: Boolean = false
    private var currentTimeframe: String = "1D"
    private var currentCandlesJson: String? = null
    private var currentLatestCandleJson: String? = null
    private var candleWsJob: Job? = null

    init {
        // Start with a placeholder so WS updates can immediately show
        currentTicker = MarketRepository.placeholderTicker(instId)
        updateUiState()

        fetchTicker()
        fetchChartData("1D")
        observeFavoriteStatus()
        observeTickerWebSocket()
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            favoriteRepository.isFavorite(instId).collect {
                isFavorite = it
                updateUiState()
            }
        }
    }

    private fun observeTickerWebSocket() {
        viewModelScope.launch {
            webSocketManager.connect()
            webSocketManager.subscribeTickers(listOf(instId))
            webSocketManager.tickerFlow
                .filter { it.instId == instId }
                .collect { updatedTicker ->
                    currentTicker = updatedTicker
                    updateUiState()
                }
        }
    }

    /** Subscribe to real-time candle WS for a given timeframe */
    private fun subscribeCandleWs(bar: String) {
        candleWsJob?.cancel()
        candleWsJob = viewModelScope.launch {
            chartRepository.observeRealtimeCandles(instId, bar)
                .catch { /* ignore */ }
                .collect { incoming ->
                    currentLatestCandleJson = Json.encodeToString(incoming)
                    updateUiState()
                }
        }
    }

    private fun updateUiState() {
        currentTicker?.let {
            _uiState.value = DetailUiState.Success(
                ticker = it,
                isFavorite = isFavorite,
                selectedTimeframe = currentTimeframe,
                candlesJson = currentCandlesJson,
                latestCandleJson = currentLatestCandleJson
            )
        }
    }

    fun fetchChartData(bar: String) {
        // Unsubscribe old candle WS
        if (currentTimeframe != bar) {
            chartRepository.unsubscribeRealtimeCandles(instId, currentTimeframe)
        }
        currentTimeframe = bar
        currentCandlesJson = null
        currentLatestCandleJson = null

        viewModelScope.launch {
            try {
                val candles = chartRepository.getInitialCandles(instId, bar)
                currentCandlesJson = Json.encodeToString(candles)
                updateUiState()
                
                // After REST load, subscribe WS for live updates
                subscribeCandleWs(bar)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun refreshData() {
        _uiState.value = DetailUiState.Loading
        fetchTicker()
        fetchChartData(currentTimeframe)
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            if (isFavorite) favoriteRepository.removeFavorite(instId)
            else favoriteRepository.addFavorite(instId)
        }
    }

    fun fetchTicker() {
        viewModelScope.launch {
            repository.fetchOne(instId)
                .catch { /* REST failed, WS will fill data */ }
                .collect { ticker ->
                    currentTicker = ticker
                    updateUiState()
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.unsubscribeTickers(listOf(instId))
        chartRepository.unsubscribeRealtimeCandles(instId, currentTimeframe)
        chartRepository.closeConnection()
        candleWsJob?.cancel()
    }
}
