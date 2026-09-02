package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.FavoritesLocalDataSource
import kotlinx.coroutines.flow.Flow

class GetFavoritePhotoIdsUseCase(
    private val dataSource: FavoritesLocalDataSource
) {
    operator fun invoke(): Flow<Set<Long>> {
        return dataSource.favoritesFlow
    }
}
