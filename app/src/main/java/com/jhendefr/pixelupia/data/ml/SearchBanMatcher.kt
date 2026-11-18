package com.jhendefr.pixelupia.data.ml

import java.text.Normalizer
import java.util.Locale

/**
 * Evaluates whether a search query matches any banned search term or excluded tag
 * on a photo, using accent-insensitive normalization and token/plural matching
 * so partial typing prefixes do not falsely suppress unrelated searches.
 */
object SearchBanMatcher {

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonAlnumRegex = Regex("[^\\p{L}\\p{Nd}]+")
    private val multiSpaceRegex = Regex("\\s+")

    private val stopWords = setOf(
        "de", "la", "el", "en", "con", "un", "una", "los", "las", "del", "al", "por", "para", "y", "o"
    )

    fun normalize(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        val noAccents = diacriticsRegex.replace(decomposed, "")
        return noAccents
            .lowercase(Locale.ROOT)
            .replace(nonAlnumRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    fun tokenize(text: String, stripStopWords: Boolean = true): List<String> {
        val norm = normalize(text)
        if (norm.isEmpty()) return emptyList()
        val raw = norm.split(' ').filter { it.isNotEmpty() }
        if (!stripStopWords) return raw
        return raw.filter { it !in stopWords }.ifEmpty { raw }
    }

    private fun tokensEquivalent(queryToken: String, bannedToken: String): Boolean {
        if (queryToken == bannedToken) return true
        if (queryToken.length >= 3 && bannedToken.length >= 3) {
            if (queryToken == "${bannedToken}s" || bannedToken == "${queryToken}s") return true
            if (queryToken == "${bannedToken}es" || bannedToken == "${queryToken}es") return true
        }
        return false
    }

    fun isQueryExcluded(excludedTerms: List<String>, rawQuery: String): Boolean {
        if (excludedTerms.isEmpty()) return false
        val queryNorm = normalize(rawQuery)
        if (queryNorm.isEmpty()) return false
        val queryTokens = tokenize(rawQuery, stripStopWords = true)

        return excludedTerms.any { term ->
            val termNorm = normalize(term)
            if (termNorm.isEmpty()) return@any false
            if (queryNorm == termNorm) return@any true

            val termTokens = tokenize(term, stripStopWords = true)
            termTokens.isNotEmpty() && termTokens.all { bt ->
                queryTokens.any { qt -> tokensEquivalent(qt, bt) }
            }
        }
    }
}

