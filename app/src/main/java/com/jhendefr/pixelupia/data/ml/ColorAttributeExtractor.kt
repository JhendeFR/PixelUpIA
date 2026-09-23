package com.jhendefr.pixelupia.data.ml

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.sqrt

/**
 * Extractor cromatico de colores dominantes para subtipos de objetos en espacio perceptual CIELAB.
 * Utiliza una paleta canonica de 12 colores y formula de distancia perceptual Delta E.
 */
object ColorAttributeExtractor {

    data class LabColor(
        val name: String,
        val l: Float,
        val a: Float,
        val b: Float
    )

    // Paleta canonica de 12 colores en RGB convertidos a CIELAB al inicializar
    private val canonicalPalette: List<LabColor> by lazy {
        listOf(
            "rojo" to Triple(220, 20, 30),
            "azul" to Triple(15, 60, 210),
            "celeste" to Triple(70, 180, 240),
            "verde" to Triple(30, 160, 45),
            "amarillo" to Triple(245, 220, 25),
            "negro" to Triple(18, 18, 18),
            "blanco" to Triple(245, 245, 245),
            "gris" to Triple(128, 128, 128),
            "marron" to Triple(115, 60, 25),
            "naranja" to Triple(240, 125, 20),
            "rosa" to Triple(245, 130, 170),
            "violeta" to Triple(130, 35, 170)
        ).map { (name, rgb) ->
            val lab = rgbToLab(rgb.first, rgb.second, rgb.third)
            LabColor(name, lab[0], lab[1], lab[2])
        }
    }

    /**
     * Convierte valores sRGB [0..255] al espacio de color perceptual CIELAB (D65).
     */
    fun rgbToLab(rInt: Int, gInt: Int, bInt: Int): FloatArray {
        // 1. Normalizar y aplicar correccion gamma inversa (sRGB lineal)
        val rLinear = pivotRgb(rInt / 255.0f)
        val gLinear = pivotRgb(gInt / 255.0f)
        val bLinear = pivotRgb(bInt / 255.0f)

        // 2. Transformacion lineal a espacio CIE XYZ (Iluminante D65)
        val x = (rLinear * 0.4124564f + gLinear * 0.3575761f + bLinear * 0.1804375f) / 0.95047f
        val y = (rLinear * 0.2126729f + gLinear * 0.7151522f + bLinear * 0.0721750f) / 1.00000f
        val z = (rLinear * 0.0193339f + gLinear * 0.1191920f + bLinear * 0.9503041f) / 1.08883f

        // 3. Transformacion no lineal a CIELAB
        val fx = pivotXyz(x)
        val fy = pivotXyz(y)
        val fz = pivotXyz(z)

        val l = (116.0f * fy) - 16.0f
        val a = 500.0f * (fx - fy)
        val b = 200.0f * (fy - fz)

        return floatArrayOf(l, a, b)
    }

    private fun pivotRgb(c: Float): Float {
        return if (c > 0.04045f) {
            Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
        } else {
            c / 12.92f
        }
    }

    private fun pivotXyz(t: Float): Float {
        return if (t > 0.008856f) {
            Math.cbrt(t.toDouble()).toFloat()
        } else {
            (7.787f * t) + (16.0f / 116.0f)
        }
    }

    /**
     * Calcula la distancia perceptual Delta E entre dos colores en espacio CIELAB.
     */
    fun deltaE(l1: Float, a1: Float, b1: Float, l2: Float, a2: Float, b2: Float): Float {
        val dl = l1 - l2
        val da = a1 - a2
        val db = b1 - b2
        return sqrt(dl * dl + da * da + db * db)
    }

    /**
     * Extrae el color dominante del objeto descartando un 10% del margen exterior
     * y muestreando en saltos de 2 a 3 px para maxima velocidad y precision.
     */
    fun extractDominantColor(bitmap: Bitmap, box: Rect): String {
        if (bitmap.isRecycled || box.width() <= 4 || box.height() <= 4) {
            return "desconocido"
        }

        // Descartar 10% de margen exterior para aislar el objeto del fondo
        val marginX = (box.width() * 0.10f).toInt().coerceAtLeast(1)
        val marginY = (box.height() * 0.10f).toInt().coerceAtLeast(1)

        val innerLeft = (box.left + marginX).coerceIn(0, bitmap.width - 1)
        val innerTop = (box.top + marginY).coerceIn(0, bitmap.height - 1)
        val innerRight = (box.right - marginX).coerceIn(innerLeft + 1, bitmap.width)
        val innerBottom = (box.bottom - marginY).coerceIn(innerTop + 1, bitmap.height)

        val innerWidth = innerRight - innerLeft
        val innerHeight = innerBottom - innerTop

        if (innerWidth <= 2 || innerHeight <= 2) {
            return "desconocido"
        }

        // Muestreo con paso adaptativo de 2 o 3 px
        val step = if (innerWidth * innerHeight > 10000) 3 else 2

        val colorVotes = mutableMapOf<String, Int>()
        canonicalPalette.forEach { colorVotes[it.name] = 0 }

        for (y in innerTop until innerBottom step step) {
            for (x in innerLeft until innerRight step step) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel shr 24) and 0xFF
                if (alpha < 50) continue // Ignorar pixeles transparentes

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val lab = rgbToLab(r, g, b)
                val pL = lab[0]
                val pA = lab[1]
                val pB = lab[2]

                // Encontrar el color canónico con menor Delta E perceptual
                var minDistance = Float.MAX_VALUE
                var closestColor = "desconocido"

                for (canonical in canonicalPalette) {
                    val dist = deltaE(pL, pA, pB, canonical.l, canonical.a, canonical.b)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestColor = canonical.name
                    }
                }

                if (closestColor != "desconocido") {
                    colorVotes[closestColor] = (colorVotes[closestColor] ?: 0) + 1
                }
            }
        }

        return colorVotes.maxByOrNull { it.value }?.key ?: "desconocido"
    }
}
