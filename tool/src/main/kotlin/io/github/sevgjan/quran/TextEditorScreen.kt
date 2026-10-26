package io.github.sevgjan.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/** Full-screen LP3 keyboard entry. Returns the text, or nothing when the user backs out. */
class TextEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initialValue: String = "",
    private val submitLabel: String = "DONE",
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptions = rememberKeyboardOptions()
        val state = rememberTextFieldState(initialValue)
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = state,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack() },
                submitLabel = submitLabel,
                singleLine = true,
                initialCaps = initialValue.isEmpty(),
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
