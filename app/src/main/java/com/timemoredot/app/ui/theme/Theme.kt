package com.timemoredot.app.ui.theme

import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** ui-design.md 6.1. dynamicColor는 쓰지 않는다. */
private fun c(hex: Long) = Color(0xFF000000 or hex)

private val Light: ColorScheme = lightColorScheme(
    primary = c(0x6F4A2E), onPrimary = c(0xFFFFFF), primaryContainer = c(0xFFDCC4), onPrimaryContainer = c(0x2B1605),
    inversePrimary = c(0xF0BC94),
    secondary = c(0x6E5B4E), onSecondary = c(0xFFFFFF), secondaryContainer = c(0xF8DECF), onSecondaryContainer = c(0x261911),
    tertiary = c(0x2F6A5A), onTertiary = c(0xFFFFFF), tertiaryContainer = c(0xB4EFDC), onTertiaryContainer = c(0x002019),
    error = c(0xBA1A1A), onError = c(0xFFFFFF), errorContainer = c(0xFFDAD6), onErrorContainer = c(0x410002),
    background = c(0xFFF8F5), onBackground = c(0x211A16), surface = c(0xFFF8F5), onSurface = c(0x211A16),
    surfaceDim = c(0xE4D8D2), surfaceBright = c(0xFFF8F5),
    surfaceContainerLowest = c(0xFFFFFF), surfaceContainerLow = c(0xFCF1EB), surfaceContainer = c(0xF6EBE5),
    surfaceContainerHigh = c(0xF0E5DF), surfaceContainerHighest = c(0xEAE0DA),
    surfaceVariant = c(0xF4DED3), onSurfaceVariant = c(0x52443B),
    outline = c(0x847469), outlineVariant = c(0xD7C3B7),
    inverseSurface = c(0x362F2B), inverseOnSurface = c(0xFCEEE8), surfaceTint = c(0x6F4A2E), scrim = c(0x000000),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = c(0xF0BC94), onPrimary = c(0x48290F), primaryContainer = c(0x613F23), onPrimaryContainer = c(0xFFDCC4),
    inversePrimary = c(0x6F4A2E),
    secondary = c(0xDBC2B3), onSecondary = c(0x3D2D23), secondaryContainer = c(0x554338), onSecondaryContainer = c(0xF8DECF),
    tertiary = c(0x98D3C0), onTertiary = c(0x00382D), tertiaryContainer = c(0x135143), onTertiaryContainer = c(0xB4EFDC),
    error = c(0xFFB4AB), onError = c(0x690005), errorContainer = c(0x93000A), onErrorContainer = c(0xFFDAD6),
    background = c(0x18120E), onBackground = c(0xEEE0D9), surface = c(0x18120E), onSurface = c(0xEEE0D9),
    surfaceDim = c(0x18120E), surfaceBright = c(0x403833),
    surfaceContainerLowest = c(0x120D0A), surfaceContainerLow = c(0x211A16), surfaceContainer = c(0x251E1A),
    surfaceContainerHigh = c(0x302824), surfaceContainerHighest = c(0x3B332E),
    surfaceVariant = c(0x52443B), onSurfaceVariant = c(0xD7C3B7),
    outline = c(0x9F8D82), outlineVariant = c(0x52443B),
    inverseSurface = c(0xEEE0D9), inverseOnSurface = c(0x362F2B), surfaceTint = c(0xF0BC94), scrim = c(0x000000),
)

/** 6.4 모양. */
internal val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/** 6.2 숫자 스타일(tnum). M8 이후 화면이 쓴다. */
object AppType {
    // 줄 높이는 글자 크기에 비례(104/96 em). 자동 축소 때 fontSize만 바꾼다(ui-design 4.2)
    val weightDisplay = TextStyle(
        fontSize = 96.sp, lineHeight = (104f / 96f).em, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum",
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
        ),
    )
    val metricValue = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val statValue = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum").phrase()   // 단계 카드 3줄이 감길 때 낱말 단위(4.5)
}

/** 6.3 간격(dp). */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 6.2: 한글을 낱말(구) 단위로 줄바꿈한다. WordBreak.Phrase는 API 33 이상에서만 듣고, 26~32는 시스템 기본(음절 단위)이다. */
private fun TextStyle.phrase() = copy(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase), localeList = LocaleList("ko-KR"))   // Phrase는 ko·ja 로케일 텍스트에만 듣는다

private val AppTypography: Typography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.phrase(), titleLarge = t.titleLarge.phrase(), titleMedium = t.titleMedium.phrase(), titleSmall = t.titleSmall.phrase(),
        bodyLarge = t.bodyLarge.phrase(), bodyMedium = t.bodyMedium.phrase(), bodySmall = t.bodySmall.phrase(),
        labelLarge = t.labelLarge.phrase(), labelMedium = t.labelMedium.phrase(), labelSmall = t.labelSmall.phrase(),
    )
}

@Composable
fun TimemoreTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = AppShapes, typography = AppTypography, content = content)
}
