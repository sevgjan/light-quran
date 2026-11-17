package io.github.sevgjan.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

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

/** The tool's own ribbon icon (the SDK has none), tinted like LightOS icons. [size] is in grid units. */
@Composable
fun BookmarkIcon(filled: Boolean, size: Float, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(if (filled) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
        contentDescription = "Bookmarks",
        tint = LightThemeTokens.colors.content,
        modifier = modifier.size(size.gridUnitsAsDp()),
    )
}

/** Ribbon painter for LightBarButton.Icon, which draws custom painters untinted; tinted here to the theme. */
@Composable
fun bookmarkPainter(filled: Boolean): Painter {
    val painter = painterResource(if (filled) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
    val tint = ColorFilter.tint(LightThemeTokens.colors.content)
    return remember(painter, tint) {
        object : Painter() {
            override val intrinsicSize: Size get() = painter.intrinsicSize
            override fun DrawScope.onDraw() = with(painter) { draw(size, colorFilter = tint) }
        }
    }
}
