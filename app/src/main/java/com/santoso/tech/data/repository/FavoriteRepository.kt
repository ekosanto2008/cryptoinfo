package com.santoso.tech.data.repository

import com.santoso.tech.data.local.dao.FavoriteDao
import com.santoso.tech.data.local.entity.FavoritePair
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao
) {
    fun getAllFavorites(): Flow<List<FavoritePair>> = favoriteDao.getAllFavorites()

    suspend fun addFavorite(instId: String) {
        favoriteDao.insertFavorite(FavoritePair(instId))
    }

    suspend fun removeFavorite(instId: String) {
        favoriteDao.deleteFavorite(FavoritePair(instId))
    }

    fun isFavorite(instId: String): Flow<Boolean> = favoriteDao.isFavorite(instId)
}
