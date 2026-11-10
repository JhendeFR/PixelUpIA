package com.jhendefr.pixelupia.data.ml

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LightweightQueryTranslator {

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    @Volatile
    private var isModelReady = false

    suspend fun ensureModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady) return@withContext true
        val conditions = DownloadConditions.Builder().build()
        val downloaded = suspendCancellableCoroutine<Boolean> { cont ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
        isModelReady = downloaded
        downloaded
    }

    suspend fun translateAndBuildClipPrompts(spanishQuery: String): List<String> = withContext(Dispatchers.IO) {
        val cleanEs = spanishQuery.trim()
        if (cleanEs.isBlank()) return@withContext emptyList()

        val ready = ensureModelDownloaded()
        if (!ready) {
            return@withContext QuerySemanticExpander.generateClipPrompts(cleanEs)
        }

        val translatedEn = suspendCancellableCoroutine<String?> { cont ->
            translator.translate(cleanEs)
                .addOnSuccessListener { result -> cont.resume(result?.trim()?.lowercase(Locale.ROOT)) }
                .addOnFailureListener { cont.resume(null) }
        }

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
    }
}

