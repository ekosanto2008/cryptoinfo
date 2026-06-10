package com.santoso.tech.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.santoso.tech.data.local.dao.FavoriteDao
import com.santoso.tech.data.local.entity.FavoritePair

@Database(entities = [FavoritePair::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
