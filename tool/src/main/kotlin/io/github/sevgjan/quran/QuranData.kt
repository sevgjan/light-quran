package io.github.sevgjan.quran

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Surah(
    val number: Int,
    val arabic: String,
    val name: String,
    val meaning: String,
    val ayahs: Int,
    val meccan: Boolean,
)

data class AyahRef(val surah: Int, val ayah: Int) {
    override fun toString() = "$surah:$ayah"

    companion object {
        fun parse(text: String): AyahRef? = text.split(':').takeIf { it.size == 2 }?.let { (s, a) ->
            val surah = s.toIntOrNull() ?: return null
            val ayah = a.toIntOrNull() ?: return null
            AyahRef(surah, ayah)
        }
    }
}

/** Ayah text per surah, parsed from Tanzil's `surah|ayah|text` format. Lines starting with # are ignored. */
class QuranText(val ayahs: List<List<String>>) {
    fun ayah(ref: AyahRef): String = ayahs[ref.surah - 1][ref.ayah - 1]

    companion object {
        fun parse(raw: String, surahCount: Int = 114): QuranText {
            val bySurah = List(surahCount) { mutableListOf<String>() }
            raw.lineSequence().forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val parts = line.split('|', limit = 3)
                if (parts.size == 3) parts[0].toIntOrNull()?.let { bySurah[it - 1] += parts[2] }
            }
            return QuranText(bySurah)
        }
    }
}

object Quran {
    const val AYAH_COUNT = 6236
    /** The Bismillah with marks removed, for matching however each surah spells it. */
    private const val BISMILLAH_PLAIN = "بسم الله الرحمن الرحيم"

    /** First ayah of each of the 30 juz. */
    val JUZ_STARTS = listOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
    ).map { (s, a) -> AyahRef(s, a) }

    fun parseSurahs(json: String): List<Surah> = Json.decodeFromString(json)

    /** Surahs other than Al-Fatiha and At-Tawbah open with the Bismillah, shown as a header. */
    fun hasBismillahHeader(surah: Int) = surah != 1 && surah != 9

    /** Tanzil includes the Bismillah (four words) at the start of ayah 1; strip it for display under the header. */
    fun displayText(ref: AyahRef, text: String): String {
        if (ref.ayah != 1 || !hasBismillahHeader(ref.surah)) return text
        val words = text.split(' ')
        return if (words.size > 4 && normalizeArabic(words.take(4).joinToString(" ")) == BISMILLAH_PLAIN) {
            words.drop(4).joinToString(" ")
        } else {
            text
        }
    }

    /** 1-based position of the ayah across the whole Qur'an (used by per-ayah audio). */
    fun globalNumber(surahs: List<Surah>, ref: AyahRef): Int = surahs.take(ref.surah - 1).sumOf { it.ayahs } + ref.ayah

    /** Inverse of [globalNumber]; null when out of range. */
    fun refOf(surahs: List<Surah>, global: Int): AyahRef? {
        var remaining = global
        surahs.forEach { surah ->
            if (remaining in 1..surah.ayahs) return AyahRef(surah.number, remaining)
            remaining -= surah.ayahs
        }
        return null
    }

    fun juzOf(ref: AyahRef): Int = JUZ_STARTS.indexOfLast { start ->
        start.surah < ref.surah || (start.surah == ref.surah && start.ayah <= ref.ayah)
    } + 1

    private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /** Arabic folded for matching: no tashkeel or Qur'anic marks, one alef, ya and ha forms unified. */
    fun normalizeArabic(text: String): String = text
        .replace(DIACRITICS, "")
        .replace(Regex("[ٱأإآ]"), "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")
        .trim()

    fun isArabic(text: String) = text.any { it in '؀'..'ۿ' }
}

data class SearchHit(val ref: AyahRef, val text: String)

object QuranSearch {
    fun search(query: String, arabic: QuranText, english: QuranText?, limit: Int = 100): List<SearchHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val hits = mutableListOf<SearchHit>()
        if (Quran.isArabic(q)) {
            val needle = Quran.normalizeArabic(q)
            arabic.ayahs.forEachIndexed { s, list ->
                list.forEachIndexed { a, text ->
                    if (hits.size < limit && Quran.normalizeArabic(text).contains(needle)) {
                        hits += SearchHit(AyahRef(s + 1, a + 1), text)
                    }
                }
            }
        } else if (english != null) {
            english.ayahs.forEachIndexed { s, list ->
                list.forEachIndexed { a, text ->
                    if (hits.size < limit && text.contains(q, ignoreCase = true)) hits += SearchHit(AyahRef(s + 1, a + 1), text)
                }
            }
        }
        return hits
    }
}
