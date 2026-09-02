package com.jhendefr.pixelupia.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartGalleryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: SmartPhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotoFts(fts: SmartPhotoFtsEntity): Long

    @Query("SELECT * FROM smart_photos WHERE photoId = :photoId")
    suspend fun getPhotoById(photoId: Long): SmartPhotoEntity?

    @Query("SELECT * FROM smart_photos ORDER BY dateAdded DESC")
    fun getAllSmartPhotos(): Flow<List<SmartPhotoEntity>>

    @Query("SELECT photoId FROM smart_photos")
    fun getIndexedPhotoIds(): Flow<List<Long>>

    @Query("SELECT * FROM smart_photos WHERE folderName = :folderName ORDER BY dateAdded DESC")
    fun getIndexedPhotosByFolder(folderName: String): Flow<List<SmartPhotoEntity>>

    @Query("""
        SELECT p.* FROM smart_photos p
        JOIN smart_photos_fts fts ON p.photoId = fts.rowid
        WHERE smart_photos_fts MATCH :query
        ORDER BY p.dateAdded DESC
    """)
    fun searchPhotosByText(query: String): Flow<List<SmartPhotoEntity>>

    @Query("DELETE FROM smart_photos WHERE photoId = :photoId")
    suspend fun deletePhotoById(photoId: Long): Int

    @Query("SELECT COUNT(*) FROM smart_photos")
    suspend fun getCount(): Int
}
