package io.github.sevgjan.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.audio.LightAudioErrorKind
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderState(
    val surah: Surah,
    val arabic: List<String>,
    val english: List<String>?,
    val prefs: ReadingPrefs,
    val bookmarks: Set<Int>,
    /** Tanzil's own spelling, from Al-Fatiha 1:1. */
    val bismillah: String,
)

class ReaderViewModel(
    private val context: SealedLightContext,
    private val store: QuranStore,
    private val player: LightAudioPlayer,
    private val surahNumber: Int,
) : LightViewModel<Unit>() {
    private val surahs = MutableStateFlow<List<Surah>>(emptyList())
    private val arabic = MutableStateFlow<QuranText?>(null)
    val selected = MutableStateFlow<Int?>(null)
    val isPlaying = player.isPlaying
    val playbackError = player.error
    val repeat = Recitation.repeat

    val state: StateFlow<ReaderState?> = combine(
        surahs, arabic, QuranRepository.english, store.prefs, store.bookmarks,
    ) { list, text, english, prefs, bookmarks ->
        val surah = list.getOrNull(surahNumber - 1) ?: return@combine null
        val ayahs = text?.ayahs?.get(surahNumber - 1) ?: return@combine null
        ReaderState(
            surah = surah,
            arabic = ayahs.mapIndexed { i, t -> Quran.displayText(AyahRef(surahNumber, i + 1), t) },
            english = english?.ayahs?.get(surahNumber - 1),
            prefs = prefs,
            bookmarks = bookmarks.filter { it.surah == surahNumber }.map { it.ayah }.toSet(),
            bismillah = text.ayah(AyahRef(1, 1)),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Ayah currently recited, when the player holds this surah with the chosen reciter. */
    val playingAyah: StateFlow<Int?> = combine(player.currentMediaItemIndex, player.queue, store.prefs, surahs) { _, _, prefs, list ->
        if (list.isEmpty()) null else Recitation.currentAyah(player, surahNumber, list, prefs.reciter)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            surahs.value = QuranRepository.surahs(context)
            arabic.value = QuranRepository.arabic(context)
            player.awaitReady()
        }
        viewModelScope.launch { store.prefs.collect { Recitation.autoAdvance.value = it.autoAdvance } }
    }

    fun select(ayah: Int) {
        selected.value = if (selected.value == ayah) null else ayah
    }

    fun playFrom(ayah: Int) {
        selected.value = null
        viewModelScope.launch {
            Recitation.playFrom(player, surahs.value, AyahRef(surahNumber, ayah), store.prefs.first().reciter)
        }
    }

    fun togglePlay() = if (player.isPlaying.value) player.pause() else player.play()
    fun next() = player.skipToNext()
    fun previous() = player.skipToPrevious()

    fun stop() {
        Recitation.repeat.value = null
        player.stop()
    }

    fun toggleRepeat(ayah: Int) {
        selected.value = null
        val ref = AyahRef(surahNumber, ayah)
        if (Recitation.repeat.value == ref) {
            Recitation.repeat.value = null
        } else {
            Recitation.repeat.value = ref
            if (playingAyah.value != ayah) playFrom(ayah)
        }
    }

    fun toggleBookmark(ayah: Int) {
        selected.value = null
        viewModelScope.launch { store.toggleBookmark(AyahRef(surahNumber, ayah)) }
    }

    fun saveLastRead(ayah: Int) {
        viewModelScope.launch { store.setLastRead(AyahRef(surahNumber, ayah)) }
    }
}

class ReaderScreen(
    private val sealedActivity: SealedLightActivity,
    private val store: QuranStore,
    private val start: AyahRef,
) : LightScreen<Unit, ReaderViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReaderViewModel>
        get() = ReaderViewModel::class.java

    override fun createViewModel() =
        ReaderViewModel(lightContext, store, Recitation.player(sealedActivity), start.surah)

    @OptIn(FlowPreview::class)
    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val selected by viewModel.selected.collectAsState()
        val playingAyah by viewModel.playingAyah.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val playbackError by viewModel.playbackError.collectAsState()
        val repeat by viewModel.repeat.collectAsState()
        // Item 0 is the surah header, item n is ayah n.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (start.ayah <= 1) 0 else start.ayah)

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .debounce(700)
                .collect { viewModel.saveLastRead(it.coerceAtLeast(1)) }
        }
        LaunchedEffect(playingAyah) {
            val ayah = playingAyah ?: return@LaunchedEffect
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (ayah !in visible.dropLast(1)) listState.animateScrollToItem(ayah)
        }

        QuranPage {
            val current = state
            LightTopBar(
                leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                center = LightTopBarCenter.Text(current?.let { "${it.surah.number} · ${it.surah.name}" } ?: ""),
            )
            if (current == null) {
                Column(Modifier.weight(1f)) {}
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    item { Header(current) }
                    items(current.arabic.size) { i ->
                        val ayah = i + 1
                        Ayah(
                            state = current,
                            ayah = ayah,
                            isPlaying = ayah == playingAyah,
                            isSelected = ayah == selected,
                            isRepeating = repeat == AyahRef(start.surah, ayah) && playingAyah != null,
                            onClick = { viewModel.select(ayah) },
                        )
                    }
                }
                playbackError?.takeIf { playingAyah != null }?.let { error ->
                    LightText(
                        text = when (error.kind) {
                            LightAudioErrorKind.Source -> "Couldn't load the recitation. Check your connection and press play."
                            else -> "Recitation stopped. Press play to try again."
                        },
                        variant = LightTextVariant.Fine,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
                    )
                }
                Controls(selected, current, playingAyah, isPlaying, repeat, listState.firstVisibleItemIndex)
            }
        }
    }

    @Composable
    private fun Header(state: ReaderState) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp())) {
            LightText(
                text = "${state.surah.meaning} · ${state.surah.ayahs} ayahs · ${if (state.surah.meccan) "Meccan" else "Medinan"}",
                variant = LightTextVariant.Fine,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (Quran.hasBismillahHeader(state.surah.number) && state.prefs.display != Display.ENGLISH) {
                ArabicText(
                    text = state.bismillah,
                    sizeSp = state.prefs.arabicSize.sp,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()),
                )
            }
        }
    }

    @Composable
    private fun Ayah(
        state: ReaderState,
        ayah: Int,
        isPlaying: Boolean,
        isSelected: Boolean,
        isRepeating: Boolean,
        onClick: () -> Unit,
    ) {
        val marker = buildString {
            if (isPlaying) append("▶ ")
            append("${state.surah.number}:$ayah")
            if (ayah in state.bookmarks) append("  ★")
            if (isRepeating) append("  repeating")
            if (isSelected) append("  · selected")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onClick)
                .padding(vertical = 0.6f.gridUnitsAsDp()),
        ) {
            LightText(text = marker, variant = LightTextVariant.Fine, lighten = !isPlaying && !isSelected)
            if (state.prefs.display != Display.ENGLISH) {
                ArabicText(
                    text = state.arabic[ayah - 1],
                    sizeSp = state.prefs.arabicSize.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 0.2f.gridUnitsAsDp()),
                )
            }
            if (state.prefs.display != Display.ARABIC) {
                state.english?.getOrNull(ayah - 1)?.let {
                    LightText(
                        text = it,
                        variant = LightTextVariant.Copy,
                        lighten = state.prefs.display == Display.BOTH,
                        modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }

    @Composable
    private fun Controls(
        selected: Int?,
        state: ReaderState,
        playingAyah: Int?,
        isPlaying: Boolean,
        repeat: AyahRef?,
        firstVisible: Int,
    ) {
        val items = when {
            selected != null -> listOf(
                LightBarButton.Text(text = "PLAY", onClick = { viewModel.playFrom(selected) }),
                LightBarButton.Text(
                    text = if (selected in state.bookmarks) "UNMARK" else "MARK",
                    onClick = { viewModel.toggleBookmark(selected) },
                ),
                LightBarButton.Text(
                    text = if (repeat == AyahRef(state.surah.number, selected)) "NO REPEAT" else "REPEAT",
                    onClick = { viewModel.toggleRepeat(selected) },
                ),
            )
            playingAyah != null -> listOf(
                LightBarButton.LightIcon(icon = LightIcons.REWIND, onClick = viewModel::previous),
                LightBarButton.LightIcon(icon = if (isPlaying) LightIcons.PAUSE else LightIcons.PLAY, onClick = viewModel::togglePlay),
                LightBarButton.LightIcon(icon = LightIcons.FAST_FORWARD, onClick = viewModel::next),
                LightBarButton.LightIcon(icon = LightIcons.STOP, onClick = viewModel::stop),
            )
            else -> listOf(
                LightBarButton.Text(text = "LISTEN", onClick = { viewModel.playFrom(firstVisible.coerceAtLeast(1)) }),
            )
        }
        LightBottomBar(items)
    }
}
