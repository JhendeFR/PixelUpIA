package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.data.local.room.SmartPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GenerateSmartMemoriesUseCase(
    private val smartGalleryDao: SmartGalleryDao,
    private val mobileClipDataSource: com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource? = null
) {

    // Anclas semanticas precalculadas en ingles para clasificacion optima de eventos con MobileCLIP
    private val eventAnchors = listOf(
        "a photo of a party, concert, or celebration with music" to "Noche de celebracion",
        "a photo of delicious food, restaurant, cafe, or dining" to "Salida gastronomica",
        "a photo of outdoor travel, mountains, beach, or nature" to "Paseo al aire libre",
        "a photo of friends or family gathering" to "Reunion especial"
    )

    suspend operator fun invoke(): List<MemoryStoryEntity> = withContext(Dispatchers.IO) {
        val stories = generateAllStoriesInternal()

        // Guardar las historias generadas en Room preservando las fijadas y manuales
        val preservedStories = smartGalleryDao.getPreservedStories()
        val preservedTitles = preservedStories.map { it.title.lowercase(Locale.ROOT) }.toSet()

        val filteredStories = stories.filterNot { preservedTitles.contains(it.title.lowercase(Locale.ROOT)) }

        if (filteredStories.isNotEmpty()) {
            smartGalleryDao.deleteUnpinnedAutoStories()
            smartGalleryDao.insertStories(filteredStories)
        }

        preservedStories + filteredStories
    }

    suspend fun generateCandidateMemories(): List<MemoryStoryEntity> = withContext(Dispatchers.IO) {
        val candidateStories = generateAllStoriesInternal()
        val existingStories = smartGalleryDao.getAllStoriesList()
        val existingTitles = existingStories.map { it.title.lowercase(Locale.ROOT).trim() }.toSet()
        candidateStories.filterNot { existingTitles.contains(it.title.lowercase(Locale.ROOT).trim()) }
    }

    private suspend fun generateAllStoriesInternal(): List<MemoryStoryEntity> {
        val stories = mutableListOf<MemoryStoryEntity>()

        val allPhotos = smartGalleryDao.getAllPhotosList()
        val validPhotos = allPhotos.filter { isCleanPhoto(it) }
        val photoMap = allPhotos.associateBy { it.photoId }

        // 1. Recuerdos por Personas Frecuentes (photoCount >= 4)
        val allClusters = smartGalleryDao.getAllClustersList()
        val personClusters = allClusters.filter { !it.isPet && !it.isHidden && it.photoCount >= 4 }

        for (cluster in personClusters.take(3)) {
            val refs = smartGalleryDao.getCrossRefsForCluster(cluster.clusterId)
                .filter { !it.isExcluded }
            val clusterPhotos = refs.mapNotNull { photoMap[it.photoId] }
                .filter { isCleanPhoto(it) }

            if (clusterPhotos.size >= 4) {
                val selectedPhotos = clusterPhotos.take(8)
                val coverUri = cluster.coverUri.ifBlank { selectedPhotos.first().uri }
                val title = if (cluster.isUserNamed) {
                    "Recuerdos con ${cluster.name}"
                } else {
                    "Momentos con ${cluster.name}"
                }
                val subtitle = "${selectedPhotos.size} fotos destacadas"

                stories.add(
                    MemoryStoryEntity(
                        title = title,
                        subtitle = subtitle,
                        coverUri = coverUri,
                        photoIds = selectedPhotos.map { it.photoId },
                        storyType = "PERSON"
                    )
                )
            }
        }

        // 2. Recuerdos por Mascotas
        val petClusters = allClusters.filter { it.isPet && !it.isHidden }
        for (cluster in petClusters.take(2)) {
            val refs = smartGalleryDao.getCrossRefsForCluster(cluster.clusterId)
                .filter { !it.isExcluded }
            val clusterPhotos = refs.mapNotNull { photoMap[it.photoId] }
                .filter { isCleanPhoto(it) }

            if (clusterPhotos.size >= 2) {
                val selectedPhotos = clusterPhotos.take(8)
                val coverUri = cluster.coverUri.ifBlank { selectedPhotos.first().uri }
                val title = "Aventuras con ${cluster.name}"
                val subtitle = "${selectedPhotos.size} fotos adorables"

                stories.add(
                    MemoryStoryEntity(
                        title = title,
                        subtitle = subtitle,
                        coverUri = coverUri,
                        photoIds = selectedPhotos.map { it.photoId },
                        storyType = "PET"
                    )
                )
            }
        }

        // 3. Recuerdos de Flashback ("Un dia como hoy")
        val todayCalendar = Calendar.getInstance()
        val currentYear = todayCalendar.get(Calendar.YEAR)
        val currentMonth = todayCalendar.get(Calendar.MONTH)
        val currentDay = todayCalendar.get(Calendar.DAY_OF_MONTH)

        val pastPhotos = validPhotos.filter { photo ->
            val cal = Calendar.getInstance().apply { timeInMillis = photo.dateAdded }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)
            year < currentYear && month == currentMonth && kotlin.math.abs(day - currentDay) <= 1
        }

        if (pastPhotos.size >= 3) {
            val calFirst = Calendar.getInstance().apply { timeInMillis = pastPhotos.first().dateAdded }
            val diffYears = currentYear - calFirst.get(Calendar.YEAR)
            val title = if (diffYears == 1) "Hace 1 ano" else if (diffYears > 1) "Hace $diffYears anos" else "Un dia como hoy"
            val selectedPhotos = pastPhotos.take(8)

            stories.add(
                MemoryStoryEntity(
                    title = title,
                    subtitle = "Revive este momento",
                    coverUri = selectedPhotos.first().uri,
                    photoIds = selectedPhotos.map { it.photoId },
                    storyType = "FLASHBACK"
                )
            )
        }

        // 4. Recuerdos por Eventos Temporales y Semanticos (ventanas de 4-8 horas con >= 5 capturas)
        val sortedPhotos = validPhotos.sortedBy { it.dateAdded }
        val timeWindows = groupPhotosByTimeWindow(sortedPhotos, windowMillis = 6 * 3600 * 1000L)

        // Cache de embeddings de anclas tematicas
        val anchorEmbeddings = mutableListOf<Pair<FloatArray, String>>()
        if (mobileClipDataSource != null) {
            for ((anchorText, title) in eventAnchors) {
                val emb = mobileClipDataSource.extractTextEmbedding(anchorText).getOrNull()
                if (emb != null) {
                    anchorEmbeddings.add(Pair(emb, title))
                }
            }
        }

        for (window in timeWindows) {
            if (window.size >= 5) {
                val title = classifyEventTitle(window, anchorEmbeddings)
                val dateStr = SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es-ES"))
                    .format(Date(window.first().dateAdded))
                val selectedPhotos = window.take(10)

                stories.add(
                    MemoryStoryEntity(
                        title = title,
                        subtitle = "Evento del $dateStr",
                        coverUri = selectedPhotos.first().uri,
                        photoIds = selectedPhotos.map { it.photoId },
                        storyType = "EVENT"
                    )
                )
            }
        }

        return stories
    }

    private fun isCleanPhoto(photo: SmartPhotoEntity): Boolean {
        val folder = photo.folderName.lowercase(Locale.ROOT)
        val name = photo.name.lowercase(Locale.ROOT)
        if (folder.contains("screenshot") || folder == "screenshots" || name.contains("screenshot") || photo.sourceApp != null) {
            return false
        }
        val ocrBlockCount = photo.boundingBoxes?.let { json ->
            Regex("""\{"text":""").findAll(json).count()
        } ?: 0
        if (ocrBlockCount > 8) {
            return false
        }
        return photo.uri.isNotBlank()
    }

    private fun groupPhotosByTimeWindow(photos: List<SmartPhotoEntity>, windowMillis: Long): List<List<SmartPhotoEntity>> {
        val groups = mutableListOf<List<SmartPhotoEntity>>()
        var currentGroup = mutableListOf<SmartPhotoEntity>()

        for (photo in photos) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(photo)
            } else {
                val diff = photo.dateAdded - currentGroup.first().dateAdded
                if (diff in 0..windowMillis) {
                    currentGroup.add(photo)
                } else {
                    if (currentGroup.size >= 4) {
                        groups.add(currentGroup)
                    }
                    currentGroup = mutableListOf(photo)
                }
            }
        }
        if (currentGroup.size >= 4) {
            groups.add(currentGroup)
        }
        return groups
    }

    private fun classifyEventTitle(
        photos: List<SmartPhotoEntity>,
        anchorEmbeddings: List<Pair<FloatArray, String>>
    ): String {
        // 1. Intentar clasificacion semantica por centroide de embeddings MobileCLIP
        if (anchorEmbeddings.isNotEmpty()) {
            val validVectors = photos.mapNotNull { photo ->
                val base64 = photo.semanticEmbedding ?: return@mapNotNull null
                try {
                    com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.base64ToEmbedding(base64)
                } catch (e: Exception) {
                    null
                }
            }

            if (validVectors.isNotEmpty()) {
                val dim = validVectors.first().size
                val centroid = FloatArray(dim)
                for (vec in validVectors) {
                    for (i in 0 until dim) {
                        centroid[i] += vec[i]
                    }
                }
                for (i in 0 until dim) {
                    centroid[i] /= validVectors.size.toFloat()
                }
                val normalizedCentroid = com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.l2Normalize(centroid)

                var bestTitle = ""
                var bestSim = 0.0f
                for ((anchorVec, title) in anchorEmbeddings) {
                    val sim = com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.cosineSimilarity(normalizedCentroid, anchorVec)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestTitle = title
                    }
                }

                if (bestSim >= 0.18f && bestTitle.isNotBlank()) {
                    return bestTitle
                }
            }
        }

        // 2. Fallback por coincidencia de etiquetas si no hay embeddings suficientes
        var foodScore = 0
        var natureScore = 0
        var partyScore = 0

        for (photo in photos) {
            for (label in photo.labels) {
                val lower = label.lowercase(Locale.ROOT)
                if (lower.contains("comida") || lower.contains("cena") || lower.contains("almuerzo") ||
                    lower.contains("restaurante") || lower.contains("cafe") || lower.contains("pizza") ||
                    lower.contains("pastel") || lower.contains("postre")) {
                    foodScore++
                }
                if (lower.contains("playa") || lower.contains("montana") || lower.contains("naturaleza") ||
                    lower.contains("parque") || lower.contains("paisaje") || lower.contains("aire libre") ||
                    lower.contains("flor") || lower.contains("bosque")) {
                    natureScore++
                }
                if (lower.contains("fiesta") || lower.contains("evento") || lower.contains("musica") ||
                    lower.contains("concierto") || lower.contains("celebracion") || lower.contains("boda") ||
                    lower.contains("cumpleanos")) {
                    partyScore++
                }
            }
        }

        return when {
            foodScore >= 2 && foodScore >= natureScore && foodScore >= partyScore -> "Salida gastronomica"
            natureScore >= 2 && natureScore >= foodScore && natureScore >= partyScore -> "Paseo al aire libre"
            partyScore >= 2 && partyScore >= foodScore && partyScore >= natureScore -> "Noche de celebracion"
            else -> "Un dia especial"
        }
    }
}
