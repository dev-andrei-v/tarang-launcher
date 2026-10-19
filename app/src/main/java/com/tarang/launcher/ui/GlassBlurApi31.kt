package com.tarang.launcher.ui

import android.os.Build
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Keep API 31 platform references out of the Android 10 rendering path. */
@RequiresApi(Build.VERSION_CODES.S)
internal object GlassBlurApi31 {
    @DoNotInline
    fun create(radius: Float): RenderEffect = android.graphics.RenderEffect.createBlurEffect(
        radius, radius, android.graphics.Shader.TileMode.CLAMP,
    ).asComposeRenderEffect()
}
