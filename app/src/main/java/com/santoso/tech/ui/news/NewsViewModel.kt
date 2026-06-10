package com.santoso.tech.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santoso.tech.data.model.NewsArticle
import com.santoso.tech.data.repository.NewsCategory
import com.santoso.tech.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NewsUiState {
    object Loading : NewsUiState()
    data class Success(val articles: List<NewsArticle>) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
    object Empty : NewsUiState()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(NewsCategory.LATEST)
    val selectedCategory: StateFlow<NewsCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _bookmarkedUrls = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedUrls: StateFlow<Set<String>> = _bookmarkedUrls.asStateFlow()

    init {
        // Re-fetch when category or search query changes (debounce search)
        combine(
            _selectedCategory,
            _searchQuery.debounce(400)
        ) { category, query -> Pair(category, query) }
            .onEach { (category, query) -> fetchNews(category, query) }
            .launchIn(viewModelScope)
    }

    fun selectCategory(category: NewsCategory) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        fetchNews(_selectedCategory.value, _searchQuery.value)
    }

    private fun fetchNews(category: NewsCategory, query: String) {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading
            newsRepository.getNews(category, query)
                .catch { e ->
                    _uiState.value = NewsUiState.Error(e.message ?: "Gagal memuat berita")
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { articles ->
                            // Merge bookmark status
                            val bookmarks = _bookmarkedUrls.value
                            val merged = articles.map { it.copy(isBookmarked = it.url in bookmarks) }
                            _uiState.value = if (merged.isEmpty()) NewsUiState.Empty
                            else NewsUiState.Success(merged)
                        },
                        onFailure = { e ->
                            _uiState.value = NewsUiState.Error(e.message ?: "Gagal memuat berita")
                        }
                    )
                }
        }

        // Also keep bookmark status updated
        viewModelScope.launch {
            newsRepository.getBookmarks().collect { bookmarks ->
                _bookmarkedUrls.value = bookmarks.map { it.url }.toSet()
            }
        }
    }

    fun toggleBookmark(article: NewsArticle) {
        viewModelScope.launch {
            if (article.isBookmarked || article.url in _bookmarkedUrls.value) {
                newsRepository.removeBookmark(article.url)
            } else {
                newsRepository.addBookmark(article)
            }
        }
    }
}
