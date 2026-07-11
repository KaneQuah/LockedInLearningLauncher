package com.lockedinlearning.ui.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lockedinlearning.R
import com.lockedinlearning.launcher3.LauncherSettings
import com.lockedinlearning.launcher3.allapps.AllAppsFragment
import com.lockedinlearning.launcher3.allapps.RevealGestureDetector
import com.lockedinlearning.launcher3.dragndrop.DragController
import com.lockedinlearning.launcher3.dragndrop.DragListener
import com.lockedinlearning.launcher3.model.FavoriteItemEntity
import com.lockedinlearning.launcher3.model.ItemInfo
import com.lockedinlearning.launcher3.view.DragLayer
import com.lockedinlearning.launcher3.view.Hotseat
import com.lockedinlearning.launcher3.view.IconView
import com.lockedinlearning.launcher3.view.buildFolderPreviewDrawable
import com.lockedinlearning.launcher3.view.PopupContainerWithArrow
import com.lockedinlearning.launcher3.view.Workspace
import com.lockedinlearning.ui.gate.GateFragment
import com.lockedinlearning.ui.gate.GateUiState
import com.lockedinlearning.ui.gate.GateViewModel
import com.lockedinlearning.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.hypot
import javax.inject.Inject

/**
 * HomeActivity is the launcher root — a traditional-View-system AppCompatActivity
 * (AOSP Launcher3-style), not a Compose host.
 *
 * State machine:
 *   - Gate not needed (Skip / NoQuestion) → show Workspace/Hotseat/Drawer
 *   - Gate active → GateFragment shown over everything else
 *   - "Do More" → voluntary practice question via GateViewModel.startPractice()
 */
@AndroidEntryPoint
class HomeActivity : AppCompatActivity(), DragListener {

    private val gateViewModel: GateViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    @Inject lateinit var widgetHost: WidgetHostManager

    private lateinit var dragLayer: DragLayer
    private lateinit var dragController: DragController
    private lateinit var workspace: Workspace
    private lateinit var hotseat: Hotseat
    private lateinit var topStatusRow: TopStatusRowView

    // ── Widget add flow state ───────────────────────────────────────────────
    private var pendingWidgetId: Int = -1
    private var pendingProvider: AppWidgetProviderInfo? = null
    // The floating "pick it up and drag it in" preview shown after a widget is picked — see
    // beginWidgetPlacement/onNewWidgetPlaced/onNewWidgetCancelled.
    private var pendingWidgetPreviewView: View? = null

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            configureOrAddWidget(pendingWidgetId, pendingProvider)
        } else {
            cancelPendingWidget()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-initialise gate check on every resume (handles cooldown + re-unlock)
        gateViewModel.reload()
        // Pick up newly installed / uninstalled apps
        homeViewModel.refreshApps()
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show system wallpaper behind the launcher, fully transparent bars (see themes.xml)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_home)

        dragLayer = findViewById(R.id.home_root)
        workspace = findViewById(R.id.workspace)
        hotseat = findViewById(R.id.hotseat)
        topStatusRow = findViewById(R.id.top_status_row)

        dragController = DragController(dragLayer, workspace, hotseat, this)
        dragLayer.dragController = dragController
        workspace.dragController = dragController
        hotseat.dragController = dragController

        topStatusRow.onProgressClick = { openProgress() }
        topStatusRow.onDoMoreClick = { gateViewModel.startPractice() }
        topStatusRow.onSettingsClick = { openSettings() }

        // Long-press on empty Workspace space (no icon under the finger) surfaces "Add widget" —
        // the only way to reach it, mirroring stock launchers' long-press-wallpaper menu.
        workspace.setOnLongClickListener {
            showHomeScreenMenu(workspace)
            true
        }

        applyChromeInsets()
        setupAllAppsDrawer()
        observeGate()
        observeHome()
    }

    /**
     * Bars stay transparent and the wallpaper draws full-bleed behind them (per
     * WindowCompat.setDecorFitsSystemWindows(false) above), but the actual interactive chrome —
     * top row and dock — must not render underneath the status/nav bar icons. Pad just those two
     * views by the system bar insets rather than reserving opaque space for the whole window.
     */
    private fun applyChromeInsets() {
        val topRowInitialTop = topStatusRow.paddingTop
        val hotseatInitialBottomMargin = (hotseat.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(dragLayer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topStatusRow.updatePadding(top = topRowInitialTop + bars.top)
            (hotseat.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = hotseatInitialBottomMargin + bars.bottom
            hotseat.requestLayout()
            insets
        }
    }

    private fun setupAllAppsDrawer() {
        val allAppsContainer = findViewById<FrameLayout>(R.id.all_apps_container)
        allAppsContainer.doOnLayout { it.translationY = it.height.toFloat() } // start fully closed
        val allAppsFragment = supportFragmentManager.findFragmentById(R.id.all_apps_container) as? AllAppsFragment
            ?: AllAppsFragment().also { supportFragmentManager.commit { replace(R.id.all_apps_container, it) } }
        val revealDetector = RevealGestureDetector(
            revealTarget = allAppsContainer,
            onOpenStateChanged = { /* no extra state to track */ },
            isContentScrolledToTop = { allAppsFragment.isScrolledToTop() }
        )
        dragLayer.revealGestureDetector = revealDetector

        onBackPressedDispatcher.addCallback(this) {
            if (allAppsContainer.translationY < allAppsContainer.height.toFloat() - 1f) {
                revealDetector.close()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    // ── DragListener (AOSP DragController callback surface) ──────────────────

    override fun onDesktopDrop(itemId: Long, cellX: Int, cellY: Int) = homeViewModel.moveDesktopItem(itemId, cellX, cellY)
    override fun onHotseatDrop(itemId: Long, slot: Int) = homeViewModel.moveHotseatItem(itemId, slot)
    override fun onFolderFusion(anchorItemId: Long, draggedItemId: Long) =
        homeViewModel.createFolder("Folder", anchorItemId, draggedItemId)
    override fun onAddToFolder(folderId: Long, draggedItemId: Long) =
        homeViewModel.addToFolder(folderId, draggedItemId)
    override fun onRemove(itemId: Long) = homeViewModel.removeFromHome(itemId)

    override fun onLongPressWithoutDrag(itemId: Long, anchorView: View) {
        val state = homeViewModel.state.value
        val allItems = state.desktopItems + state.hotseatItems
        when (val item = allItems.find { it.entity.id == itemId }) {
            is ItemInfo.AppItem -> showAppOptionsMenu(anchorView, item)
            is ItemInfo.FolderItem -> showFolderManageMenu(anchorView, item)
            is ItemInfo.WidgetItem -> showWidgetOptionsMenu(anchorView, item)
            null -> Unit
        }
    }

    override fun onNewWidgetPlaced(widgetId: Int, spanX: Int, spanY: Int, cellX: Int, cellY: Int) {
        removePendingWidgetPreview()
        homeViewModel.addWidget(widgetId, spanX, spanY, targetCell = cellX to cellY)
    }

    override fun onNewWidgetCancelled(widgetId: Int) {
        removePendingWidgetPreview()
        widgetHost.deleteWidgetId(widgetId)
    }

    private fun removePendingWidgetPreview() {
        pendingWidgetPreviewView?.let { dragLayer.removeView(it) }
        pendingWidgetPreviewView = null
    }

    override fun currentDesktopItems(): List<FavoriteItemEntity> =
        homeViewModel.state.value.desktopItems.map { it.entity }
    override fun currentHotseatItems(): List<FavoriteItemEntity> =
        homeViewModel.state.value.hotseatItems.map { it.entity }

    private fun observeGate() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                gateViewModel.state.collect { state ->
                    val showGate = state !is GateUiState.Skip && state !is GateUiState.NoQuestion
                    setGateVisible(showGate)
                }
            }
        }
    }

    private fun observeHome() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.state.collect { state ->
                    topStatusRow.bind(state.streak, state.todayCorrect)
                    renderWorkspace(state)
                    renderHotseat(state)
                }
            }
        }
    }

    // ── Grid rendering (static — no drag yet, that's Phase D) ───────────────

    private fun renderWorkspace(state: HomeUiState) {
        workspace.removeAllViews()
        for (item in state.desktopItems) {
            val view = viewForItem(item, state) ?: continue
            val entity = item.entity
            workspace.addViewToCell(view, entity.cellX, entity.cellY, entity.spanX, entity.spanY, tag = entity.id)
        }
    }

    private fun renderHotseat(state: HomeUiState) {
        hotseat.removeAllViews()
        for (item in state.hotseatItems) {
            val view = viewForItem(item, state) ?: continue
            hotseat.addViewToCell(view, item.entity.cellX, 0, 1, 1, tag = item.entity.id)
        }
    }

    private fun viewForItem(item: ItemInfo, state: HomeUiState): android.view.View? {
        return when (item) {
            is ItemInfo.AppItem -> {
                val pkg = item.entity.packageName ?: return null
                IconView(this).apply {
                    bind(
                        label = item.label,
                        icon = loadIcon(pkg),
                        appearance = state.iconAppearance
                    )
                    setOnClickListener { launchApp(pkg) }
                }
            }
            is ItemInfo.FolderItem -> IconView(this).apply {
                val previewIcons = item.contents.mapNotNull { it.entity.packageName?.let(::loadIcon) }
                val sizePx = (resources.getDimensionPixelSize(R.dimen.icon_size) * state.iconAppearance.sizeScale).toInt()
                bind(
                    label = item.label,
                    icon = if (previewIcons.isEmpty()) null else buildFolderPreviewDrawable(this@HomeActivity, previewIcons, sizePx),
                    appearance = state.iconAppearance
                )
                // Tap: browse contents (tap an app to launch it). Long-press: manage the folder
                // itself (Rename/Ungroup/Delete) — see onLongPressWithoutDrag.
                setOnClickListener { showFolderContentsMenu(item) }
            }
            is ItemInfo.WidgetItem -> {
                val widgetId = item.entity.appWidgetId ?: return null
                val density = resources.displayMetrics.density
                val cellWidthPx = workspace.cellWidthPx.takeIf { it > 0 }
                val cellHeightPx = workspace.cellHeightPx.takeIf { it > 0 }
                val widthDp = cellWidthPx?.let { (it * item.entity.spanX / density).toInt() }
                val heightDp = cellHeightPx?.let { (it * item.entity.spanY / density).toInt() }
                widgetHost.createView(this, widgetId, widthDp, heightDp) ?: TextView(this).apply {
                    text = "Widget unavailable"
                    setTextColor(android.graphics.Color.WHITE)
                }
            }
        }
    }

    /** Tapping a folder opens a centered lightbox showing its contents in a 4-row x 3-column
     *  grid (paginated 12-at-a-time if it holds more), matching stock Android's folder-open —
     *  not an anchored dropdown. Folder *management* (Rename/Ungroup/Delete) stays behind the
     *  long-press menu instead (see [showFolderManageMenu]). */
    private fun showFolderContentsMenu(folder: ItemInfo.FolderItem) {
        if (folder.contents.isEmpty()) return
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val cardWidth = dp(288)
        val columnWidth = (cardWidth - dp(32)) / 3

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            isClickable = true
        }

        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.folder_corner_radius)
            setCardBackgroundColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY)
            )
            cardElevation = dp(8).toFloat()
            isClickable = true // absorb taps so they don't fall through to the dismissing scrim
            layoutParams = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(TextView(this).apply {
            text = folder.label
            gravity = Gravity.CENTER
            setTextColor(MaterialColors.getColor(this, android.R.attr.textColorPrimary, Color.WHITE))
            setPadding(0, 0, 0, dp(12))
        })

        val state = homeViewModel.state.value
        val currentOrder = folder.contents.toMutableList()
        val pagesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun dismissFolder() {
            dragLayer.isFolderOpen = false
            dragLayer.removeView(scrim)
        }

        // Drag state, shared across every icon's touch listener for the lifetime of this dialog.
        var draggingIndex: Int? = null
        var dragShadow: View? = null
        val longPressHandler = Handler(Looper.getMainLooper())

        fun rebuildPages() {
            pagesRow.removeAllViews()
            currentOrder.chunked(FOLDER_PAGE_SIZE).forEach { pageApps ->
                val grid = GridLayout(this).apply {
                    columnCount = FOLDER_GRID_COLUMNS
                    rowCount = FOLDER_GRID_ROWS
                    layoutParams = LinearLayout.LayoutParams(cardWidth - dp(32), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                pageApps.forEach { app ->
                    val pkg = app.entity.packageName
                    val icon = IconView(this).apply {
                        bind(label = app.label, icon = pkg?.let { loadIcon(it) }, appearance = state.iconAppearance)
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = columnWidth
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        setOnClickListener {
                            pkg?.let { launchApp(it) }
                            dismissFolder()
                        }
                    }
                    var downRawX = 0f
                    var downRawY = 0f
                    var pendingLongPress: Runnable? = null
                    icon.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downRawX = event.rawX
                                downRawY = event.rawY
                                val runnable = Runnable {
                                    draggingIndex = currentOrder.indexOf(app)
                                    val bitmap = Bitmap.createBitmap(
                                        icon.width.coerceAtLeast(1), icon.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888
                                    )
                                    icon.draw(Canvas(bitmap))
                                    val iconLoc = IntArray(2).also { icon.getLocationOnScreen(it) }
                                    val cardLoc = IntArray(2).also { card.getLocationOnScreen(it) }
                                    dragShadow = ImageView(this).apply {
                                        setImageBitmap(bitmap)
                                        layoutParams = FrameLayout.LayoutParams(icon.width, icon.height)
                                        x = (iconLoc[0] - cardLoc[0]).toFloat()
                                        y = (iconLoc[1] - cardLoc[1]).toFloat()
                                        elevation = dp(8).toFloat()
                                    }
                                    card.addView(dragShadow)
                                    icon.alpha = 0f
                                }
                                pendingLongPress = runnable
                                longPressHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val shadow = dragShadow
                                if (shadow != null && draggingIndex != null) {
                                    val cardLoc = IntArray(2).also { card.getLocationOnScreen(it) }
                                    shadow.x = event.rawX - cardLoc[0] - shadow.width / 2f
                                    shadow.y = event.rawY - cardLoc[1] - shadow.height / 2f
                                    return@setOnTouchListener true
                                } else if (hypot((event.rawX - downRawX).toDouble(), (event.rawY - downRawY).toDouble()) > TOUCH_SLOP) {
                                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                                    pendingLongPress = null
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                                pendingLongPress = null
                                val fromIndex = draggingIndex
                                val shadow = dragShadow
                                if (fromIndex != null && shadow != null) {
                                    card.removeView(shadow)
                                    dragShadow = null
                                    draggingIndex = null
                                    val toIndex = findDropIndex(pagesRow, event.rawX, event.rawY, columnWidth)
                                    if (toIndex != null && toIndex != fromIndex) {
                                        val moved = currentOrder.removeAt(fromIndex)
                                        currentOrder.add(toIndex.coerceIn(0, currentOrder.size), moved)
                                        homeViewModel.reorderFolder(folder.entity.id, currentOrder.map { it.entity.id })
                                    }
                                    rebuildPages()
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        false
                    }
                    grid.addView(icon)
                }
                pagesRow.addView(grid)
            }
        }
        rebuildPages()

        val pageCount = (currentOrder.size + FOLDER_PAGE_SIZE - 1) / FOLDER_PAGE_SIZE
        content.addView(
            if (pageCount > 1) HorizontalScrollView(this).apply { addView(pagesRow) } else pagesRow
        )

        card.addView(content)
        scrim.addView(card)
        scrim.setOnClickListener { dismissFolder() }
        dragLayer.isFolderOpen = true
        dragLayer.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Maps a raw screen point to a target index in the (flattened, page-order) folder contents
     *  list — used to resolve where a dragged icon should land within [rebuildPages]'s grids. */
    private fun findDropIndex(pagesRow: LinearLayout, rawX: Float, rawY: Float, columnWidth: Int): Int? {
        for (pageIndex in 0 until pagesRow.childCount) {
            val grid = pagesRow.getChildAt(pageIndex) as? GridLayout ?: continue
            val loc = IntArray(2).also { grid.getLocationOnScreen(it) }
            val localX = rawX - loc[0]
            val localY = rawY - loc[1]
            if (localX < 0 || localY < 0 || localX > grid.width || localY > grid.height) continue
            val col = (localX / columnWidth.coerceAtLeast(1)).toInt().coerceIn(0, FOLDER_GRID_COLUMNS - 1)
            val rowHeight = (grid.height / FOLDER_GRID_ROWS).coerceAtLeast(1)
            val row = (localY / rowHeight).toInt().coerceIn(0, FOLDER_GRID_ROWS - 1)
            return pageIndex * FOLDER_PAGE_SIZE + row * FOLDER_GRID_COLUMNS + col
        }
        return null
    }

    private fun showAppOptionsMenu(anchor: View, app: ItemInfo.AppItem) {
        val pkg = app.entity.packageName
        PopupContainerWithArrow.show(
            anchor,
            listOf(
                "App info" to {
                    if (pkg != null) {
                        runCatching {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                            )
                        }
                    }
                    Unit
                },
                "Remove" to { homeViewModel.removeFromHome(app.entity.id) }
            )
        )
    }

    private fun showFolderManageMenu(anchor: View, folder: ItemInfo.FolderItem) {
        PopupContainerWithArrow.show(
            anchor,
            listOf(
                "Rename" to { promptRenameFolder(folder) },
                "Ungroup" to { homeViewModel.ungroupFolder(folder.entity.id) },
                "Delete" to { homeViewModel.deleteFolder(folder.entity.id) }
            )
        )
    }

    private fun promptRenameFolder(folder: ItemInfo.FolderItem) {
        val input = EditText(this).apply {
            setText(folder.label)
            setSelection(text.length)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val wrapper = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename folder")
            .setView(wrapper)
            .setPositiveButton("Rename") { _, _ ->
                homeViewModel.renameFolder(folder.entity.id, input.text?.toString().orEmpty())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWidgetOptionsMenu(anchor: View, widget: ItemInfo.WidgetItem) {
        val widgetId = widget.entity.appWidgetId ?: return
        PopupContainerWithArrow.show(
            anchor,
            listOf("Remove widget" to { homeViewModel.removeWidget(widget.entity.id, widgetId, widgetHost) })
        )
    }

    private fun loadIcon(packageName: String): Drawable? =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

    private fun launchApp(packageName: String) {
        runCatching {
            packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
        }
    }

    private fun setGateVisible(visible: Boolean) {
        val container = findViewById<FrameLayout>(R.id.gate_fragment_container)
        container.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        // While the Gate covers the screen, the drawer-reveal / drag machinery underneath it must
        // not steal touches meant for the Gate's own content (e.g. a flashcard's scroll view).
        dragLayer.isLauncherInteractive = !visible
        val existing = supportFragmentManager.findFragmentById(R.id.gate_fragment_container)
        if (visible && existing == null) {
            supportFragmentManager.commit { replace(R.id.gate_fragment_container, GateFragment()) }
        } else if (!visible && existing != null) {
            supportFragmentManager.commit { remove(existing) }
        }
    }

    fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    fun openProgress() = startActivity(
        Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_START_SCREEN, "progress")
    )

    // ── Widget add flow ─────────────────────────────────────────────────────

    private fun showHomeScreenMenu(anchor: View) {
        PopupContainerWithArrow.show(anchor, listOf("Add widget" to { showWidgetPicker() }))
    }

    private fun showWidgetPicker() {
        val providers = widgetHost.installedProviders()
        if (providers.isEmpty()) {
            Toast.makeText(this, "No widgets available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = MaterialAlertDialogBuilder(this)
        // Rows must be built from the builder's own themed context, not the Activity's — the
        // dialog wraps a Material alertDialogTheme overlay (correct surface/onSurface contrast
        // for the dialog card), which the Activity's theme knows nothing about.
        val themedContext = builder.context
        val density = themedContext.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val primaryTextColor = resolveThemeColor(themedContext, android.R.attr.textColorPrimary)

        val container = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val dialog = builder
            .setTitle("Add widget")
            .setView(ScrollView(themedContext).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .create()

        providers.forEach { info ->
            val row = LinearLayout(themedContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true
                isFocusable = true
                background = resolveThemeDrawable(themedContext, android.R.attr.selectableItemBackground)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    dialog.dismiss()
                    startAddWidget(info)
                }
            }
            row.addView(ImageView(themedContext).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(48))
                scaleType = ImageView.ScaleType.FIT_CENTER
                widgetHost.previewFor(info)?.let { setImageDrawable(it) }
            })
            row.addView(TextView(themedContext).apply {
                text = widgetHost.providerLabel(info)
                setTextColor(primaryTextColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(16)
                }
            })
            container.addView(row)
        }

        dialog.show()
    }

    // Color theme attrs (textColorPrimary etc.) are frequently ColorStateLists, not plain colors —
    // raw TypedValue.resolveAttribute + .data doesn't reliably resolve those to a usable ARGB int.
    // MaterialColors.getColor() is Material's own sanctioned helper for exactly this.
    private fun resolveThemeColor(context: android.content.Context, attr: Int): Int =
        MaterialColors.getColor(context, attr, Color.BLACK)

    private fun resolveThemeDrawable(context: android.content.Context, attr: Int): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return runCatching { context.getDrawable(typedValue.resourceId) }.getOrNull()
    }

    fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateWidgetId()
        pendingWidgetId = id
        pendingProvider = info

        if (widgetHost.bindWidgetIdIfAllowed(id, info)) {
            configureOrAddWidget(id, info)
        } else {
            // Ask the system for permission to bind this provider
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            runCatching { bindWidgetLauncher.launch(intent) }
                .onFailure { cancelPendingWidget() }
        }
    }

    private fun configureOrAddWidget(id: Int, info: AppWidgetProviderInfo?) {
        if (id == -1) return
        if (info?.configure != null) {
            // Provider has a configuration activity — must be launched via the host
            runCatching { widgetHost.startConfigure(this, id, REQUEST_CONFIGURE_WIDGET) }
                .onFailure {
                    // Some providers gate their config activity; add unconfigured as fallback
                    beginWidgetPlacement(id, info)
                    clearPending()
                }
        } else {
            beginWidgetPlacement(id, info)
            clearPending()
        }
    }

    /** Sizes a widget's cell span from its declared minWidth/minHeight, the same way real AOSP
     *  Launcher3 does — a fixed 2x2 guess forces larger widgets into too few cells, and since
     *  CellLayout measures children EXACTLY to their span, that squeezes/overflows the widget's
     *  real content past its allocated slot.
     *
     *  [AppWidgetProviderInfo.minWidth]/[AppWidgetProviderInfo.minHeight] (and the
     *  minResizeWidth/minResizeHeight variants) are declared in dp in the provider's XML, but the
     *  framework resolves them via `getDimensionPixelSize` at parse time — the fields you read
     *  here are already actual device pixels, not dp. Dividing by a dp-converted cell size (as
     *  an earlier version of this code did) double-converts and inflates the required span by
     *  the device's density factor, which is what made a widget stock Android places at 1 row
     *  land here at 2+ rows. Compare pixels to pixels directly.
     *
     *  minWidth/minHeight are the provider's *recommended default* footprint, not its smallest
     *  usable one — plenty of providers (media/music controls in particular) declare a generous
     *  default assuming the host will let the user resize down afterwards. Since this launcher
     *  has no post-placement resize UI yet, placing at the recommended default instead of the
     *  provider's actual resize floor (minResizeWidth/minResizeHeight, when smaller) is what made
     *  such widgets look oversized — falls back to minWidth/minHeight when a provider doesn't
     *  declare resize minimums (pre-JB-MR1 providers report 0 for both fields). */
    private fun computeWidgetSpan(info: AppWidgetProviderInfo?): Pair<Int, Int> {
        // Workspace hasn't necessarily been measured yet (e.g. a widget added before the first
        // layout pass finishes) — in that case cellWidthPx/workspace.width are both 0/unmeasured,
        // and dividing minWidth by a garbage 1px cell inflates spanX/spanY to the grid's full size,
        // which is exactly the "widget eats the whole home screen" bug. Bail to the same safe
        // default used when there's no provider info at all rather than trusting an unmeasured cell.
        // API 31+ providers can declare targetCellWidth/targetCellHeight directly — an explicit
        // "I want N cells" hint in cell units, not dp — and stock launchers place at exactly that
        // span rather than deriving one from minWidth/minHeight.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info != null &&
            info.targetCellWidth > 0 && info.targetCellHeight > 0
        ) {
            return info.targetCellWidth.coerceIn(1, LauncherSettings.GRID_SIZE) to
                info.targetCellHeight.coerceIn(1, LauncherSettings.GRID_SIZE)
        }

        val cellWidthPx = workspace.cellWidthPx.takeIf { it > 0 } ?: return DEFAULT_WIDGET_SPAN to DEFAULT_WIDGET_SPAN
        val cellHeightPx = workspace.cellHeightPx.takeIf { it > 0 } ?: cellWidthPx
        val targetWidthPx = info?.let { widget ->
            widget.minResizeWidth.takeIf { it in 1 until widget.minWidth } ?: widget.minWidth
        }
        val targetHeightPx = info?.let { widget ->
            widget.minResizeHeight.takeIf { it in 1 until widget.minHeight } ?: widget.minHeight
        }
        val spanX = targetWidthPx?.let { ceil(it.toDouble() / cellWidthPx).toInt() }
            ?.coerceIn(1, LauncherSettings.GRID_SIZE) ?: DEFAULT_WIDGET_SPAN
        val spanY = targetHeightPx?.let { ceil(it.toDouble() / cellHeightPx).toInt() }
            ?.coerceIn(1, LauncherSettings.GRID_SIZE) ?: DEFAULT_WIDGET_SPAN
        return spanX to spanY
    }

    /**
     * Once a widget id is allocated/bound/configured, it isn't placed yet — a floating preview
     * (the same image shown in the picker) appears centered over the Workspace, and the user
     * long-presses *that* to pick it up and drag it into a cell, reusing the exact same
     * ghost-slot/collision-preview machinery DragController already provides for placed items.
     * Only on a successful drop does a Favorites row actually get created (see
     * onNewWidgetPlaced/onNewWidgetCancelled).
     */
    private fun beginWidgetPlacement(id: Int, info: AppWidgetProviderInfo?) {
        removePendingWidgetPreview()
        val (spanX, spanY) = computeWidgetSpan(info)
        val cellWidthPx = workspace.cellWidthPx.takeIf { it > 0 } ?: resources.getDimensionPixelSize(R.dimen.icon_size)
        val cellHeightPx = workspace.cellHeightPx.takeIf { it > 0 } ?: cellWidthPx
        val previewWidth = cellWidthPx * spanX
        val previewHeight = cellHeightPx * spanY

        val preview = ImageView(this).apply {
            setImageDrawable(info?.let { widgetHost.previewFor(it) })
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.argb(60, 255, 255, 255))
            layoutParams = FrameLayout.LayoutParams(previewWidth, previewHeight)
        }
        dragLayer.addView(preview)
        pendingWidgetPreviewView = preview

        val wsLoc = IntArray(2).also { workspace.getLocationOnScreen(it) }
        val centerX = wsLoc[0] + workspace.width / 2f
        val centerY = wsLoc[1] + workspace.height / 2f
        preview.doOnLayout {
            it.x = centerX - previewWidth / 2f
            it.y = centerY - previewHeight / 2f
        }

        var lastRawX = centerX
        var lastRawY = centerY
        preview.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastRawX = event.rawX
                lastRawY = event.rawY
            }
            false
        }
        preview.setOnLongClickListener {
            dragController.startDragForNewWidget(preview, id, spanX, spanY, lastRawX, lastRawY)
            true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK && pendingWidgetId != -1) {
                beginWidgetPlacement(pendingWidgetId, pendingProvider)
                clearPending()
            } else {
                cancelPendingWidget()
            }
        }
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != -1) widgetHost.deleteWidgetId(pendingWidgetId)
        clearPending()
    }

    private fun clearPending() {
        pendingWidgetId = -1
        pendingProvider = null
    }

    companion object {
        private const val REQUEST_CONFIGURE_WIDGET = 4711
        private const val FOLDER_GRID_COLUMNS = 3
        private const val FOLDER_GRID_ROWS = 4
        private const val FOLDER_PAGE_SIZE = FOLDER_GRID_COLUMNS * FOLDER_GRID_ROWS
        private const val DEFAULT_WIDGET_SPAN = 2
        private const val TOUCH_SLOP = 24f
    }
}
