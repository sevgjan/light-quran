package io.github.sevgjan.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RefRow(val ref: AyahRef, val title: String, val detail: String? = null, val arabic: Boolean = false)

/** A titled list of ayah references. Tapping one returns it. */
@Composable
private fun RefList(title: String, rows: List<RefRow>?, empty: String, onBack: () -> Unit, onPick: (AyahRef) -> Unit) {
    QuranPage {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(title),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        when {
            rows == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "Searching…", variant = LightTextVariant.Copy, lighten = true)
            }
            rows.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = empty,
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
                items(rows) { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { onPick(row.ref) }
                            .padding(vertical = 0.4f.gridUnitsAsDp()),
                    ) {
                        LightText(text = row.title, variant = LightTextVariant.Copy, maxLines = 1)
                        row.detail?.let {
                            if (row.arabic) {
                                ArabicText(text = it, sizeSp = 20, maxLines = 2)
                            } else {
                                LightText(
                                    text = it,
                                    variant = LightTextVariant.Fine,
                                    lighten = true,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSurahs(context: SealedLightContext): List<Surah>? {
    var surahs by remember { mutableStateOf<List<Surah>?>(null) }
    LaunchedEffect(Unit) { surahs = QuranRepository.surahs(context) }
    return surahs
}

class JuzScreen(sealedActivity: SealedLightActivity, private val context: SealedLightContext) :
    SimpleLightScreen<AyahRef>(sealedActivity) {
    @Composable
    override fun Content() {
        val surahs = rememberSurahs(context)
        val rows = surahs?.let { list ->
            Quran.JUZ_STARTS.mapIndexed { i, ref -> RefRow(ref, "Juz ${i + 1}", "${list[ref.surah - 1].name} $ref") }
        }
        RefList("Juz", rows, "", onBack = { goBack() }, onPick = { goBack(it) })
    }
}

class BookmarksScreen(
    sealedActivity: SealedLightActivity,
    private val context: SealedLightContext,
    private val store: QuranStore,
) : SimpleLightScreen<AyahRef>(sealedActivity) {
    @Composable
    override fun Content() {
        val surahs = rememberSurahs(context)
        val bookmarks by store.bookmarks.collectAsState(initial = emptyList())
        val english by QuranRepository.english.collectAsState()
        val rows = surahs?.let { list ->
            bookmarks.map { ref -> RefRow(ref, "${list[ref.surah - 1].name} $ref", english?.ayah(ref)) }
        }
        RefList(
            "Bookmarks", rows, "No bookmarks yet. Tap an ayah while reading to bookmark it.",
            onBack = { goBack() }, onPick = { goBack(it) },
        )
    }
}

class SearchScreen(
    sealedActivity: SealedLightActivity,
    private val context: SealedLightContext,
    private val query: String,
) : SimpleLightScreen<AyahRef>(sealedActivity) {
    @Composable
    override fun Content() {
        val english by QuranRepository.english.collectAsState()
        var rows by remember { mutableStateOf<List<RefRow>?>(null) }
        LaunchedEffect(query, english) {
            val surahs = QuranRepository.surahs(context)
            val arabic = QuranRepository.arabic(context)
            rows = withContext(Dispatchers.Default) {
                QuranSearch.search(query, arabic, english).map { hit ->
                    RefRow(hit.ref, "${surahs[hit.ref.surah - 1].name} ${hit.ref}", hit.text, arabic = Quran.isArabic(query))
                }
            }
        }
        val empty = "Nothing found for \"$query\"."
        RefList("\"$query\"", rows, empty, onBack = { goBack() }, onPick = { goBack(it) })
    }
}
