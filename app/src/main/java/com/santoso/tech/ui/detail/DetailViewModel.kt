package com.santoso.tech.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santoso.tech.data.model.CandleData
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.data.repository.CandleRepository
import com.santoso.tech.data.repository.FavoriteRepository
import com.santoso.tech.data.repository.MarketRepository
import com.santoso.tech.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val ticker: Ticker,
        val isFavorite: Boolean,
        val candles: List<CandleData> = emptyList(),
        val selectedTimeframe: String = "1D"
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val candleRepository: CandleRepository,
    private val favoriteRepository: FavoriteRepository,
    private val webSocketManager: WebSocketManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val instId: String = checkNotNull(savedStateHandle["instId"])

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var currentTicker: Ticker? = null
    private var isFavorite: Boolean = false
    private var currentCandles: MutableList<CandleData> = mutableListOf()
    private var currentTimeframe: String = "1D"
    private var candleWsJob: Job? = null

    init {
        // Start with a placeholder so WS updates can immediately show
        currentTicker = MarketRepository.placeholderTicker(instId)
        updateUiState()

        fetchTicker()
        fetchCandles("1D")
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
            webSocketManager.subscribeCandle(instId, bar)
            webSocketManager.candleFlow
                .filter { it.first == instId }
                .collect { (_, incoming) ->
                    // The WS sends the LATEST candle for current bar.
                    // Update the last entry if same timestamp, else append.
                    if (currentCandles.isNotEmpty()) {
                        val last = currentCandles.last()
                        if (last.timestamp == incoming.timestamp) {
                            currentCandles[currentCandles.lastIndex] = incoming
                        } else {
                            currentCandles.add(incoming)
                            // Keep list manageable
                            if (currentCandles.size > 120) currentCandles.removeAt(0)
                        }
                    } else {
                        currentCandles.add(incoming)
                    }
                    updateUiState()
                }
        }
    }

    private fun updateUiState() {
        currentTicker?.let {
            _uiState.value = DetailUiState.Success(
                ticker = it,
                isFavorite = isFavorite,
                candles = currentCandles.toList(),
                selectedTimeframe = currentTimeframe
            )
        }
    }

    fun fetchCandles(bar: String) {
        // Unsubscribe old candle WS
        if (currentTimeframe != bar) {
            webSocketManager.unsubscribeCandle(instId, currentTimeframe)
        }
        currentTimeframe = bar
        currentCandles.clear()

        viewModelScope.launch {
            candleRepository.fetchCandles(instId, bar)
                .catch { /* ignore */ }
                .collect { candles ->
                    currentCandles = candles.toMutableList()
                    updateUiState()
                    // After REST load, subscribe WS for live updates
                    subscribeCandleWs(bar)
                }
        }
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
        webSocketManager.unsubscribeCandle(instId, currentTimeframe)
        candleWsJob?.cancel()
    }
}
