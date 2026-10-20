package com.example.ui.liquidglass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkElevatedSurface
import com.example.ui.theme.DarkInteractiveSurface
import com.example.ui.theme.DarkPrimaryAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.LightDivider
import com.example.ui.theme.LightPrimaryAccent
import com.example.ui.theme.LightSecondaryBackground
import com.example.ui.theme.LightSubtleSurface
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LocalDarkTheme
import org.intellij.lang.annotations.Language

/**
 * Adapted from the attached `com.kyant.backdrop` (AndroidLiquidGlass-kmp) source implementation:
 * - Preserves `Shaders.kt` / `HighlightModifier.kt` / `InnerShadowModifier.kt` / `Lens.kt` optical
 *   principles: directional specular rim highlight, subtle inner refraction diffusion, and
 *   AGSL RuntimeShader / RenderEffect surface treatment where supported by the Android API level.
 * - Adapted to the VidhyTop warm-academic Light Mode (#FAF8F6 / #FFFFFF / #ECEAE6 / #3244DB)
 *   and deep-charcoal Dark Mode (#0A0A0A / #161619 / #1E1E24 / #26262B / #2563EB) with zero
 *   purple-blue gradients or neon glows.
 */
@Language("AGSL")
private const val LIQUID_GLASS_SURFACE_SHADER = """
uniform float2 size;
uniform float4 tintColor;
uniform float4 highlightColor;
uniform float cornerRadius;

half4 main(float2 coord) {
    float2 uv = coord / max(size, float2(1.0, 1.0));
    // Directional light diffusion from top-left to bottom-right (matching com.kyant.backdrop HighlightStyle)
    float lightGrad = clamp(1.0 - (uv.x * 0.55 + uv.y * 0.75), 0.0, 1.0);
    // Subtle inner edge refraction falloff
    float2 centerDist = abs(uv - float2(0.5, 1.0)) * 2.0;
    float edgeFactor = smoothstep(0.65, 1.0, max(centerDist.x, centerDist.y));
    float specAlpha = (lightGrad * 0.12 + edgeFactor * 0.06) * highlightColor.a;
    return mix(tintColor, highlightColor, specAlpha);
}
"""

@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    cornerRadius: Dp = 20.dp,
    enabled: Boolean = true,
    elevated: Boolean = false
): Modifier {
    val isDark = LocalDarkTheme.current
    val baseSurfaceColor = if (isDark) {
        if (elevated) DarkElevatedSurface.copy(alpha = 0.92f) else DarkSurface.copy(alpha = 0.90f)
    } else {
        if (elevated) LightSurface.copy(alpha = 0.94f) else LightSecondaryBackground.copy(alpha = 0.90f)
    }

    val topSpecularColor = if (isDark) {
        DarkInteractiveSurface.copy(alpha = 0.95f)
    } else {
        LightSurface.copy(alpha = 0.90f)
    }

    val bottomRimColor = if (isDark) {
        DarkPrimaryAccent.copy(alpha = 0.16f)
    } else {
        LightDivider.copy(alpha = 0.85f)
    }

    if (!enabled) {
        return this
            .clip(shape)
            .background(
                if (isDark) {
                    if (elevated) DarkElevatedSurface else DarkSurface
                } else {
                    LightSurface
                },
                shape
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    }

    return this
        .clip(shape)
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val rimBrush = Brush.linearGradient(
                colors = listOf(topSpecularColor, bottomRimColor),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val innerSheenBrush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.16f)
                    )
                } else {
                    listOf(
                        LightSurface.copy(alpha = 0.65f),
                        LightSecondaryBackground.copy(alpha = 0.18f),
                        LightSubtleSurface.copy(alpha = 0.22f)
                    )
                },
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.8f, size.height)
            )

            onDrawBehind {
                // 1. Base translucent diffused glass material
                drawRoundRect(
                    color = baseSurfaceColor,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
                // 2. Inner refraction & light diffusion sheen
                drawRoundRect(
                    brush = innerSheenBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
                // 3. Optical specular border highlight (matching com.kyant.backdrop HighlightModifier)
                drawRoundRect(
                    brush = rimBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
}

@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    Box(
        modifier = modifier.liquidGlassSurface(
            shape = shape,
            cornerRadius = cornerRadius,
            enabled = enabled,
            elevated = true
        ),
        content = content
    )
}

/**
 * Applies a real Android 12+ (API 31+) hardware RenderEffect blur to a background layer when
 * requested (e.g. behind a modal overlay), with zero-cost no-op fallback on API 24-30.
 */
fun Modifier.optionalBackdropBlur(blurRadiusDp: Dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusDp > 0.dp) {
        this.graphicsLayer {
            val px = blurRadiusDp.toPx()
            renderEffect = RenderEffect.createBlurEffect(
                px,
                px,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        this
    }
}
