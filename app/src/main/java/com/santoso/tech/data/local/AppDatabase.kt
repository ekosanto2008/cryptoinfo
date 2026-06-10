package com.santoso.tech.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.santoso.tech.data.local.dao.FavoriteDao
import com.santoso.tech.data.local.dao.NewsBookmarkDao
import com.santoso.tech.data.local.entity.FavoritePair
import com.santoso.tech.data.local.entity.NewsBookmark

@Database(
    entities = [FavoritePair::class, NewsBookmark::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun newsBookmarkDao(): NewsBookmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `news_bookmarks` (
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `imageUrl` TEXT,
                        `sourceName` TEXT NOT NULL,
                        `publishedAt` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `savedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`url`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
