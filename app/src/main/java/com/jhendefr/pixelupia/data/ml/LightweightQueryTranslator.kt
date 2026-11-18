package com.jhendefr.pixelupia.data.ml

import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LightweightQueryTranslator {

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    private val enToEsTranslator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
    )

    private val esToEnCache = LruCache<String, String>(256)
    private val enToEsCache = LruCache<String, String>(256)

    @Volatile
    private var isModelReady = false

    @Volatile
    private var isEnToEsModelReady = false

    @Volatile
    private var lastEsToEnFailureMs = 0L

    @Volatile
    private var lastEnToEsFailureMs = 0L

    private val retryCooldownMs = 60_000L
    private val downloadTimeoutMs = 2_500L

    suspend fun ensureModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady) return@withContext true
        val now = System.currentTimeMillis()
        if (lastEsToEnFailureMs > 0L && now - lastEsToEnFailureMs < retryCooldownMs) {
            return@withContext false
        }
        val conditions = DownloadConditions.Builder().build()
        val downloaded = withTimeoutOrNull(downloadTimeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(false) }
            }
        } ?: false
        isModelReady = downloaded
        if (!downloaded) lastEsToEnFailureMs = now
        downloaded
    }

    suspend fun ensureEnToEsModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isEnToEsModelReady) return@withContext true
        val now = System.currentTimeMillis()
        if (lastEnToEsFailureMs > 0L && now - lastEnToEsFailureMs < retryCooldownMs) {
            return@withContext false
        }
        val conditions = DownloadConditions.Builder().build()
        val downloaded = withTimeoutOrNull(downloadTimeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                enToEsTranslator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(false) }
            }
        } ?: false
        isEnToEsModelReady = downloaded
        if (!downloaded) lastEnToEsFailureMs = now
        downloaded
    }

    suspend fun translateEsToEn(spanishQuery: String): String? = withContext(Dispatchers.IO) {
        val cleanEs = spanishQuery.trim()
        if (cleanEs.isBlank()) return@withContext null

        val key = cleanEs.lowercase(Locale.ROOT)
        esToEnCache.get(key)?.let { return@withContext it }

        if (!ensureModelDownloaded()) return@withContext null

        val translatedEn = suspendCancellableCoroutine<String?> { cont ->
            translator.translate(cleanEs)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result?.trim()?.lowercase(Locale.ROOT))
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (!translatedEn.isNullOrBlank()) {
            esToEnCache.put(key, translatedEn)
        }
        translatedEn
    }

    suspend fun translateEnToEs(englishQuery: String): String? = withContext(Dispatchers.IO) {
        val cleanEn = englishQuery.trim()
        if (cleanEn.isBlank()) return@withContext null

        val key = cleanEn.lowercase(Locale.ROOT)
        enToEsCache.get(key)?.let { return@withContext it }

        if (!ensureEnToEsModelDownloaded()) return@withContext null

        val translatedEs = suspendCancellableCoroutine<String?> { cont ->
            enToEsTranslator.translate(cleanEn)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result?.trim()?.lowercase(Locale.ROOT))
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (!translatedEs.isNullOrBlank()) {
            enToEsCache.put(key, translatedEs)
        }
        translatedEs
    }

    suspend fun translateAndBuildClipPrompts(spanishQuery: String): List<String> = withContext(Dispatchers.IO) {
        val cleanEs = spanishQuery.trim()
        if (cleanEs.isBlank()) return@withContext emptyList()

        val translatedEn = translateEsToEn(cleanEs)
        if (translatedEn.isNullOrBlank()) {
            return@withContext QuerySemanticExpander.generateClipPrompts(cleanEs)
        }

        linkedSetOf(
            "a photo of $translatedEn",
            "a photo of a $translatedEn",
            translatedEn
        ).toList()
    }

    fun close() {
        translator.close()
        enToEsTranslator.close()
    }
}

