package io.github.sevgjan.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/** Amiri Quran (SIL Open Font License), drawn for Qur'anic text with full marks. */
val AmiriQuran = FontFamily(Font(R.font.amiri_quran))

@Composable
fun ArabicText(
    text: String,
    sizeSp: Int,
    modifier: Modifier = Modifier,
    color: Color? = null,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color ?: LightThemeTokens.colors.content,
        maxLines = maxLines,
        style = TextStyle(
            fontFamily = AmiriQuran,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 2).sp,
            textAlign = align,
            textDirection = TextDirection.Rtl,
        ),
    )
}

/** Themed full-screen column used by every screen. */
@Composable
fun QuranPage(content: @Composable ColumnScope.() -> Unit) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors) {
        Column(modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background), content = content)
    }
}
