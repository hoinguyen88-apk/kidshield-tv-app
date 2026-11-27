package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class KidShieldSemanticColors(
    val kidsSafe: Color,
    val kidsSafeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val locked: Color,
    val lockedContainer: Color,
    val family: Color,
    val familyContainer: Color,
    val chartGrid: Color
)

val LocalKidShieldColors = staticCompositionLocalOf {
    KidShieldSemanticColors(
        kidsSafe = ElectricTeal,
        kidsSafeContainer = ElectricTealDark,
        warning = WarmAmber,
        warningContainer = WarmAmberContainer,
        locked = CrimsonLock,
        lockedContainer = CrimsonLockContainer,
        family = FamilyIndigo,
        familyContainer = FamilyIndigoContainer,
        chartGrid = MidnightOutline
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ElectricTeal,
    onPrimary = Color(0xFF00201A),
    primaryContainer = ElectricTealDark,
    onPrimaryContainer = Color(0xFF72F8DF),
    secondary = FamilyIndigo,
    onSecondary = Color(0xFF0E1338),
    secondaryContainer = FamilyIndigoContainer,
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = SkyCyan,
    onTertiary = Color(0xFF001F2E),
    background = MidnightBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = MidnightSurfaceHigh,
    outline = MidnightOutline,
    error = CrimsonLock,
    onError = Color.White,
    errorContainer = CrimsonLockContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = IndigoSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF0284C7),
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val KidShieldShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun KidShieldTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) {
        KidShieldSemanticColors(
            kidsSafe = ElectricTeal,
            kidsSafeContainer = ElectricTealDark,
            warning = WarmAmber,
            warningContainer = WarmAmberContainer,
            locked = CrimsonLock,
            lockedContainer = CrimsonLockContainer,
            family = FamilyIndigo,
            familyContainer = FamilyIndigoContainer,
            chartGrid = MidnightOutline
        )
    } else {
        KidShieldSemanticColors(
            kidsSafe = TealPrimaryLight,
            kidsSafeContainer = Color(0xFFCCFBF1),
            warning = Color(0xFFD97706),
            warningContainer = Color(0xFFFEF3C7),
            locked = Color(0xFFDC2626),
            lockedContainer = Color(0xFFFEE2E2),
            family = IndigoSecondaryLight,
            familyContainer = Color(0xFFE0E7FF),
            chartGrid = LightOutline
        )
    }

    CompositionLocalProvider(LocalKidShieldColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = KidShieldShapes,
            content = content
        )
    }
}
