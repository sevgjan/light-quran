package io.github.sevgjan.quran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(context: SealedLightContext, store: QuranStore) : LightViewModel<Unit>() {
    val surahs = MutableStateFlow<List<Surah>>(emptyList())
    val lastRead = store.lastRead.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { surahs.value = QuranRepository.surahs(context) }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {
    private val store = QuranStore(lightContext.dataStore)

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(lightContext, store)

    @Composable
    override fun Content() {
        val surahs by viewModel.surahs.collectAsState()
        val lastRead by viewModel.lastRead.collectAsState()

        QuranPage {
            // LightTopBar takes one button per side; saved ayahs sit next to search, so this row is drawn here.
            Box(modifier = Modifier.fillMaxWidth().height(3f.gridUnitsAsDp()).padding(horizontal = 1f.gridUnitsAsDp())) {
                LightIcon(
                    icon = LightIcons.SETTINGS,
                    modifier = Modifier.align(Alignment.CenterStart).lightClickable {
                        navigateTo(screenFactory = { SettingsScreen(it, store) })
                    },
                )
                LightText(text = "Qur'an", variant = LightTextVariant.Fine, modifier = Modifier.align(Alignment.Center))
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(1.2f.gridUnitsAsDp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookmarkIcon(filled = false, size = 2f, modifier = Modifier.lightClickable(onClick = ::openBookmarks))
                    LightIcon(icon = LightIcons.SEARCH, modifier = Modifier.lightClickable(onClick = ::openSearch))
                }
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
                lastRead?.let { ref ->
                    surahs.getOrNull(ref.surah - 1)?.let { surah ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lightClickable { openReader(ref) }
                                    .padding(vertical = 0.5f.gridUnitsAsDp()),
                            ) {
                                LightText(text = "Continue", variant = LightTextVariant.Fine, lighten = true)
                                LightText(text = "${surah.name} $ref", variant = LightTextVariant.Copy)
                            }
                        }
                    }
                }
                items(surahs, key = { it.number }) { surah ->
                    SurahRow(surah) { openReader(AyahRef(surah.number, 1)) }
                }
            }
            LightBottomBar(
                listOfNotNull(
                    LightBarButton.Text(text = "JUZ", onClick = {
                        navigateTo(screenFactory = { JuzScreen(it, lightContext) }) { openReader(it) }
                    }),
                    lastRead?.let { ref -> LightBarButton.Text(text = "RESUME", onClick = { openReader(ref) }) },
                ),
            )
        }
    }

    private fun openReader(ref: AyahRef) {
        navigateTo(screenFactory = { ReaderScreen(it, store, ref) })
    }

    private fun openBookmarks() {
        navigateTo(screenFactory = { BookmarksScreen(it, lightContext, store) }) { openReader(it) }
    }

    private fun openSearch() {
        navigateTo(screenFactory = { TextEditorScreen(it, title = "Search (English or Arabic)", submitLabel = "SEARCH") }) { query ->
            navigateTo(screenFactory = { SearchScreen(it, lightContext, query) }) { openReader(it) }
        }
    }
}

@Composable
private fun SurahRow(surah: Surah, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().lightClickable(onClick = onClick).padding(vertical = 0.3f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = surah.number.toString(),
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.width(2.2f.gridUnitsAsDp()),
        )
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = surah.name, variant = LightTextVariant.Copy, maxLines = 1)
            LightText(
                text = "${surah.meaning} · ${surah.ayahs}",
                variant = LightTextVariant.Fine,
                lighten = true,
                maxLines = 1,
            )
        }
        Box(contentAlignment = Alignment.CenterEnd) {
            ArabicText(text = surah.arabic.removePrefix("سُورَةُ "), sizeSp = 20, maxLines = 1)
        }
    }
}
