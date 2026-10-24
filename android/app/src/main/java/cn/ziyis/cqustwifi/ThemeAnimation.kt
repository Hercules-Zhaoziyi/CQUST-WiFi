package cn.ziyis.cqustwifi

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.*

data class AnimatedTheme(val controller: ThemeController, val darkFraction: Float)

// Interpolate the palette itself, so host screens, dialogs and the timetable stay in sync.
// No screenshot overlays: credentials and camera previews are never captured for animation.
@Composable
fun rememberAnimatedTheme(dark: Boolean, animations: Boolean, mode: ColorSchemeMode): AnimatedTheme {
    val light = remember { lightColorScheme() }
    val night = remember { darkColorScheme() }
    val fraction by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = if (animations) tween(520, easing = FastOutSlowInEasing) else snap(),
        label = "application-theme"
    )
    val colors = blendThemeColors(light, night, fraction)
    return AnimatedTheme(
        ThemeController(mode, lightColors = colors, darkColors = colors, isDark = dark),
        fraction
    )
}

internal fun blendThemeColors(light: Colors, dark: Colors, fraction: Float): Colors {
    val p = fraction.coerceIn(0f, 1f)
    return light.copy(
        primary = lerp(light.primary, dark.primary, p),
        onPrimary = lerp(light.onPrimary, dark.onPrimary, p),
        primaryVariant = lerp(light.primaryVariant, dark.primaryVariant, p),
        onPrimaryVariant = lerp(light.onPrimaryVariant, dark.onPrimaryVariant, p),
        error = lerp(light.error, dark.error, p),
        onError = lerp(light.onError, dark.onError, p),
        errorContainer = lerp(light.errorContainer, dark.errorContainer, p),
        onErrorContainer = lerp(light.onErrorContainer, dark.onErrorContainer, p),
        disabledPrimary = lerp(light.disabledPrimary, dark.disabledPrimary, p),
        disabledOnPrimary = lerp(light.disabledOnPrimary, dark.disabledOnPrimary, p),
        disabledPrimaryButton = lerp(light.disabledPrimaryButton, dark.disabledPrimaryButton, p),
        disabledOnPrimaryButton = lerp(light.disabledOnPrimaryButton, dark.disabledOnPrimaryButton, p),
        disabledPrimarySlider = lerp(light.disabledPrimarySlider, dark.disabledPrimarySlider, p),
        primaryContainer = lerp(light.primaryContainer, dark.primaryContainer, p),
        onPrimaryContainer = lerp(light.onPrimaryContainer, dark.onPrimaryContainer, p),
        secondary = lerp(light.secondary, dark.secondary, p),
        onSecondary = lerp(light.onSecondary, dark.onSecondary, p),
        secondaryVariant = lerp(light.secondaryVariant, dark.secondaryVariant, p),
        onSecondaryVariant = lerp(light.onSecondaryVariant, dark.onSecondaryVariant, p),
        disabledSecondary = lerp(light.disabledSecondary, dark.disabledSecondary, p),
        disabledOnSecondary = lerp(light.disabledOnSecondary, dark.disabledOnSecondary, p),
        disabledSecondaryVariant = lerp(light.disabledSecondaryVariant, dark.disabledSecondaryVariant, p),
        disabledOnSecondaryVariant = lerp(light.disabledOnSecondaryVariant, dark.disabledOnSecondaryVariant, p),
        secondaryContainer = lerp(light.secondaryContainer, dark.secondaryContainer, p),
        onSecondaryContainer = lerp(light.onSecondaryContainer, dark.onSecondaryContainer, p),
        secondaryContainerVariant = lerp(light.secondaryContainerVariant, dark.secondaryContainerVariant, p),
        onSecondaryContainerVariant = lerp(light.onSecondaryContainerVariant, dark.onSecondaryContainerVariant, p),
        tertiaryContainer = lerp(light.tertiaryContainer, dark.tertiaryContainer, p),
        onTertiaryContainer = lerp(light.onTertiaryContainer, dark.onTertiaryContainer, p),
        tertiaryContainerVariant = lerp(light.tertiaryContainerVariant, dark.tertiaryContainerVariant, p),
        background = lerp(light.background, dark.background, p),
        onBackground = lerp(light.onBackground, dark.onBackground, p),
        onBackgroundVariant = lerp(light.onBackgroundVariant, dark.onBackgroundVariant, p),
        surface = lerp(light.surface, dark.surface, p),
        onSurface = lerp(light.onSurface, dark.onSurface, p),
        surfaceVariant = lerp(light.surfaceVariant, dark.surfaceVariant, p),
        onSurfaceSecondary = lerp(light.onSurfaceSecondary, dark.onSurfaceSecondary, p),
        onSurfaceVariantSummary = lerp(light.onSurfaceVariantSummary, dark.onSurfaceVariantSummary, p),
        onSurfaceVariantActions = lerp(light.onSurfaceVariantActions, dark.onSurfaceVariantActions, p),
        disabledOnSurface = lerp(light.disabledOnSurface, dark.disabledOnSurface, p),
        surfaceContainer = lerp(light.surfaceContainer, dark.surfaceContainer, p),
        onSurfaceContainer = lerp(light.onSurfaceContainer, dark.onSurfaceContainer, p),
        onSurfaceContainerVariant = lerp(light.onSurfaceContainerVariant, dark.onSurfaceContainerVariant, p),
        surfaceContainerHigh = lerp(light.surfaceContainerHigh, dark.surfaceContainerHigh, p),
        onSurfaceContainerHigh = lerp(light.onSurfaceContainerHigh, dark.onSurfaceContainerHigh, p),
        surfaceContainerHighest = lerp(light.surfaceContainerHighest, dark.surfaceContainerHighest, p),
        onSurfaceContainerHighest = lerp(light.onSurfaceContainerHighest, dark.onSurfaceContainerHighest, p),
        outline = lerp(light.outline, dark.outline, p),
        dividerLine = lerp(light.dividerLine, dark.dividerLine, p),
        windowDimming = lerp(light.windowDimming, dark.windowDimming, p),
        sliderKeyPoint = lerp(light.sliderKeyPoint, dark.sliderKeyPoint, p),
        sliderKeyPointForeground = lerp(light.sliderKeyPointForeground, dark.sliderKeyPointForeground, p),
        sliderBackground = lerp(light.sliderBackground, dark.sliderBackground, p)
    )
}

