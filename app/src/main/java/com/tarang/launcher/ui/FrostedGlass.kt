package com.tarang.launcher.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.DevicePerformance

// We blur a HALF-resolution copy of the backdrop slice (1/4 the pixels through the blur kernel), then
// upscale the result — the detail is gone to the blur anyway, so it's invisible but much cheaper on a
// weak TV GPU.
private const val GLASS_DOWNSCALE = 0.5f

/**
 * Fades ONLY the blur layer of every frosted surface (the tint/sheen/rim stay). Read at draw time.
 * 1 = full frost, 0 = no blur (the raw wallpaper shows behind the tint). Used to "clear the glass"
 * over a moment just before an app launch. A stable lambda so providing it never recomposes.
 */
val LocalGlassBlurAlpha = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * tvOS "Liquid Glass": blurs the recorded [backdrop] wallpaper directly behind this element (a true
 * frosted-glass backdrop), then layers a legibility [tint], an optional content-aware [accent]
 * wash, a soft top-left sheen, and a beveled rim highlight on the edge. On Android 13+ an AGSL shader
 * can also refract the backdrop through the glass edges.
 *
 * Blur needs API 31+ and refraction needs API 33+. Low-memory and older devices use tint + sheen + rim
 * (and a plain blur where available). The element must be drawn above the same [backdrop] graphics
 * layer that recorded the wallpaper (see LauncherScreen) — it re-draws that layer shifted by its own
 * on-screen position so the blurred slice lines up.
 *
 * [live] gates the per-frame backdrop RE-CAPTURE; when false the last captured slice is reused (frozen
 * glass, still frosted — used mid launch/return zoom). [refract] gates the AGSL edge refraction: pass
 * false while anything is animating, since the bevel is imperceptible mid-motion but costs a shader pass.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: GraphicsLayer,
    shape: Shape,
    tint: Color,
    accent: Color? = null,
    blurRadius: Dp = 16.dp,
    live: Boolean = true,
    refract: Boolean = true,
    blur: Boolean = true,
    // Capture/blur resolution fraction. Smaller = fewer pixels through the blur kernel = cheaper, at
    // the cost of a coarser frost — invisible on the small top-bar chips, so they pass a lower value.
    downscale: Float = GLASS_DOWNSCALE,
): Modifier {
    val context = LocalContext.current
    val supportsEffects = remember(context) { DevicePerformance.supportsGlass(context) }
    // Return before allocating capture layers or effects on older and low-memory devices.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !blur || !supportsEffects) {
        return glassSurface(shape, tint, accent)
    }

    val layer = rememberGraphicsLayer()
    var offset by remember { mutableStateOf(Offset.Zero) }
    var glassSize by remember { mutableStateOf(IntSize.Zero) }
    val supportsShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // Blur runs on the downscaled copy, so scale the kernel to match (it's upscaled back afterwards).
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() } * downscale
    // Downscaled capture/blur size.
    val dsSize = IntSize(
        (glassSize.width * downscale).toInt().coerceAtLeast(1),
        (glassSize.height * downscale).toInt().coerceAtLeast(1),
    )

    val effect: RenderEffect = remember(dsSize, blurPx, supportsShader, refract) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && refract) {
            GlassRefractionApi33.create(blurPx, dsSize)
        } else {
            GlassBlurApi31.create(blurPx)
        }
    }
    val blurAlpha = LocalGlassBlurAlpha.current

    return this
        .onGloballyPositioned { offset = it.positionInRoot(); glassSize = it.size }
        .clip(shape)
        .drawBehind {
            if (glassSize.width > 0 && glassSize.height > 0) {
                layer.renderEffect = effect
                // Fade just the blur (the tint below stays), so the glass can "clear" before a launch.
                layer.alpha = blurAlpha()
                // [live] only gates the expensive re-capture of the backdrop slice. When frozen we
                // still draw the LAST captured slice, so the glass stays frosted mid-animation instead
                // of dropping to a near-transparent tint (which read as a flash on Frame Art enter/exit).
                if (live) {
                    // Capture the backdrop slice at the downscaled resolution (scale the draw down into dsSize).
                    layer.record(dsSize) {
                        scale(downscale, downscale, pivot = Offset.Zero) {
                            translate(-offset.x, -offset.y) { drawLayer(backdrop) }
                        }
                    }
                }
                // Upscale the blurred downscaled slice back to full size.
                scale(1f / downscale, 1f / downscale, pivot = Offset.Zero) {
                    drawLayer(layer)
                }
            }
        }
        .glassSurface(shape, tint, accent)
}

/** Translucent glass with no off-screen capture or platform effects. */
private fun Modifier.glassSurface(shape: Shape, tint: Color, accent: Color?): Modifier {
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.12f)))
    return clip(shape)
        .drawBehind {
            drawRect(tint)
            accent?.let { drawRect(it.copy(alpha = 0.10f)) }
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.7f, size.height),
                ),
            )
        }
        .border(1.dp, rim, shape)
}
