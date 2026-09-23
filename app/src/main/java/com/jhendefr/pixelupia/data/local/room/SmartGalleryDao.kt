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

    @Query("SELECT * FROM smart_photos ORDER BY dateAdded DESC")
    suspend fun getAllPhotosList(): List<SmartPhotoEntity>

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
        SELECT p.* FROM smart_photos p
        JOIN smart_photos_fts fts ON p.photoId = fts.rowid
        WHERE smart_photos_fts MATCH :query
        ORDER BY p.dateAdded DESC
    """)
    suspend fun searchPhotosByTextList(query: String): List<SmartPhotoEntity>

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
            semanticEmbedding = :semanticEmbedding,
            isEnriched = 1 
        WHERE photoId = :photoId
    """)
    suspend fun updateEnrichment(
        photoId: Long,
        hasFaces: Boolean,
        barcodes: List<String>,
        labels: List<String>,
        entities: List<String>,
        searchContent: String,
        semanticEmbedding: String? = null
    ): Int

    @Query("SELECT * FROM smart_photos WHERE semanticEmbedding IS NOT NULL")
    suspend fun getPhotosWithEmbedding(): List<SmartPhotoEntity>

    @Query("SELECT * FROM smart_photos WHERE semanticEmbedding IS NOT NULL ORDER BY dateAdded DESC")
    fun getPhotosWithEmbeddingFlow(): Flow<List<SmartPhotoEntity>>

    @Query("UPDATE smart_photos SET semanticEmbedding = :embedding WHERE photoId = :photoId")
    suspend fun updateSemanticEmbedding(photoId: Long, embedding: String): Int

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

    @Query("""
        UPDATE smart_photos 
        SET labels = :labels,
            searchContent = :searchContent
        WHERE photoId = :photoId
    """)
    suspend fun updatePhotoLabelsAndSearch(
        photoId: Long,
        labels: List<String>,
        searchContent: String
    ): Int

    @Query("""
        UPDATE smart_photos 
        SET labels = :labels,
            searchContent = :searchContent,
            excludedTags = :excludedTags
        WHERE photoId = :photoId
    """)
    suspend fun updatePhotoTagsAndExclusions(
        photoId: Long,
        labels: List<String>,
        searchContent: String,
        excludedTags: List<String>
    ): Int

    @Query("SELECT COUNT(DISTINCT photoId) FROM photo_cluster_cross_ref WHERE clusterId = :clusterId AND isExcluded = 0")
    suspend fun getClusterPhotoCount(clusterId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM photo_cluster_cross_ref WHERE clusterId = :clusterId AND photoId = :photoId AND isExcluded = 0)")
    suspend fun hasPhotoInCluster(clusterId: Long, photoId: Long): Boolean

    @Query("""
        UPDATE photo_cluster_cross_ref 
        SET isExcluded = 1 
        WHERE photoId = :photoId 
          AND clusterId IN (SELECT clusterId FROM face_clusters WHERE isPet = 1)
    """)
    suspend fun excludePhotoFromAllPetClusters(photoId: Long): Int

    @Query("SELECT * FROM smart_photos WHERE photoId IN (:photoIds)")
    suspend fun getPhotosByIds(photoIds: List<Long>): List<SmartPhotoEntity>

    // Gestion de Clusters de Personas y Mascotas
    @Query("""
        SELECT * FROM face_clusters 
        WHERE (photoCount >= 2 OR isUserNamed = 1) AND isHidden = 0 
        ORDER BY photoCount DESC, updatedAt DESC
    """)
    fun getAllClusters(): Flow<List<FaceClusterEntity>>

    @Query("SELECT * FROM face_clusters WHERE isHidden = 0 ORDER BY photoCount DESC, updatedAt DESC")
    fun getAllClustersUnfiltered(): Flow<List<FaceClusterEntity>>

    @Query("SELECT * FROM face_clusters WHERE isHidden = 0 ORDER BY photoCount DESC, updatedAt DESC")
    suspend fun getAllClustersList(): List<FaceClusterEntity>

    @Query("SELECT * FROM face_clusters WHERE isPet = :isPet AND isHidden = 0 ORDER BY photoCount DESC, updatedAt DESC")
    fun getClustersByType(isPet: Boolean): Flow<List<FaceClusterEntity>>

    @Query("SELECT * FROM face_clusters WHERE clusterId = :clusterId")
    suspend fun getClusterById(clusterId: Long): FaceClusterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCluster(cluster: FaceClusterEntity): Long

    @Update
    suspend fun updateCluster(cluster: FaceClusterEntity): Int

    @Query("UPDATE face_clusters SET name = :name, isUserNamed = 1, updatedAt = :updatedAt WHERE clusterId = :clusterId")
    suspend fun updateClusterName(clusterId: Long, name: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM face_clusters WHERE clusterId = :clusterId")
    suspend fun deleteCluster(clusterId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: PhotoClusterCrossRefEntity): Long

    @Query("DELETE FROM photo_cluster_cross_ref WHERE photoId = :photoId AND clusterId = :clusterId")
    suspend fun deleteCrossRef(photoId: Long, clusterId: Long): Int

    @Query("DELETE FROM photo_cluster_cross_ref WHERE clusterId = :clusterId")
    suspend fun deleteCrossRefsForCluster(clusterId: Long): Int

    @Query("SELECT * FROM photo_cluster_cross_ref WHERE photoId = :photoId AND clusterId = :clusterId")
    suspend fun getCrossRef(photoId: Long, clusterId: Long): PhotoClusterCrossRefEntity?

    @Query("SELECT * FROM photo_cluster_cross_ref WHERE clusterId = :clusterId")
    suspend fun getCrossRefsForCluster(clusterId: Long): List<PhotoClusterCrossRefEntity>

    @Query("UPDATE face_clusters SET isHidden = 1 WHERE clusterId = :clusterId")
    suspend fun hideCluster(clusterId: Long): Int

    @Query("SELECT clusterId FROM photo_cluster_cross_ref WHERE photoId = :photoId AND isExcluded = 1")
    suspend fun getExcludedClusterIdsForPhoto(photoId: Long): List<Long>

    @Query("""
        SELECT p.* FROM smart_photos p
        JOIN photo_cluster_cross_ref ref ON p.photoId = ref.photoId
        WHERE ref.clusterId = :clusterId AND ref.isExcluded = 0
        ORDER BY p.dateAdded DESC
    """)
    fun getPhotosForCluster(clusterId: Long): Flow<List<SmartPhotoEntity>>

    @Query("""
        SELECT c.* FROM face_clusters c
        JOIN photo_cluster_cross_ref ref ON c.clusterId = ref.clusterId
        WHERE ref.photoId = :photoId AND ref.isExcluded = 0
    """)
    fun getClustersForPhoto(photoId: Long): Flow<List<FaceClusterEntity>>

    // Gestion de Historias y Recuerdos Inteligentes
    @Query("SELECT * FROM memory_stories ORDER BY isPinned DESC, createdAt DESC")
    fun getAllStories(): Flow<List<MemoryStoryEntity>>

    @Query("SELECT * FROM memory_stories ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllStoriesList(): List<MemoryStoryEntity>

    @Query("SELECT * FROM memory_stories WHERE storyId = :storyId")
    suspend fun getStoryById(storyId: Long): MemoryStoryEntity?

    @Query("SELECT * FROM memory_stories WHERE isPinned = 1 OR isManual = 1")
    suspend fun getPreservedStories(): List<MemoryStoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: MemoryStoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<MemoryStoryEntity>): List<Long>

    @Query("UPDATE memory_stories SET title = :title WHERE storyId = :storyId")
    suspend fun updateStoryTitle(storyId: Long, title: String): Int

    @Query("UPDATE memory_stories SET photoIds = :photoIds, subtitle = :subtitle, coverUri = :coverUri WHERE storyId = :storyId")
    suspend fun updateStoryPhotos(storyId: Long, photoIds: List<Long>, subtitle: String, coverUri: String): Int

    @Query("UPDATE memory_stories SET isPinned = :isPinned WHERE storyId = :storyId")
    suspend fun updateStoryPinned(storyId: Long, isPinned: Boolean): Int

    @Query("DELETE FROM memory_stories WHERE isPinned = 0 AND isManual = 0")
    suspend fun deleteUnpinnedAutoStories(): Int

    @Query("DELETE FROM memory_stories")
    suspend fun clearAllStories(): Int

    @Query("DELETE FROM memory_stories WHERE storyId = :storyId")
    suspend fun deleteStoryById(storyId: Long): Int
}
