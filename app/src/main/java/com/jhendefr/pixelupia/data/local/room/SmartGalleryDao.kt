package com.jhendefr.pixelupia.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartGalleryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: SmartPhotoEntity): Long

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

    @Query("""
        SELECT * FROM smart_photos 
        WHERE isEnriched = 0 
        AND (folderName LIKE '%screenshot%' OR folderName = 'Screenshots')
        ORDER BY dateAdded DESC
    """)
    suspend fun getUnenrichedScreenshots(): List<SmartPhotoEntity>

    @Query("""
        UPDATE smart_photos 
        SET hasFaces = :hasFaces, 
            barcodes = :barcodes, 
            labels = :labels, 
            entities = :entities, 
            searchContent = :searchContent,
            isEnriched = 1 
        WHERE photoId = :photoId
    """)
    suspend fun updateEnrichment(
        photoId: Long,
        hasFaces: Boolean,
        barcodes: List<String>,
        labels: List<String>,
        entities: List<String>,
        searchContent: String
    ): Int

    @Query("SELECT COUNT(*) FROM smart_photos WHERE isEnriched = 1")
    fun getEnrichedCount(): Flow<Int>

    @Query("DELETE FROM smart_photos WHERE photoId = :photoId")
    suspend fun deletePhotoById(photoId: Long): Int

    @Query("SELECT COUNT(*) FROM smart_photos")
    suspend fun getCount(): Int

    @Query("SELECT * FROM smart_photos WHERE sourceApp = :sourceApp ORDER BY dateAdded DESC")
    fun getPhotosBySourceApp(sourceApp: String): Flow<List<SmartPhotoEntity>>

    @Query("SELECT DISTINCT sourceApp FROM smart_photos WHERE sourceApp IS NOT NULL AND sourceApp != ''")
    fun getDistinctSourceApps(): Flow<List<String>>
}
