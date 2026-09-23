package com.jhendefr.pixelupia.data.ml

/**
 * Utilidades de conversion bitwise de alta precision entre Float32 (IEEE 754 precision simple)
 * y Float16 (IEEE 754 media precision) para tensores de modelos de Deep Learning en Edge AI.
 * Compatible con cualquier version de Android (independiente de API level 33).
 */
object Float16Utils {

    /**
     * Convierte un valor Float32 (4 bytes) a su representacion binaria Float16 empaquetada en un Short (2 bytes).
     */
    fun float32ToFloat16(value: Float): Short {
        val fbits = java.lang.Float.floatToIntBits(value)
        val sign = (fbits ushr 16) and 0x8000
        var valExp = ((fbits ushr 23) and 0xFF) - (127 - 15)
        var mant = fbits and 0x007FFFFF

        if (valExp <= 0) {
            if (valExp < -10) return sign.toShort()
            mant = mant or 0x00800000
            val t = 14 - valExp
            val a = (1 shl (t - 1)) - 1
            val b = (mant shr t) and 1
            mant = (mant + a + b) shr t
            return (sign or mant).toShort()
        } else if (valExp == 0xFF - (127 - 15)) {
            if (mant == 0) return (sign or 0x7C00).toShort()
            mant = mant shr 13
            return (sign or 0x7C00 or mant or (if (mant == 0) 1 else 0)).toShort()
        } else {
            mant = mant + 0x0FFF + ((mant shr 13) and 1)
            if ((mant and 0x00800000) != 0) {
                mant = 0
                valExp += 1
            }
            if (valExp > 30) return (sign or 0x7C00).toShort()
            return (sign or (valExp shl 10) or (mant shr 13)).toShort()
        }
    }

    /**
     * Convierte un valor Float16 binario empaquetado en un Short (2 bytes) a un Float32 utilizable.
     */
    fun float16ToFloat32(hbits: Short): Float {
        var mant = hbits.toInt() and 0x03FF
        var exp = hbits.toInt() and 0x7C00
        if (exp == 0x7C00) {
            exp = 0x3FC00
        } else if (exp != 0) {
            exp += 0x1C000
            if (mant == 0 && exp == 0x1C000) {
                return java.lang.Float.intBitsToFloat((hbits.toInt() and 0x8000) shl 16)
            }
        } else if (mant != 0) {
            exp = 0x1C400
            do {
                mant = mant shl 1
                exp -= 0x400
            } while ((mant and 0x0400) == 0)
            mant = mant and 0x03FF
        }
        return java.lang.Float.intBitsToFloat(
            ((hbits.toInt() and 0x8000) shl 16) or ((exp or mant) shl 13)
        )
    }
}
