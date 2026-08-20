/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SeedColors {
    val Ember = Color(0xFFFF6D3A)
    val EmberDim = Color(0xFFB84D2A)
    val Ocean = Color(0xFF0077CC)
    val OceanDim = Color(0xFF005599)
    val Forest = Color(0xFF2D8C3C)
    val ForestDim = Color(0xFF1E5E28)
    val Midnight = Color(0xFF6A0DAD)
    val MidnightDim = Color(0xFF4B0082)
    val Rose = Color(0xFFE5484D)
    val RoseDim = Color(0xFFAA2E32)
    
    val Slate = Color(0xFF4A5568)
    val SlateDim = Color(0xFF2D3748)
    val Gold = Color(0xFFD4AF37)
    val GoldDim = Color(0xFF996515)
    val Mint = Color(0xFF3EB489)
    val MintDim = Color(0xFF2E8B57)
    val Cyberpunk = Color(0xFFFCEE09)
    val CyberpunkDim = Color(0xFFFF003C)

    val DeepPurple = Color(0xFF673AB7)
    val DeepPurpleDim = Color(0xFF4527A0)
    val ElectricBlue = Color(0xFF2196F3)
    val ElectricBlueDim = Color(0xFF1565C0)
    val Crimson = Color(0xFFDC143C)
    val CrimsonDim = Color(0xFF8B0000)
    val Emerald = Color(0xFF50C878)
    val EmeraldDim = Color(0xFF046307)
    val Solar = Color(0xFFFFCC33)
    val SolarDim = Color(0xFFE69500)

    val Night = Color(0xFF0E1013)
    val Surface = Color(0xFF1A1D22)
    val TextPrimary = Color(0xFFF2F3F5)
    val TextSecondary = Color(0xFF9AA1AB)
    val Success = Color(0xFF4CC38A)
    val Danger = Color(0xFFE5484D)
}

data class ThemeConfig(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color
)

@Composable
fun SeedTvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: String = "Ember",
    content: @Composable () -> Unit,
) {
    val config = when (themeColor) {
        "Ocean" -> ThemeConfig(SeedColors.Ocean, SeedColors.OceanDim, Color(0xFF0A192F), Color(0xFF112240))
        "Forest" -> ThemeConfig(SeedColors.Forest, SeedColors.ForestDim, Color(0xFF1B2E1D), Color(0xFF243B26))
        "Midnight" -> ThemeConfig(SeedColors.Midnight, SeedColors.MidnightDim, Color(0xFF120B1A), Color(0xFF1C142B))
        "Rose" -> ThemeConfig(SeedColors.Rose, SeedColors.RoseDim, Color(0xFF1F1112), Color(0xFF2B1617))
        "Slate" -> ThemeConfig(SeedColors.Slate, SeedColors.SlateDim, Color(0xFF171923), Color(0xFF1E2433))
        "Gold" -> ThemeConfig(SeedColors.Gold, SeedColors.GoldDim, Color(0xFF1A150A), Color(0xFF241E11))
        "Mint" -> ThemeConfig(SeedColors.Mint, SeedColors.MintDim, Color(0xFF0F1D19), Color(0xFF172924))
        "Cyberpunk" -> ThemeConfig(SeedColors.Cyberpunk, SeedColors.CyberpunkDim, Color(0xFF0D0221), Color(0xFF1A0B2E))
        "OLED" -> ThemeConfig(SeedColors.Ember, SeedColors.EmberDim, Color.Black, Color(0xFF111111))
        "Deep Purple" -> ThemeConfig(SeedColors.DeepPurple, SeedColors.DeepPurpleDim, Color(0xFF120B1A), Color(0xFF1C142B))
        "Electric Blue" -> ThemeConfig(SeedColors.ElectricBlue, SeedColors.ElectricBlueDim, Color(0xFF0A192F), Color(0xFF112240))
        "Crimson" -> ThemeConfig(SeedColors.Crimson, SeedColors.CrimsonDim, Color(0xFF1F1112), Color(0xFF2B1617))
        "Emerald" -> ThemeConfig(SeedColors.Emerald, SeedColors.EmeraldDim, Color(0xFF0F1D19), Color(0xFF172924))
        "Solar" -> ThemeConfig(SeedColors.Solar, SeedColors.SolarDim, Color(0xFF1A150A), Color(0xFF241E11))
        else -> ThemeConfig(SeedColors.Ember, SeedColors.EmberDim, SeedColors.Night, SeedColors.Surface)
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = config.primary,
            secondary = config.secondary,
            background = config.background,
            surface = config.surface,
            surfaceVariant = config.surface.copy(alpha = 0.7f),
            onPrimary = if (themeColor == "Cyberpunk" || themeColor == "Solar") Color.Black else Color.White,
            onBackground = SeedColors.TextPrimary,
            onSurface = SeedColors.TextPrimary,
            onSurfaceVariant = SeedColors.TextSecondary,
            error = SeedColors.Danger,
        )
    } else {
        lightColorScheme(
            primary = config.secondary,
            secondary = config.primary,
            background = Color.White,
            surface = Color(0xFFF7F8F9),
            onPrimary = Color.White,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
