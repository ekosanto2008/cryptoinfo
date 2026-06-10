package com.santoso.tech.di

import android.content.Context
import androidx.room.Room
import com.santoso.tech.data.local.AppDatabase
import com.santoso.tech.data.local.dao.FavoriteDao
import com.santoso.tech.data.local.dao.NewsBookmarkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "crypto_info_db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    fun provideNewsBookmarkDao(database: AppDatabase): NewsBookmarkDao {
        return database.newsBookmarkDao()
    }
}
