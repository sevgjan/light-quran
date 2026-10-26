package io.github.sevgjan.quran

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Display(val label: String) { BOTH("Arabic and English"), ARABIC("Arabic only"), ENGLISH("English only") }

enum class ArabicSize(val label: String, val sp: Int) { SMALL("Small", 22), MEDIUM("Medium", 27), LARGE("Large", 33) }

enum class Reciter(val label: String, val edition: String, val bitrate: Int) {
    ALAFASY("Mishary Alafasy", "ar.alafasy", 128),
    HUSARY("Mahmoud Al-Husary", "ar.husary", 128),
    MINSHAWI("Mohamed Al-Minshawi", "ar.minshawi", 128),
    ABDUL_BASIT("Abdul Basit (Murattal)", "ar.abdulbasitmurattal", 64),
    SUDAIS("Abdurrahman As-Sudais", "ar.abdurrahmaansudais", 64),
    ;

    /** Per-ayah recitation from the Islamic Network CDN (alquran.cloud). */
    fun url(globalAyah: Int) = "https://cdn.islamic.network/quran/audio/$bitrate/$edition/$globalAyah.mp3"
}

data class ReadingPrefs(
    val display: Display = Display.BOTH,
    val arabicSize: ArabicSize = ArabicSize.MEDIUM,
    val reciter: Reciter = Reciter.ALAFASY,
    val autoAdvance: Boolean = false,
)

class QuranStore(private val dataStore: DataStore<Preferences>) {
    val prefs: Flow<ReadingPrefs> = dataStore.data.map { p ->
        ReadingPrefs(
            display = p[DISPLAY].toEnum(Display.BOTH),
            arabicSize = p[ARABIC_SIZE].toEnum(ArabicSize.MEDIUM),
            reciter = p[RECITER].toEnum(Reciter.ALAFASY),
            autoAdvance = p[AUTO_ADVANCE] ?: false,
        )
    }
    val lastRead: Flow<AyahRef?> = dataStore.data.map { p -> p[LAST_READ]?.let(AyahRef::parse) }
    val bookmarks: Flow<List<AyahRef>> = dataStore.data.map { p ->
        p[BOOKMARKS].orEmpty().mapNotNull(AyahRef::parse).sortedWith(compareBy({ it.surah }, { it.ayah }))
    }

    suspend fun setDisplay(value: Display) = dataStore.edit { it[DISPLAY] = value.name }
    suspend fun setArabicSize(value: ArabicSize) = dataStore.edit { it[ARABIC_SIZE] = value.name }
    suspend fun setReciter(value: Reciter) = dataStore.edit { it[RECITER] = value.name }
    suspend fun setAutoAdvance(value: Boolean) = dataStore.edit { it[AUTO_ADVANCE] = value }
    suspend fun setLastRead(ref: AyahRef) = dataStore.edit { it[LAST_READ] = ref.toString() }

    suspend fun toggleBookmark(ref: AyahRef) = dataStore.edit { p ->
        val current = p[BOOKMARKS].orEmpty()
        p[BOOKMARKS] = if (ref.toString() in current) current - ref.toString() else current + ref.toString()
    }

    private companion object {
        val DISPLAY = stringPreferencesKey("display")
        val ARABIC_SIZE = stringPreferencesKey("arabic_size")
        val RECITER = stringPreferencesKey("reciter")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val LAST_READ = stringPreferencesKey("last_read")
        val BOOKMARKS = stringSetPreferencesKey("bookmarks")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

/** Arabic text, English translation and surah list, all bundled. Loaded once per process. */
object QuranRepository {
    private val mutex = Mutex()
    private var surahList: List<Surah>? = null
    private var arabicText: QuranText? = null
    private val _english = MutableStateFlow<QuranText?>(null)
    val english: StateFlow<QuranText?> = _english

    suspend fun surahs(context: SealedLightContext): List<Surah> = load(context).first
    suspend fun arabic(context: SealedLightContext): QuranText = load(context).second

    private suspend fun load(context: SealedLightContext): Pair<List<Surah>, QuranText> = mutex.withLock {
        val surahs = surahList ?: withContext(Dispatchers.IO) {
            Quran.parseSurahs(context.readAsset("surahs.json").decodeToString())
        }.also { surahList = it }
        val arabic = arabicText ?: withContext(Dispatchers.Default) {
            QuranText.parse(context.readAsset("quran-uthmani.txt").decodeToString())
        }.also { arabicText = it }
        if (_english.value == null) {
            _english.value = withContext(Dispatchers.Default) {
                // Pickthall (1930), public domain; Tanzil's digital text.
                QuranText.parse(context.readAsset("en-pickthall.txt").decodeToString())
            }
        }
        surahs to arabic
    }
}
