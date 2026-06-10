package com.santoso.tech.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class Currency { USD, IDR }
enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _currencyFlow = MutableStateFlow(
        Currency.valueOf(prefs.getString("currency", Currency.USD.name) ?: Currency.USD.name)
    )
    val currencyFlow: StateFlow<Currency> = _currencyFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    fun setCurrency(currency: Currency) {
        prefs.edit().putString("currency", currency.name).apply()
        _currencyFlow.value = currency
    }

    fun toggleCurrency() {
        val next = if (_currencyFlow.value == Currency.USD) Currency.IDR else Currency.USD
        setCurrency(next)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _themeModeFlow.value = mode
    }

    fun toggleThemeMode() {
        val next = when (_themeModeFlow.value) {
            ThemeMode.SYSTEM, ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
        }
        setThemeMode(next)
    }
}
