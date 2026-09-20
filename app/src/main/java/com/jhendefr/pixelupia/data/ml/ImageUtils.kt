package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build

object ImageUtils {

    fun loadCorrectlyOrientedBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val width = info.size.width
                    val height = info.size.height
                    val maxDim = maxOf(width, height)
                    if (maxDim > maxDimension) {
                        val sampleSize = (maxDim / maxDimension).coerceAtLeast(1)
                        decoder.setTargetSampleSize(sampleSize)
                    }
                }
            } else {
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOptions)
                }
                val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                var sample = 1
                while (maxDim / sample > maxDimension) {
                    sample *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
                val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                } ?: return null

                val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL

                val matrix = Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }
                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    if (rotated != rawBitmap) rawBitmap.recycle()
                    rotated
                } else {
                    rawBitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
