package es.zenvairo.nextcloudsync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val NcBlue = Color(0xFF0082C9)
val NcBlueDark = Color(0xFF005A94)

private val LightColors = lightColorScheme(
    primary = NcBlue,
    onPrimary = Color.White,
    secondary = NcBlueDark,
    tertiary = Color(0xFF6E5B00)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FC3F0),
    onPrimary = Color(0xFF00344F),
    secondary = Color(0xFF9ED4F2),
    tertiary = Color(0xFFDEC66C)
)

/**
 * Semantic colors that Material 3's default color scheme doesn't cover (success / warning / in
 * progress), used for sync status, health checks and mount states across the app.
 */
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val syncing: Color
)

private val LightExtendedColors = ExtendedColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color.White,
    successContainer = Color(0xFFDDF3DE),
    onSuccessContainer = Color(0xFF0B3D12),
    warning = Color(0xFF8A5A00),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFE8B8),
    onWarningContainer = Color(0xFF4A3200),
    syncing = NcBlue
)

private val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF8FD98C),
    onSuccess = Color(0xFF0B3D12),
    successContainer = Color(0xFF1E4A24),
    onSuccessContainer = Color(0xFFC8F5C8),
    warning = Color(0xFFF2C069),
    onWarning = Color(0xFF4A3200),
    warningContainer = Color(0xFF5C4300),
    onWarningContainer = Color(0xFFFFE8B8),
    syncing = Color(0xFF6FC3F0)
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Access point for the semantic colors above: `MaterialTheme.extendedColors.success`. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

/** Shared spacing scale (4/8/12/16/24/32dp) to keep paddings consistent across screens. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun NextCloudSyncTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
