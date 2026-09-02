package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.FavoritesLocalDataSource

class ToggleFavoriteUseCase(
    private val dataSource: FavoritesLocalDataSource
) {
    operator fun invoke(photoId: Long): Boolean {
        return dataSource.toggleFavorite(photoId)
    }
}
