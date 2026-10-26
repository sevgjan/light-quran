package io.github.sevgjan.quran

import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.audio.LightAudioItem
import com.thelightphone.sdk.audio.LightAudioPlayback
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.audio.LightAudioSource
import com.thelightphone.sdk.audio.LightMediaCache
import com.thelightphone.sdk.audio.LightMediaMetadata
import com.thelightphone.sdk.audio.LightPlayerSpec
import com.thelightphone.sdk.audio.LightQueueItem
import com.thelightphone.sdk.audio.NO_MEDIA_ITEM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One detached player for the whole tool (the SDK allows one detached handle per process), so
 * recitation keeps going with the screen off or after leaving the reader. The queue holds one
 * item per ayah; each item id names its reciter and global ayah number, so the queue can run
 * on into the next surah.
 */
object Recitation {
    private var player: LightAudioPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var surahs: List<Surah> = emptyList()

    /** Ayah to keep repeating, if any. */
    val repeat = MutableStateFlow<AyahRef?>(null)

    /** Continue into the next surah when one finishes (a reading preference, off by default). */
    val autoAdvance = MutableStateFlow(false)

    fun player(sealedActivity: SealedLightActivity): LightAudioPlayer = player ?: DefaultLightAudio(sealedActivity)
        .newPlayer(
            LightPlayerSpec(
                playback = LightAudioPlayback.Detached,
                cache = LightMediaCache("recitation", sizeBytes = 512L * 1024 * 1024),
            ),
        )
        .also { created ->
            player = created
            // Also re-check when playback (re)starts or the setting changes, not only on a new item.
            scope.launch {
                combine(created.currentMediaItemIndex, created.isPlaying, created.queue, autoAdvance) { index, playing, _, _ ->
                    index to playing
                }.collect { (index, playing) -> if (playing) onItemChanged(created, index) }
            }
        }

    private fun onItemChanged(player: LightAudioPlayer, index: Int) {
        if (index == NO_MEDIA_ITEM) return
        val queue = player.queue.value
        // Repeat: when playback moves past the repeated ayah, jump back to it.
        repeat.value?.let { target ->
            val targetIndex = queue.indexOfFirst { parse(it)?.second == target }
            if (targetIndex >= 0 && index > targetIndex) player.seekToDefaultPosition(targetIndex)
            return
        }
        // Next surah: once the last ayah starts, queue the following surah so playback runs on.
        if (autoAdvance.value && index == queue.lastIndex) {
            val (reciter, last) = parse(queue.last()) ?: return
            if (last.surah < 114 && last.ayah == surahs.getOrNull(last.surah - 1)?.ayahs) {
                items(last.surah + 1, reciter).forEach(player::addMediaItem)
            }
        }
    }

    private fun itemId(reciter: Reciter, globalAyah: Int) = "${reciter.edition}/$globalAyah"

    private fun parse(item: LightQueueItem): Pair<Reciter, AyahRef>? {
        val reciter = Reciter.entries.firstOrNull { it.edition == item.id.substringBefore('/') } ?: return null
        val ref = item.id.substringAfter('/').toIntOrNull()?.let { Quran.refOf(surahs, it) } ?: return null
        return reciter to ref
    }

    private fun items(surahNumber: Int, reciter: Reciter): List<LightAudioItem> {
        val surah = surahs[surahNumber - 1]
        val first = Quran.globalNumber(surahs, AyahRef(surahNumber, 1))
        return (1..surah.ayahs).map { ayah ->
            LightAudioItem(
                source = LightAudioSource.UrlSource(reciter.url(first + ayah - 1)),
                metadata = LightMediaMetadata(title = "${surah.name} $surahNumber:$ayah", artist = reciter.label),
                id = itemId(reciter, first + ayah - 1),
            )
        }
    }

    /** Ayah of [surah] being recited by [reciter] right now, if any. */
    fun currentAyah(player: LightAudioPlayer, surah: Int, surahs: List<Surah>, reciter: Reciter): Int? {
        this.surahs = surahs
        val item = player.queue.value.getOrNull(player.currentMediaItemIndex.value) ?: return null
        val (itemReciter, ref) = parse(item) ?: return null
        return if (itemReciter == reciter && ref.surah == surah) ref.ayah else null
    }

    fun playFrom(player: LightAudioPlayer, surahs: List<Surah>, ref: AyahRef, reciter: Reciter) {
        this.surahs = surahs
        if (repeat.value != null && repeat.value != ref) repeat.value = null
        val id = itemId(reciter, Quran.globalNumber(surahs, ref))
        val index = player.queue.value.indexOfFirst { it.id == id }
        if (index >= 0) {
            player.seekToDefaultPosition(index)
        } else {
            player.setMediaQueue(items(ref.surah, reciter), startIndex = ref.ayah - 1)
        }
        player.play()
    }
}
