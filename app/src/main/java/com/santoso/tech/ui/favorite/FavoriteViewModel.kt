package com.santoso.tech.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santoso.tech.data.model.Ticker
import com.santoso.tech.data.repository.FavoriteRepository
import com.santoso.tech.data.repository.MarketRepository
import com.santoso.tech.ui.market.Currency
import com.santoso.tech.ui.market.MarketViewModel
import com.santoso.tech.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FavoriteUiState {
    object Loading : FavoriteUiState()
    object Empty : FavoriteUiState()
    data class Success(val tickers: List<Ticker>, val currency: Currency) : FavoriteUiState()
    data class Error(val message: String) : FavoriteUiState()
}

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val marketRepository: MarketRepository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavoriteUiState>(FavoriteUiState.Loading)
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    private val _tickers = MutableStateFlow<Map<String, Ticker>>(emptyMap())
    private val _favoriteIds = MutableStateFlow<List<String>>(emptyList())
    private val _currency = MutableStateFlow(Currency.USD)

    init {
        observeFavorites()
        observeWebSocket()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteRepository.getAllFavorites().collect { list ->
                val ids = list.map { it.instId }
                _favoriteIds.value = ids

                if (ids.isEmpty()) {
                    _uiState.value = FavoriteUiState.Empty
                    return@collect
                }

                // Fetch any missing tickers from API
                ids.forEach { id ->
                    if (!_tickers.value.containsKey(id)) {
                        fetchOneTicker(id)
                    }
                }

                // Subscribe WS for real-time updates
                webSocketManager.connect()
                webSocketManager.subscribeTickers(ids)

                emitSuccess()
            }
        }
    }

    private fun fetchOneTicker(id: String) {
        viewModelScope.launch {
            marketRepository.fetchOne(id)
                .catch { }
                .collect { ticker ->
                    val updated = _tickers.value.toMutableMap()
                    updated[id] = ticker
                    _tickers.value = updated
                    emitSuccess()
                }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            webSocketManager.tickerFlow.collect { tick ->
                val ids = _favoriteIds.value
                if (ids.contains(tick.instId)) {
                    val updated = _tickers.value.toMutableMap()
                    updated[tick.instId] = tick
                    _tickers.value = updated
                    emitSuccess()
                }
            }
        }
    }

    private fun emitSuccess() {
        val ids = _favoriteIds.value
        if (ids.isEmpty()) {
            _uiState.value = FavoriteUiState.Empty
            return
        }
        val list = ids.mapNotNull { _tickers.value[it] }
        if (list.isNotEmpty()) {
            _uiState.value = FavoriteUiState.Success(list, _currency.value)
        }
    }

    fun toggleCurrency() {
        _currency.value = if (_currency.value == Currency.USD) Currency.IDR else Currency.USD
        emitSuccess()
    }

    fun removeFavorite(instId: String) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(instId)
        }
    }
}
