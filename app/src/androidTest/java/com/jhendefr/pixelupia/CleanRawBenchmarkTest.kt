package com.jhendefr.pixelupia

import android.Manifest
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * ARNÉS DE PRUEBAS 100% LIMPIO Y AISLADO (SIN DEPENDENCIAS DE LA LÓGICA DE UI/APP)
 *
 * Evalúa de forma independiente los 9 modelos del proyecto:
 * 1. ML Kit Text Recognition
 * 2. ML Kit Face Detection (Modo ACCURATE vs Modo FAST)
 * 3. ML Kit Barcode Scanning
 * 4. ML Kit Image Labeling (Umbral crudo 0.10f para barrido completo en Python)
 * 5. ML Kit Object Detection (5 categorías base: Fashion good, Food, Home good, Place, Plant + Recorte + Color CIELAB)
 * 6. ML Kit Entity Extraction (NER puro en español sin Regex Fallback)
 * 7. MobileCLIP-S0 Vision & Text Encoder (mobileclip_s0_image.tflite y mobileclip_s0_text.tflite)
 * 8. FaceNet Inception-ResNet-v1 (facenet.tflite CON y SIN alineación afín de ojos)
 * 9. MobileNetV3 Large (mobilenet_v3_large.tflite vs MobileCLIP-S0 en recortes de mascotas)
 */
@RunWith(AndroidJUnit4::class)
class CleanRawBenchmarkTest {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val faceDetectorAccurate = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .build()
    )

    private val faceDetectorFast = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.10f)
            .build()
    )

    private val barcodeScanner = BarcodeScanning.getClient()

    private val imageLabelerRaw = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.10f)
            .build()
    )

    private val objectDetectorRaw = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH).build()
    )

    @Test
    fun runCleanIsolatedBenchmark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // Otorgar permisos de lectura de almacenamiento / multimedia automáticamente en runtime
        runCatching {
            val ui = instrumentation.uiAutomation
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ui.grantRuntimePermission(pkg, Manifest.permission.READ_MEDIA_IMAGES)
                ui.executeShellCommand("pm grant $pkg android.permission.READ_MEDIA_IMAGES")
            } else {
                ui.grantRuntimePermission(pkg, Manifest.permission.READ_EXTERNAL_STORAGE)
                ui.executeShellCommand("pm grant $pkg android.permission.READ_EXTERNAL_STORAGE")
            }
        }

        runCatching { Tasks.await(entityExtractor.downloadModelIfNeeded()) }

        val facenetInterpreter = loadTfliteFromAssets(context, "facenet.tflite")
        val mobilenetPetInterpreter = loadTfliteFromAssets(context, "mobilenet_v3_large.tflite")
        val clipImageInterpreter = loadTfliteFromAssets(context, "mobileclip_s0_image.tflite")
        val clipTextInterpreter = runCatching { loadTfliteFromAssets(context, "mobileclip_s0_text.tflite") }.getOrNull()
        val pureTokenizer = if (clipTextInterpreter != null) PureClipBpeTokenizer(context) else null

        val datasetRoot = File(context.getExternalFilesDir(null), "DataSet")
        require(datasetRoot.exists()) {
            "No se encontró DataSet en ${datasetRoot.absolutePath}. Ejecuta: adb push \"C:/Users/jhean/Downloads/Data/DataSet\" /sdcard/Android/data/com.jhendefr.pixelupia/files/DataSet"
        }

        // Si ground_truth.json está en /sdcard/Download/ground_truth.json o dentro de DataSet, cargarlo para evaluar MobileCLIP Text en el dispositivo
        val gtFile = listOf(
            File(datasetRoot, "ground_truth.json"),
            File(context.getExternalFilesDir(null), "ground_truth.json"),
            File("/sdcard/Download/ground_truth.json"),
            File(datasetRoot, "ground_truth.json")
        ).firstOrNull { it.exists() }
        val gtQueriesByFile = mutableMapOf<String, JSONObject>()
        if (gtFile != null) {
            runCatching {
                val gtJson = JSONObject(gtFile.readText())
                val imgs = gtJson.optJSONArray("images") ?: JSONArray()
                for (i in 0 until imgs.length()) {
                    val item = imgs.getJSONObject(i)
                    val fName = item.optString("file_name")
                    val mclip = item.optJSONObject("ground_truth")?.optJSONObject("mobileclip_eval")
                    if (fName.isNotEmpty() && mclip != null) {
                        gtQueriesByFile[fName] = mclip
                    }
                }
            }
        }

        val imageFiles = datasetRoot.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            .sortedBy { it.absolutePath }
            .toList()

        require(imageFiles.isNotEmpty()) {
            "No se encontraron imágenes en ${datasetRoot.absolutePath}. Verifica que hiciste adb push."
        }

        val resultsArray = JSONArray()

        for (file in imageFiles) {
            val bitmap = decodeOrientedBitmap(file, maxDimension = 2048) ?: continue
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val relPath = "DataSet/" + file.relativeTo(datasetRoot).invariantSeparatorsPath

            val record = JSONObject().apply {
                put("file_name", file.name)
                put("relative_path", relPath)
                put("width", bitmap.width)
                put("height", bitmap.height)
            }
            val latencyJson = JSONObject()

            // 1. ML Kit Text Recognition (OCR Puro)
            var rawOcrText = ""
            val ocrBlocksJson = JSONArray()
            latencyJson.put("1_mlkit_ocr_ms", measureTimeMillis {
                val visionText = runCatching { Tasks.await(textRecognizer.process(inputImage)) }.getOrNull()
                if (visionText != null) {
                    rawOcrText = visionText.text
                    for (block in visionText.textBlocks) {
                        val b = block.boundingBox ?: continue
                        ocrBlocksJson.put(JSONObject().apply {
                            put("text", block.text)
                            put("box_2d", rectToNormArray(b, bitmap.width, bitmap.height))
                        })
                    }
                }
            })
            record.put("1_mlkit_ocr", JSONObject().apply {
                put("full_text", rawOcrText)
                put("block_count", ocrBlocksJson.length())
                put("blocks", ocrBlocksJson)
            })

            // 2 & 8. ML Kit Face Detection (Accurate vs Fast) + FaceNet (512D Con y Sin Alineación)
            val facesAccurateJson = JSONArray()
            latencyJson.put("2_mlkit_face_accurate_ms", measureTimeMillis {
                val faces = runCatching { Tasks.await(faceDetectorAccurate.process(inputImage)) }.getOrNull().orEmpty()
                for (face in faces) {
                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

                    var embAligned: FloatArray? = null
                    var embUnaligned: FloatArray? = null
                    val facenetMs = measureTimeMillis {
                        embAligned = runPureFaceNet(facenetInterpreter, bitmap, face.boundingBox, leftEye, rightEye, align = true)
                        embUnaligned = runPureFaceNet(facenetInterpreter, bitmap, face.boundingBox, leftEye, rightEye, align = false)
                    }

                    facesAccurateJson.put(JSONObject().apply {
                        put("box_2d", rectToNormArray(face.boundingBox, bitmap.width, bitmap.height))
                        put("has_eyes", leftEye != null && rightEye != null)
                        put("head_euler_y", face.headEulerAngleY)
                        put("head_euler_z", face.headEulerAngleZ)
                        put("facenet_ms", facenetMs)
                        if (embAligned != null) put("embedding_aligned_512d", floatArrayToJson(embAligned!!))
                        if (embUnaligned != null) put("embedding_unaligned_512d", floatArrayToJson(embUnaligned!!))
                    })
                }
            })

            val facesFastBoxesJson = JSONArray()
            latencyJson.put("2_mlkit_face_fast_ms", measureTimeMillis {
                val fastFaces = runCatching { Tasks.await(faceDetectorFast.process(inputImage)) }.getOrNull().orEmpty()
                for (ff in fastFaces) {
                    facesFastBoxesJson.put(rectToNormArray(ff.boundingBox, bitmap.width, bitmap.height))
                }
            })

            record.put("2_and_8_faces_and_facenet", JSONObject().apply {
                put("accurate_faces", facesAccurateJson)
                put("fast_faces_boxes", facesFastBoxesJson)
                put("fast_mode_face_count", facesFastBoxesJson.length())
            })

            // 3. ML Kit Barcode Scanning (Puro)
            val barcodesJson = JSONArray()
            latencyJson.put("3_mlkit_barcode_ms", measureTimeMillis {
                val barcodes = runCatching { Tasks.await(barcodeScanner.process(inputImage)) }.getOrNull().orEmpty()
                for (bc in barcodes) {
                    barcodesJson.put(JSONObject().apply {
                        put("format_code", bc.format)
                        put("raw_value", bc.rawValue ?: "")
                        bc.boundingBox?.let { put("box_2d", rectToNormArray(it, bitmap.width, bitmap.height)) }
                    })
                }
            })
            record.put("3_mlkit_barcodes", barcodesJson)

            // 4. ML Kit Image Labeling (Puro con umbral >= 0.10f)
            val labelsJson = JSONArray()
            latencyJson.put("4_mlkit_image_labeling_ms", measureTimeMillis {
                val labels = runCatching { Tasks.await(imageLabelerRaw.process(inputImage)) }.getOrNull().orEmpty()
                for (l in labels) {
                    labelsJson.put(JSONObject().apply {
                        put("index", l.index)
                        put("text_en", l.text)
                        put("confidence", l.confidence)
                    })
                }
            })
            record.put("4_mlkit_image_labels_raw", labelsJson)

            // 5 & 9. ML Kit Object Detection Puro + Color CIELAB + MobileNetV3 Large (1280D) vs MobileCLIP Crop (512D)
            val objectsJson = JSONArray()
            latencyJson.put("5_mlkit_object_detection_ms", measureTimeMillis {
                val objs = runCatching { Tasks.await(objectDetectorRaw.process(inputImage)) }.getOrNull().orEmpty()
                for (obj in objs) {
                    val nativeLabelsJson = JSONArray()
                    for (lbl in obj.labels) {
                        nativeLabelsJson.put(JSONObject().apply {
                            put("text_en", lbl.text)
                            put("confidence", lbl.confidence)
                            put("index", lbl.index)
                        })
                    }

                    val cropBitmap = cropBoxSafe(bitmap, obj.boundingBox, padRatio = 0.0f)
                    val cropLabelsJson = JSONArray()
                    val cropLabels = runCatching {
                        Tasks.await(imageLabelerRaw.process(InputImage.fromBitmap(cropBitmap, 0)))
                    }.getOrNull().orEmpty()
                    for (cl in cropLabels.take(5)) {
                        cropLabelsJson.put(JSONObject().apply {
                            put("text_en", cl.text)
                            put("confidence", cl.confidence)
                        })
                    }

                    val petEmb1280 = runPureMobileNetV3(mobilenetPetInterpreter, bitmap, obj.boundingBox, padRatio = 0.10f)
                    val clipCropEmb512 = runPureMobileClipImage(clipImageInterpreter, cropBitmap)
                    val colorCielab = extractPureCielabColor(bitmap, obj.boundingBox, marginRatio = 0.10f)

                    objectsJson.put(JSONObject().apply {
                        put("box_2d", rectToNormArray(obj.boundingBox, bitmap.width, bitmap.height))
                        put("native_base_labels", nativeLabelsJson)
                        put("crop_image_labels", cropLabelsJson)
                        put("dominant_color_cielab", colorCielab)
                        if (petEmb1280 != null) put("mobilenetv3_1280d", floatArrayToJson(petEmb1280))
                        if (clipCropEmb512 != null) put("mobileclip_crop_512d", floatArrayToJson(clipCropEmb512))
                    })
                }
            })
            record.put("5_and_9_objects_and_pets", objectsJson)

            // 6. ML Kit Entity Extraction (NER Puro, sin Regex Fallback)
            val entitiesJson = JSONArray()
            latencyJson.put("6_mlkit_entity_extraction_ms", measureTimeMillis {
                if (rawOcrText.isNotBlank()) {
                    val params = EntityExtractionParams.Builder(rawOcrText).build()
                    val annotations = runCatching { Tasks.await(entityExtractor.annotate(params)) }.getOrNull().orEmpty()
                    for (ann in annotations) {
                        for (ent in ann.entities) {
                            entitiesJson.put(JSONObject().apply {
                                put("raw_type_int", ent.type)
                                put("type_name", mapMlKitEntityType(ent.type))
                                put("text", ann.annotatedText)
                            })
                        }
                    }
                }
            })
            record.put("6_mlkit_entities_raw", entitiesJson)

            // 7. MobileCLIP-S0 Image (512D) + Text (512D)
            var clipImgVec: FloatArray? = null
            latencyJson.put("7_mobileclip_image_ms", measureTimeMillis {
                clipImgVec = runPureMobileClipImage(clipImageInterpreter, bitmap)
            })
            if (clipImgVec != null) {
                record.put("7_mobileclip_image_512d", floatArrayToJson(clipImgVec!!))

                val gtClip = gtQueriesByFile[file.name]
                if (gtClip != null && clipTextInterpreter != null && pureTokenizer != null) {
                    val posScoresEsRaw = JSONArray()
                    val posScoresTranslatedEn = JSONArray()
                    val negScoresTranslatedEn = JSONArray()

                    val qEs = gtClip.optJSONArray("open_search_queries_es") ?: JSONArray()
                    for (k in 0 until qEs.length()) {
                        val txtVec = runPureMobileClipText(clipTextInterpreter, pureTokenizer, qEs.getString(k))
                        if (txtVec != null) posScoresEsRaw.put(dotProduct(clipImgVec!!, txtVec).toDouble())
                    }

                    val qEn = gtClip.optJSONArray("expected_translated_queries_en") ?: JSONArray()
                    for (k in 0 until qEn.length()) {
                        val enQuery = qEn.getString(k)
                        val txtVec = runPureMobileClipText(clipTextInterpreter, pureTokenizer, "a photo of $enQuery")
                        if (txtVec != null) posScoresTranslatedEn.put(dotProduct(clipImgVec!!, txtVec).toDouble())
                    }

                    val negEn = gtClip.optJSONArray("hard_negative_queries_en") ?: JSONArray()
                    for (k in 0 until negEn.length()) {
                        val negQuery = negEn.getString(k)
                        val txtVec = runPureMobileClipText(clipTextInterpreter, pureTokenizer, "a photo of $negQuery")
                        if (txtVec != null) negScoresTranslatedEn.put(dotProduct(clipImgVec!!, txtVec).toDouble())
                    }

                    record.put("7_mobileclip_text_eval", JSONObject().apply {
                        put("pos_scores_es_raw", posScoresEsRaw)
                        put("pos_scores_translated_en", posScoresTranslatedEn)
                        put("neg_scores_translated_en", negScoresTranslatedEn)
                    })
                }
            }

            record.put("latencies_ms", latencyJson)
            resultsArray.put(record)
            bitmap.recycle()
        }

        val finalJson = JSONObject().apply {
            put("benchmark_type", "CLEAN_ISOLATED_RAW_MODELS")
            put("total_images", resultsArray.length())
            put("results", resultsArray)
        }

        val appDir = context.getExternalFilesDir(null) ?: context.filesDir
        val localFile = File(appDir, "clean_raw_predictions.json")
        localFile.writeText(finalJson.toString())

        // Copiar a /sdcard/Download/ con privilegios de shell (UiAutomation) para que persista tras la desinstalación automática de Gradle
        val destPath = "/sdcard/Download/clean_raw_predictions.json"
        instrumentation.uiAutomation.executeShellCommand("cp ${localFile.absolutePath} $destPath").use { pfd ->
            FileInputStream(pfd.fileDescriptor).readBytes()
        }
        println("Benchmark limpio exportado en: $destPath")
    }

    private fun loadTfliteFromAssets(context: Context, fileName: String): Interpreter {
        val afd = context.assets.openFd(fileName)
        val channel = FileInputStream(afd.fileDescriptor).channel
        val buffer = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        return Interpreter(buffer, Interpreter.Options().apply { setNumThreads(4) })
    }

    private fun runPureFaceNet(
        interpreter: Interpreter,
        source: Bitmap,
        box: Rect,
        leftEye: PointF?,
        rightEye: PointF?,
        align: Boolean,
        padRatio: Float = 0.15f
    ): FloatArray? = runCatching {
        val cropped = if (align && leftEye != null && rightEye != null) {
            val eyeL = if (leftEye.x < rightEye.x) leftEye else rightEye
            val eyeR = if (leftEye.x < rightEye.x) rightEye else leftEye
            val angle = Math.toDegrees(atan2((eyeR.y - eyeL.y).toDouble(), (eyeR.x - eyeL.x).toDouble())).toFloat()
            val matrix = Matrix().apply {
                setRotate(-angle, (eyeL.x + eyeR.x) / 2f, (eyeL.y + eyeR.y) / 2f)
            }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val rectF = RectF(box)
            matrix.mapRect(rectF)
            val rotBox = Rect(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt())
            cropBoxSafe(rotated, rotBox, padRatio)
        } else {
            cropBoxSafe(source, box, padRatio)
        }

        val resized = Bitmap.createScaledBitmap(cropped, 160, 160, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(160 * 160)
        resized.getPixels(pixels, 0, 160, 0, 0, 160, 160)
        for (px in pixels) {
            inputBuffer.putFloat(((px shr 16 and 0xFF) - 127.5f) / 128.0f)
            inputBuffer.putFloat(((px shr 8 and 0xFF) - 127.5f) / 128.0f)
            inputBuffer.putFloat(((px and 0xFF) - 127.5f) / 128.0f)
        }
        val output = Array(1) { FloatArray(512) }
        interpreter.run(inputBuffer, output)
        l2Normalize(output[0])
    }.getOrNull()

    private fun runPureMobileNetV3(
        interpreter: Interpreter,
        source: Bitmap,
        box: Rect,
        padRatio: Float = 0.10f
    ): FloatArray? = runCatching {
        val cropped = cropBoxSafe(source, box, padRatio)
        val resized = Bitmap.createScaledBitmap(cropped, 224, 224, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(224 * 224)
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        for (px in pixels) {
            inputBuffer.putFloat((px shr 16 and 0xFF) / 255.0f)
            inputBuffer.putFloat((px shr 8 and 0xFF) / 255.0f)
            inputBuffer.putFloat((px and 0xFF) / 255.0f)
        }
        val outDim = interpreter.getOutputTensor(0).shape().last()
        val output = Array(1) { FloatArray(outDim) }
        interpreter.run(inputBuffer, output)
        l2Normalize(output[0])
    }.getOrNull()

    private fun runPureMobileClipImage(interpreter: Interpreter, bitmap: Bitmap): FloatArray? = runCatching {
        val shape = interpreter.getInputTensor(0).shape()
        val isNchw = shape.size == 4 && shape[1] == 3
        val size = min(bitmap.width, bitmap.height)
        val sq = Bitmap.createBitmap(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)
        val resized = Bitmap.createScaledBitmap(sq, 224, 224, true)
        val pixels = IntArray(224 * 224)
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

        val meanR = 0.48145466f; val meanG = 0.45782750f; val meanB = 0.40821073f
        val stdR = 0.26862954f; val stdG = 0.26130258f; val stdB = 0.27577711f

        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * 224 * 224 * 4).order(ByteOrder.nativeOrder())
        if (isNchw) {
            for (px in pixels) inputBuffer.putFloat(((px shr 16 and 0xFF) / 255f - meanR) / stdR)
            for (px in pixels) inputBuffer.putFloat(((px shr 8 and 0xFF) / 255f - meanG) / stdG)
            for (px in pixels) inputBuffer.putFloat(((px and 0xFF) / 255f - meanB) / stdB)
        } else {
            for (px in pixels) {
                inputBuffer.putFloat(((px shr 16 and 0xFF) / 255f - meanR) / stdR)
                inputBuffer.putFloat(((px shr 8 and 0xFF) / 255f - meanG) / stdG)
                inputBuffer.putFloat(((px and 0xFF) / 255f - meanB) / stdB)
            }
        }
        val outputBuffer = ByteBuffer.allocateDirect(512 * 4).order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val vec = FloatArray(512) { outputBuffer.float }
        l2Normalize(vec)
    }.getOrNull()

    private fun runPureMobileClipText(
        interpreter: Interpreter,
        tokenizer: PureClipBpeTokenizer,
        text: String
    ): FloatArray? = runCatching {
        val tokens = tokenizer.tokenize(text)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 77 * 4).order(ByteOrder.nativeOrder())
        for (t in tokens) inputBuffer.putInt(t)
        val outputBuffer = ByteBuffer.allocateDirect(512 * 4).order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val vec = FloatArray(512) { outputBuffer.float }
        l2Normalize(vec)
    }.getOrNull()

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun cropBoxSafe(bitmap: Bitmap, box: Rect, padRatio: Float): Bitmap {
        val padX = (box.width() * padRatio).toInt()
        val padY = (box.height() * padRatio).toInt()
        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = min(bitmap.width, box.right + padX)
        val bottom = min(bitmap.height, box.bottom + padY)
        return if (right - left > 8 && bottom - top > 8) {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } else bitmap
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).coerceAtLeast(1e-12f)
        return FloatArray(v.size) { v[it] / norm }
    }

    private fun rectToNormArray(r: Rect, w: Int, h: Int): JSONArray {
        return JSONArray(listOf(
            ((r.top * 1000) / max(1, h)).coerceIn(0, 1000),
            ((r.left * 1000) / max(1, w)).coerceIn(0, 1000),
            ((r.bottom * 1000) / max(1, h)).coerceIn(0, 1000),
            ((r.right * 1000) / max(1, w)).coerceIn(0, 1000)
        ))
    }

    private fun floatArrayToJson(arr: FloatArray): JSONArray {
        val ja = JSONArray()
        for (v in arr) ja.put(v.toDouble())
        return ja
    }

    private fun mapMlKitEntityType(type: Int): String = when (type) {
        Entity.TYPE_URL -> "URL"
        Entity.TYPE_PHONE -> "Telefono"
        Entity.TYPE_EMAIL -> "Email"
        Entity.TYPE_IBAN -> "IBAN"
        Entity.TYPE_ADDRESS -> "Direccion"
        Entity.TYPE_PAYMENT_CARD -> "Tarjeta"
        Entity.TYPE_MONEY -> "Dinero"
        Entity.TYPE_TRACKING_NUMBER -> "Seguimiento"
        else -> "Otro_$type"
    }

    private fun extractPureCielabColor(bitmap: Bitmap, box: Rect, marginRatio: Float): String {
        val palette = listOf(
            "rojo" to Triple(220, 20, 30), "azul" to Triple(15, 60, 210),
            "celeste" to Triple(70, 180, 240), "verde" to Triple(30, 160, 45),
            "amarillo" to Triple(245, 220, 25), "negro" to Triple(18, 18, 18),
            "blanco" to Triple(245, 245, 245), "gris" to Triple(128, 128, 128),
            "marron" to Triple(115, 60, 25), "naranja" to Triple(240, 125, 20),
            "rosa" to Triple(245, 130, 170), "violeta" to Triple(130, 35, 170)
        )
        val mx = (box.width() * marginRatio).toInt()
        val my = (box.height() * marginRatio).toInt()
        val l = (box.left + mx).coerceIn(0, bitmap.width - 1)
        val t = (box.top + my).coerceIn(0, bitmap.height - 1)
        val r = (box.right - mx).coerceIn(l + 1, bitmap.width)
        val b = (box.bottom - my).coerceIn(t + 1, bitmap.height)
        val votes = mutableMapOf<String, Int>()
        for (y in t until b step 3) {
            for (x in l until r step 3) {
                val px = bitmap.getPixel(x, y)
                val lab = rgbToLab((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
                val best = palette.minByOrNull { (_, rgb) ->
                    val clab = rgbToLab(rgb.first, rgb.second, rgb.third)
                    val dl = lab[0] - clab[0]; val da = lab[1] - clab[1]; val db = lab[2] - clab[2]
                    sqrt(dl * dl + da * da + db * db)
                }?.first ?: "desconocido"
                votes[best] = (votes[best] ?: 0) + 1
            }
        }
        return votes.maxByOrNull { it.value }?.key ?: "desconocido"
    }

    private fun rgbToLab(rInt: Int, gInt: Int, bInt: Int): FloatArray {
        fun pivotRgb(c: Float) = if (c > 0.04045f) ((c + 0.055) / 1.055).pow(2.4).toFloat() else c / 12.92f
        fun pivotXyz(t: Float) = if (t > 0.008856f) cbrt(t.toDouble()).toFloat() else (7.787f * t) + (16f / 116f)
        val rl = pivotRgb(rInt / 255f); val gl = pivotRgb(gInt / 255f); val bl = pivotRgb(bInt / 255f)
        val x = (rl * 0.4124564f + gl * 0.3575761f + bl * 0.1804375f) / 0.95047f
        val y = (rl * 0.2126729f + gl * 0.7151522f + bl * 0.0721750f)
        val z = (rl * 0.0193339f + gl * 0.1191920f + bl * 0.9503041f) / 1.08883f
        val fx = pivotXyz(x); val fy = pivotXyz(y); val fz = pivotXyz(z)
        return floatArrayOf((116f * fy) - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    private fun decodeOrientedBitmap(file: File, maxDimension: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > maxDimension) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val matrix = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        if (!matrix.isIdentity) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true) else raw
    }.getOrNull()

    /**
     * Tokenizador BPE autónomo para evaluar mobileclip_s0_text.tflite sin acoplamiento.
     */
    private class PureClipBpeTokenizer(context: Context) {
        private val vocab = HashMap<String, Int>(50000)
        private val bpeRanks = HashMap<Pair<String, String>, Int>(50000)
        private val pattern = Regex("""<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""", RegexOption.IGNORE_CASE)

        init {
            runCatching {
                val jsonStr = context.assets.open("clip_vocab.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val obj = JSONObject(jsonStr)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    vocab[k] = obj.getInt(k)
                }
                BufferedReader(InputStreamReader(context.assets.open("clip_merges.txt"), Charsets.UTF_8)).use { reader ->
                    var rank = 0
                    var line = reader.readLine()
                    while (line != null) {
                        val t = line.trim()
                        if (t.isNotEmpty() && !t.startsWith("#")) {
                            val p = t.split(" ")
                            if (p.size == 2) bpeRanks[Pair(p[0], p[1])] = rank++
                        }
                        line = reader.readLine()
                    }
                }
            }
        }

        fun tokenize(text: String): IntArray {
            val tokens = mutableListOf(49406)
            for (m in pattern.findAll(text.lowercase().trim())) {
                for (bpeTok in bpe(m.value)) {
                    vocab[bpeTok]?.let { tokens.add(it) }
                }
            }
            tokens.add(49407)
            val res = IntArray(77) { 0 }
            val limit = min(tokens.size, 77)
            for (i in 0 until limit) res[i] = tokens[i]
            if (tokens.size > 77) res[76] = 49407
            return res
        }

        private fun bpe(token: String): List<String> {
            if (token.isEmpty()) return emptyList()
            val word = ArrayList<String>(token.length)
            for (i in 0 until token.length - 1) word.add(token[i].toString())
            word.add(token.last().toString() + "</w>")
            while (word.size > 1) {
                var minRank = Int.MAX_VALUE
                var bestPair: Pair<String, String>? = null
                for (i in 0 until word.size - 1) {
                    val pair = Pair(word[i], word[i + 1])
                    val r = bpeRanks[pair]
                    if (r != null && r < minRank) {
                        minRank = r
                        bestPair = pair
                    }
                }
                if (bestPair == null) break
                val nextWord = ArrayList<String>(word.size)
                var i = 0
                while (i < word.size) {
                    if (i < word.size - 1 && word[i] == bestPair.first && word[i + 1] == bestPair.second) {
                        nextWord.add(bestPair.first + bestPair.second)
                        i += 2
                    } else {
                        nextWord.add(word[i])
                        i++
                    }
                }
                word.clear()
                word.addAll(nextWord)
            }
            return word
        }
    }
}
