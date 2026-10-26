package io.github.sevgjan.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: QuranStore) : LightViewModel<Unit>() {
    val prefs = store.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, ReadingPrefs())

    fun setDisplay(value: Display) = viewModelScope.launch { store.setDisplay(value) }
    fun setArabicSize(value: ArabicSize) = viewModelScope.launch { store.setArabicSize(value) }
    fun setReciter(value: Reciter) = viewModelScope.launch { store.setReciter(value) }
    fun toggleAutoAdvance() = viewModelScope.launch {
        val next = !prefs.value.autoAdvance
        store.setAutoAdvance(next)
        Recitation.autoAdvance.value = next
    }
}

class SettingsScreen(sealedActivity: SealedLightActivity, private val store: QuranStore) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(store)

    @Composable
    override fun Content() {
        val prefs by viewModel.prefs.collectAsState()

        QuranPage {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                center = LightTopBarCenter.Text("Settings"),
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
                Setting("Show", prefs.display.label) { viewModel.setDisplay(next(Display.entries, prefs.display)) }
                Setting("Arabic size", prefs.arabicSize.label) {
                    viewModel.setArabicSize(next(ArabicSize.entries, prefs.arabicSize))
                }
                Setting("Reciter", prefs.reciter.label) { viewModel.setReciter(next(Reciter.entries, prefs.reciter)) }
                Setting(
                    "When a surah ends",
                    if (prefs.autoAdvance) "Continue to the next surah" else "Stop",
                    viewModel::toggleAutoAdvance,
                )
                LightText(
                    text = "Privacy: no account, no tracking, no analytics. The text and translation are on the phone; " +
                        "it only goes online to stream recitation from cdn.islamic.network while you listen. " +
                        "Bookmarks and reading position stay on the phone.",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
                LightText(
                    text = "Quran text: Tanzil Project, Uthmani v1.1, CC BY 3.0, unmodified. Updates: tanzil.net. " +
                        "English: Marmaduke Pickthall, The Meaning of the Glorious Koran (1930), public domain, in Tanzil's digital edition. " +
                        "Recitation: the reciters named above, licensed to Islamic Network (alquran.cloud) for free " +
                        "non-commercial use, streamed and cached on the phone. Font: Amiri Quran, SIL Open Font License 1.1. " +
                        "Surah names: alquran.cloud. Code: MIT License.",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
            }
        }
    }

    @Composable
    private fun Setting(label: String, value: String, onClick: () -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().lightClickable(onClick = onClick).padding(vertical = 0.4f.gridUnitsAsDp())) {
            LightText(text = label, variant = LightTextVariant.Fine, lighten = true)
            LightText(text = value, variant = LightTextVariant.Copy)
        }
    }

    private fun <T> next(values: List<T>, current: T): T = values[(values.indexOf(current) + 1) % values.size]
}
