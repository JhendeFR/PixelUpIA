package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class DetectedBarcodeItem(
    val id: String,
    val value: String,
    val formatName: String,
    val box: Rect?
)

data class BarcodeScanningResult(
    val barcodes: List<DetectedBarcodeItem>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

class BarcodeDataSource(private val context: Context) {

    private val scanner = BarcodeScanning.getClient()

    suspend fun scanBarcodes(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        scanBarcodesDetailed(uri).barcodes.map { it.value }
    }

    suspend fun scanBarcodesDetailed(uri: Uri): BarcodeScanningResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val imgWidth = image.width
        val imgHeight = image.height

        suspendCancellableCoroutine { continuation ->
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val result = barcodes.mapIndexedNotNull { index, barcode ->
                        val value = barcode.rawValue ?: barcode.displayValue ?: return@mapIndexedNotNull null
                        if (value.isBlank()) return@mapIndexedNotNull null

                        val formatName = when (barcode.format) {
                            Barcode.FORMAT_QR_CODE -> "QR"
                            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8 -> "EAN"
                            Barcode.FORMAT_CODE_128 -> "Code 128"
                            Barcode.FORMAT_PDF417 -> "PDF417"
                            else -> "Codigo"
                        }

                        DetectedBarcodeItem(
                            id = "barcode_$index",
                            value = value,
                            formatName = formatName,
                            box = barcode.boundingBox
                        )
                    }
                    continuation.resume(
                        BarcodeScanningResult(
                            barcodes = result,
                            imageWidth = imgWidth,
                            imageHeight = imgHeight
                        )
                    )
                }
                .addOnFailureListener {
                    continuation.resume(
                        BarcodeScanningResult(
                            barcodes = emptyList(),
                            imageWidth = imgWidth,
                            imageHeight = imgHeight
                        )
                    )
                }
        }
    }
}
