package com.lockedinlearning.launcher3.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.lockedinlearning.R
import com.lockedinlearning.data.datastore.IconShape
import com.lockedinlearning.ui.home.IconAppearance

/**
 * Composite app-icon cell (icon + optional notification badge + optional 12sp marquee label),
 * used inside Workspace/Hotseat cells, folder previews, and the All Apps drawer list.
 * Touch target is enforced at [com.lockedinlearning.R.dimen.touch_target_min] (48dp) via padding
 * on the root, regardless of the visual icon size chosen in Settings.
 */
class IconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val imageView: ShapeableImageView
    private val badgeView: TextView
    private val labelView: TextView

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        val minTouch = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        minimumWidth = minTouch
        minimumHeight = minTouch
        setPadding(paddingLeft, 8.dp, paddingRight, 4.dp)
        LayoutInflater.from(context).inflate(R.layout.view_icon, this, true)
        imageView = findViewById(R.id.icon_image)
        badgeView = findViewById(R.id.icon_badge)
        labelView = findViewById(R.id.icon_label)
    }

    fun bind(
        label: String,
        icon: Drawable?,
        appearance: IconAppearance,
        badgeCount: Int = 0,
        showBadgeCount: Boolean = true
    ) {
        imageView.setImageDrawable(icon)
        imageView.shapeAppearanceModel = appearance.toShapeAppearanceModel(resources.getDimension(R.dimen.icon_size) / 2f)
        val scale = appearance.sizeScale
        val baseSize = resources.getDimensionPixelSize(R.dimen.icon_size)
        imageView.layoutParams = imageView.layoutParams.apply {
            width = (baseSize * scale).toInt()
            height = (baseSize * scale).toInt()
        }
        labelView.text = label
        labelView.isVisible = appearance.showLabels
        contentDescription = label

        badgeView.isVisible = badgeCount > 0
        if (badgeCount > 0) {
            badgeView.text = if (showBadgeCount) (if (badgeCount > 9) "9+" else badgeCount.toString()) else ""
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

/**
 * Stock-Android-style closed folder preview: a translucent rounded box with up to 4 of the
 * folder's contained app icons laid out in a 2x2 mosaic (IconView's own shapeAppearanceModel
 * clips this to the user's chosen icon shape, same as a single app icon would be).
 */
fun buildFolderPreviewDrawable(context: Context, icons: List<Drawable>, sizePx: Int): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), sizePx * 0.28f, sizePx * 0.28f, boxPaint)

    val mosaic = icons.take(4)
    val padding = (sizePx * 0.12f).toInt()
    val gap = (sizePx * 0.06f).toInt()
    val cell = (sizePx - 2 * padding - gap) / 2
    mosaic.forEachIndexed { index, icon ->
        val col = index % 2
        val row = index / 2
        val left = padding + col * (cell + gap)
        val top = padding + row * (cell + gap)
        icon.setBounds(left, top, left + cell, top + cell)
        icon.draw(canvas)
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** Converts the plain-Kotlin [IconAppearance] shape preference into an MDC [ShapeAppearanceModel]. */
fun IconAppearance.toShapeAppearanceModel(cornerRadiusPx: Float): ShapeAppearanceModel {
    val builder = ShapeAppearanceModel.builder()
    return when (shape) {
        IconShape.CIRCLE -> builder.setAllCorners(CornerFamily.ROUNDED, cornerRadiusPx).build()
        IconShape.SQUIRCLE -> builder.setAllCorners(CornerFamily.ROUNDED, cornerRadiusPx * 0.6f).build()
        IconShape.ROUNDED_SQUARE -> builder.setAllCorners(CornerFamily.ROUNDED, cornerRadiusPx * 0.3f).build()
        IconShape.SYSTEM_DEFAULT -> builder.setAllCorners(CornerFamily.ROUNDED, cornerRadiusPx * 0.3f).build()
    }
}
