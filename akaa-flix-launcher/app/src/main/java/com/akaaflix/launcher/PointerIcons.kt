package com.akaaflix.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.PointerIcon
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.core.ui.LauncherUi

/** Custom mouse pointers used inside the launcher (air mice on TV boxes). Android 7.0+. */
@RequiresApi(24)
object PointerIcons {
    fun create(context: Context, style: String): PointerIcon? {
        if (style == "system" || Build.VERSION.SDK_INT < 24) return null
        val d = context.resources.displayMetrics.density
        val size = ((if (style == "large") 40 else 28) * d).toInt()
        val bmp = Bitmap.createBitmap(size + 8, size + 8, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LauncherUi.palette.accent; maskFilter = BlurMaskFilter(4 * d, BlurMaskFilter.Blur.NORMAL) }
        c.drawCircle(size * 0.3f, size * 0.3f, size * 0.22f, glow)
        val icon = ResourcesCompat.getDrawable(context.resources, CoreR.drawable.ic_cursor, null)?.mutate() ?: return null
        icon.setTint(0xFFFFFFFF.toInt())
        icon.setBounds(0, 0, size, size)
        icon.draw(c)
        return PointerIcon.create(bmp, size * 5f / 24f, size * 3.6f / 24f)
    }
}
