package com.lockedinlearning.launcher3.view

import android.content.Context
import android.util.AttributeSet

/**
 * The Home screen grid — a single, static 5x5 [CellLayout] page (no paging/multi-screen,
 * per this launcher's single-page requirement). Named separately from CellLayout, matching
 * AOSP's Workspace/CellLayout split, so a future multi-page Workspace wouldn't need callers
 * to change.
 */
class Workspace @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CellLayout(context, attrs)
