package com.jhendefr.pixelupia.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesLocalDataSource(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pixelupia_favorites", Context.MODE_PRIVATE)
    private val key = "favorite_photo_ids"

    private val _favoritesFlow = MutableStateFlow(loadFavorites())
    val favoritesFlow: Flow<Set<Long>> = _favoritesFlow.asStateFlow()

    private fun loadFavorites(): Set<Long> {
        val stringSet = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return stringSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleFavorite(photoId: Long): Boolean {
        val current = loadFavorites().toMutableSet()
        val isNowFavorite = if (current.contains(photoId)) {
            current.remove(photoId)
            false
        } else {
            current.add(photoId)
            true
        }
        prefs.edit().putStringSet(key, current.map { it.toString() }.toSet()).apply()
        _favoritesFlow.value = current
        return isNowFavorite
    }

    fun isFavorite(photoId: Long): Boolean {
        return loadFavorites().contains(photoId)
    }
}
