package io.github.sevgjan.quran

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuranDataTest {
    private val surahs = Quran.parseSurahs(File("src/main/assets/surahs.json").readText())
    private val text = QuranText.parse(File("src/main/assets/quran-uthmani.txt").readText())

    @Test
    fun bundledTextIsComplete() {
        assertEquals(114, surahs.size)
        assertEquals(Quran.AYAH_COUNT, surahs.sumOf { it.ayahs })
        assertEquals(surahs.map { it.ayahs }, text.ayahs.map { it.size })
    }

    @Test
    fun bismillahIsAHeaderNotPartOfAyahOne() {
        val bismillah = text.ayah(AyahRef(1, 1))
        // Every surah with a header loses exactly the four Bismillah words from ayah 1.
        (2..114).filter(Quran::hasBismillahHeader).forEach { surah ->
            val ref = AyahRef(surah, 1)
            val raw = text.ayah(ref)
            val shown = Quran.displayText(ref, raw)
            assertEquals(raw.split(' ').size - 4, shown.split(' ').size, "surah $surah")
        }
        // Al-Fatiha 1:1 is the Bismillah itself and stays; At-Tawbah has none.
        assertEquals(bismillah, Quran.displayText(AyahRef(1, 1), bismillah))
        assertEquals(text.ayah(AyahRef(9, 1)), Quran.displayText(AyahRef(9, 1), text.ayah(AyahRef(9, 1))))
        assertFalse(Quran.hasBismillahHeader(9))
    }

    @Test
    fun globalNumbers() {
        assertEquals(1, Quran.globalNumber(surahs, AyahRef(1, 1)))
        assertEquals(8, Quran.globalNumber(surahs, AyahRef(2, 1)))
        assertEquals(262, Quran.globalNumber(surahs, AyahRef(2, 255)))
        assertEquals(Quran.AYAH_COUNT, Quran.globalNumber(surahs, AyahRef(114, 6)))
    }

    @Test
    fun juz() {
        assertEquals(1, Quran.juzOf(AyahRef(2, 141)))
        assertEquals(2, Quran.juzOf(AyahRef(2, 142)))
        assertEquals(30, Quran.juzOf(AyahRef(114, 6)))
        assertEquals(30, Quran.JUZ_STARTS.size)
    }

    @Test
    fun arabicSearchIgnoresDiacritics() {
        val hits = QuranSearch.search("الله لا اله الا هو الحي القيوم", text, english = null)
        assertTrue(hits.any { it.ref == AyahRef(2, 255) }, "Ayat al-Kursi not found: ${hits.map { it.ref }}")
    }

    @Test
    fun englishSearch() {
        val english = QuranText(listOf(listOf("In the name of Allah", "Praise be"), listOf("This is the Book")))
        assertEquals(listOf(AyahRef(2, 1)), QuranSearch.search("the book", text, english, limit = 10).map { it.ref })
    }

    @Test
    fun refParsing() {
        assertEquals(AyahRef(18, 10), AyahRef.parse("18:10"))
        assertEquals(null, AyahRef.parse("x"))
    }
}

class AyahNumberingTest {
    private val surahs = Quran.parseSurahs(File("src/main/assets/surahs.json").readText())

    @Test
    fun refOfIsInverseOfGlobalNumber() {
        listOf(AyahRef(1, 1), AyahRef(1, 7), AyahRef(2, 1), AyahRef(2, 286), AyahRef(18, 110), AyahRef(114, 6)).forEach { ref ->
            assertEquals(ref, Quran.refOf(surahs, Quran.globalNumber(surahs, ref)))
        }
        assertEquals(null, Quran.refOf(surahs, 0))
        assertEquals(null, Quran.refOf(surahs, Quran.AYAH_COUNT + 1))
    }
}
