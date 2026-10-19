package com.tarang.launcher.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

// AGSL refraction: near the rounded edges, bend the sampled (already-blurred) backdrop toward the
// element centre, magnifying the rim like the thick bevel of a real glass slab. Subtle and confined
// to the edge by the squared falloff, so the middle stays a clean frost.
private const val REFRACTION_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float strength;
    half4 main(float2 coord) {
        float2 uv = coord / size;
        float2 toEdge = min(uv, 1.0 - uv);
        float edge = min(toEdge.x, toEdge.y);
        float f = 1.0 - smoothstep(0.0, 0.18, edge);
        float2 dir = uv - 0.5;
        float len = length(dir) + 0.0001;
        float2 ndir = dir / len;
        float2 displaced = coord - ndir * (f * f) * strength;
        return content.eval(displaced);
    }
"""

/** Only Android 13+ can load this class and its shader references. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object GlassRefractionApi33 {
    @DoNotInline
    fun create(radius: Float, size: IntSize): RenderEffect {
        val shader = RuntimeShader(REFRACTION_AGSL)
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("strength", min(min(size.width, size.height) * 0.08f, 18f))
        val blur = android.graphics.RenderEffect.createBlurEffect(
            radius, radius, android.graphics.Shader.TileMode.CLAMP,
        )
        val refract = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        return android.graphics.RenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
    }
}
