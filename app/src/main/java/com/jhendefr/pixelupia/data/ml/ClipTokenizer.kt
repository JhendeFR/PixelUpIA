package com.jhendefr.pixelupia.data.ml

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Tokenizador BPE (Byte Pair Encoding) compatible con OpenAI CLIP y MobileCLIP.
 * Utiliza un vocabulario de 49408 tokens y reglas de merge BPE.
 * Context length: 77 enteros (SOT: 49406, EOT: 49407, Padding: 0).
 */
class ClipTokenizer(private val context: Context) {

    companion object {
        const val CONTEXT_LENGTH = 77
        const val START_TOKEN = 49406
        const val END_TOKEN = 49407
        const val PAD_TOKEN = 0

        // Expresion regular estandar de tokenizacion previa para CLIP
        private val PATTERN = Regex(
            """<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""",
            RegexOption.IGNORE_CASE
        )
    }

    private var isInitialized = false
    private val vocab = HashMap<String, Int>(50000)
    private val bpeRanks = HashMap<Pair<String, String>, Int>(50000)
    private val bpeCache = HashMap<String, List<String>>(1000)

    @Synchronized
    private fun ensureInitialized() {
        if (isInitialized) return

        // 1. Cargar vocabulario JSON desde assets
        try {
            val vocabInputStream = context.assets.open("clip_vocab.json")
            val vocabString = vocabInputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val jsonObject = JSONObject(vocabString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = jsonObject.getInt(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Cargar reglas de merge BPE desde assets
        try {
            val mergesInputStream = context.assets.open("clip_merges.txt")
            BufferedReader(InputStreamReader(mergesInputStream, Charsets.UTF_8)).use { reader ->
                var rank = 0
                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split(" ")
                        if (parts.size == 2) {
                            bpeRanks[Pair(parts[0], parts[1])] = rank
                            rank++
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isInitialized = true
    }

    /**
     * Codifica un texto arbitrario en un arreglo estricto de 77 enteros.
     */
    fun tokenize(text: String): IntArray {
        ensureInitialized()

        val cleanText = text.lowercase().trim()
        val tokens = mutableListOf<Int>()
        tokens.add(START_TOKEN)

        if (cleanText.isNotBlank()) {
            val matches = PATTERN.findAll(cleanText)
            for (match in matches) {
                val word = match.value
                val bpeTokens = bpe(word)
                for (bpeToken in bpeTokens) {
                    val tokenId = vocab[bpeToken]
                    if (tokenId != null) {
                        tokens.add(tokenId)
                    }
                }
            }
        }

        tokens.add(END_TOKEN)

        val result = IntArray(CONTEXT_LENGTH) { PAD_TOKEN }
        if (tokens.size > CONTEXT_LENGTH) {
            // Truncar preservando START y END
            for (i in 0 until CONTEXT_LENGTH - 1) {
                result[i] = tokens[i]
            }
            result[CONTEXT_LENGTH - 1] = END_TOKEN
        } else {
            for (i in tokens.indices) {
                result[i] = tokens[i]
            }
        }

        return result
    }

    private fun bpe(token: String): List<String> {
        val cached = bpeCache[token]
        if (cached != null) return cached

        if (token.isEmpty()) return emptyList()

        // Construir caracteres iniciales con </w> en el ultimo caracter
        val word = ArrayList<String>(token.length)
        for (i in 0 until token.length - 1) {
            word.add(token[i].toString())
        }
        word.add(token.last().toString() + "</w>")

        var pairs = getPairs(word)
        if (pairs.isEmpty()) {
            val single = listOf(token + "</w>")
            bpeCache[token] = single
            return single
        }

        while (true) {
            // Buscar el par con menor rank (mayor prioridad)
            var minRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null

            for (pair in pairs) {
                val rank = bpeRanks[pair]
                if (rank != null && rank < minRank) {
                    minRank = rank
                    bestPair = pair
                }
            }

            if (bestPair == null) break

            val first = bestPair.first
            val second = bestPair.second
            val newWord = ArrayList<String>(word.size)
            var i = 0

            while (i < word.size) {
                val j = word.indexOfFirstFrom(first, i)
                if (j == -1) {
                    for (k in i until word.size) {
                        newWord.add(word[k])
                    }
                    break
                }

                for (k in i until j) {
                    newWord.add(word[k])
                }
                i = j

                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }

            word.clear()
            word.addAll(newWord)
            if (word.size == 1) break
            pairs = getPairs(word)
        }

        bpeCache[token] = word
        return word
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = HashSet<Pair<String, String>>(word.size)
        for (i in 0 until word.size - 1) {
            pairs.add(Pair(word[i], word[i + 1]))
        }
        return pairs
    }

    private fun List<String>.indexOfFirstFrom(element: String, startIndex: Int): Int {
        for (i in startIndex until this.size) {
            if (this[i] == element) return i
        }
        return -1
    }
}
