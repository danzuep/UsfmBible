package io.github.danzuep.usfmbible.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageOrderTest {
    @Test
    fun sortsAlphabeticallyIgnoringCase() {
        val names = listOf("español", "Deutsch", "English", "français", "Bahasa Indonesia")
        assertEquals(
            listOf("Bahasa Indonesia", "Deutsch", "English", "español", "français"),
            names.sortedByLanguageName { it },
        )
    }

    @Test
    fun isStableForSameLettersInDifferentCase() {
        assertEquals(listOf("Ab", "ab"), listOf("ab", "Ab").sortedByLanguageName { it })
    }
}
