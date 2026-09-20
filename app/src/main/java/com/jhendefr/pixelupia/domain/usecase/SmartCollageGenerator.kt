package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.CollageTemplate
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SmartCollageItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SmartCollageGenerator {

    fun generateSuggestedCollages(photos: List<Photo>): List<SmartCollageItem> {
        if (photos.size < 3) return emptyList()

        val collages = mutableListOf<SmartCollageItem>()
        val calendarNow = Calendar.getInstance()
        val currentMonth = calendarNow.get(Calendar.MONTH)
        val currentDay = calendarNow.get(Calendar.DAY_OF_MONTH)
        val currentYear = calendarNow.get(Calendar.YEAR)

        // 1. "Un dia como hoy" (fotos de anos anteriores en esta misma fecha)
        val onThisDayPhotos = photos.filter { photo ->
            if (photo.dateTaken <= 0L) return@filter false
            val cal = Calendar.getInstance().apply { timeInMillis = photo.dateTaken }
            val photoYear = cal.get(Calendar.YEAR)
            val photoMonth = cal.get(Calendar.MONTH)
            val photoDay = cal.get(Calendar.DAY_OF_MONTH)

            photoYear < currentYear && photoMonth == currentMonth && Math.abs(photoDay - currentDay) <= 1
        }

        if (onThisDayPhotos.size >= 3) {
            val template = if (onThisDayPhotos.size >= 4) CollageTemplate.GRID_2X2 else CollageTemplate.DOMINANT_WITH_THUMBS
            val sample = onThisDayPhotos.take(4)
            val cal = Calendar.getInstance().apply { timeInMillis = sample.first().dateTaken }
            val yearsAgo = currentYear - cal.get(Calendar.YEAR)
            val yearText = if (yearsAgo == 1) "hace 1 ano" else "hace $yearsAgo anos"

            collages.add(
                SmartCollageItem(
                    id = "on_this_day",
                    title = "Un dia como hoy",
                    subtitle = "$yearText - ${sample.size} fotos",
                    template = template,
                    photos = sample
                )
            )
        }

        // 2. Agrupamiento por fecha reciente (Fin de semana / Eventos del ultimo mes)
        val dateGroupFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val readableDateFormat = SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es-ES"))

        val groupedByDay = photos
            .filter { it.dateTaken > 0L }
            .groupBy { dateGroupFormat.format(Date(it.dateTaken)) }
            .toList()
            .sortedByDescending { it.first }

        for ((_, dayPhotos) in groupedByDay) {
            if (dayPhotos.size >= 3 && collages.size < 4) {
                val sample = dayPhotos.take(4)
                val template = when (sample.size) {
                    4 -> CollageTemplate.GRID_2X2
                    3 -> CollageTemplate.TRIPTYCH
                    else -> CollageTemplate.DOMINANT_WITH_THUMBS
                }

                val dateStr = try {
                    readableDateFormat.format(Date(sample.first().dateTaken))
                } catch (_: Exception) {
                    "Recuerdos"
                }

                collages.add(
                    SmartCollageItem(
                        id = "day_${sample.first().id}",
                        title = "Momentos de $dateStr",
                        subtitle = "${sample.size} mejores capturas",
                        template = template,
                        photos = sample
                    )
                )
            }
        }

        // Si aun no tenemos suficientes collages y hay al menos 3 fotos en la coleccion
        if (collages.isEmpty() && photos.size >= 3) {
            val sample = photos.take(4)
            val template = if (sample.size >= 4) CollageTemplate.GRID_2X2 else CollageTemplate.DOMINANT_WITH_THUMBS
            collages.add(
                SmartCollageItem(
                    id = "recent_highlights",
                    title = "Momentos Destacados",
                    subtitle = "Seleccion inteligente de tu galeria",
                    template = template,
                    photos = sample
                )
            )
        }

        return collages
    }
}
