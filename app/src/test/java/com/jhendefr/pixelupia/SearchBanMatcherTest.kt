package com.jhendefr.pixelupia

import com.jhendefr.pixelupia.data.ml.SearchBanMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBanMatcherTest {

    @Test
    fun exactAndAccentInsensitiveMatchAreExcluded() {
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("diversión"), "diversion"))
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("PERRO"), "perro"))
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("cabaña"), "cabana"))
    }

    @Test
    fun singularAndPluralVariantsMatch() {
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("perro"), "perros"))
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("perros"), "perro"))
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("flor"), "flores"))
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("flores"), "flor"))
    }

    @Test
    fun multiWordBanExcludesSupersetQueriesWithoutOverbanningSingleWords() {
        val banned = listOf("auto rojo")
        assertTrue(SearchBanMatcher.isQueryExcluded(banned, "auto rojo"))
        assertTrue(SearchBanMatcher.isQueryExcluded(banned, "auto rojo deportivo"))
        assertTrue(SearchBanMatcher.isQueryExcluded(banned, "autos rojos"))
        assertFalse(SearchBanMatcher.isQueryExcluded(banned, "rojo"))
        assertFalse(SearchBanMatcher.isQueryExcluded(banned, "bicicleta roja"))
        assertFalse(SearchBanMatcher.isQueryExcluded(banned, "auto"))
    }

    @Test
    fun typingPrefixesAndUnrelatedCompoundWordsAreNotBanned() {
        assertFalse(SearchBanMatcher.isQueryExcluded(listOf("casa"), "c"))
        assertFalse(SearchBanMatcher.isQueryExcluded(listOf("casa"), "ca"))
        assertFalse(SearchBanMatcher.isQueryExcluded(listOf("auto"), "autobus"))
    }

    @Test
    fun stopwordsInBannedPhraseDoNotBreakMatching() {
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("casa en el bosque"), "casa bosque"))
        assertTrue(SearchBanMatcher.isQueryExcluded(listOf("casa en el bosque"), "casa en el bosque"))
    }
}

