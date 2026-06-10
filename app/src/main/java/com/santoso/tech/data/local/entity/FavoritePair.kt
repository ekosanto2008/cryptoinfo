package com.santoso.tech.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_pairs")
data class FavoritePair(
    @PrimaryKey val instId: String
)
