package com.santoso.tech.data.local.dao

import androidx.room.*
import com.santoso.tech.data.local.entity.FavoritePair
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_pairs")
    fun getAllFavorites(): Flow<List<FavoritePair>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoritePair)

    @Delete
    suspend fun deleteFavorite(favorite: FavoritePair)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_pairs WHERE instId = :instId)")
    fun isFavorite(instId: String): Flow<Boolean>
}
