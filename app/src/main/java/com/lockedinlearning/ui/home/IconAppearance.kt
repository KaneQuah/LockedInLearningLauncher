package com.lockedinlearning.ui.home

import com.lockedinlearning.data.datastore.IconShape

/** Global icon rendering config — plain Kotlin, consumed by the View-based launcher3 layer. */
data class IconAppearance(
    val shape: IconShape = IconShape.SYSTEM_DEFAULT,
    val sizeScale: Float = 1f,
    val showLabels: Boolean = true
)

const val BASE_ICON_SIZE_DP = 52
