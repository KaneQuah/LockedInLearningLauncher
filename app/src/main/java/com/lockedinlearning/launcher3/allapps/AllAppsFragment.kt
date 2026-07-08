package com.lockedinlearning.launcher3.allapps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.lockedinlearning.R
import com.lockedinlearning.launcher3.LauncherSettings
import com.lockedinlearning.launcher3.model.AppInfo
import com.lockedinlearning.launcher3.view.PopupContainerWithArrow
import com.lockedinlearning.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The "All Apps" drawer — reached by swiping up from Home (see RevealGestureDetector) or the
 * dedicated handle. Deliberately shares no UI with the top status row / Gate: it observes
 * HomeViewModel (scoped to the host Activity) only for the installed-app list, icon appearance,
 * and badges — never streak/gate state — so the S-09 top row can never leak into this surface.
 */
@AndroidEntryPoint
class AllAppsFragment : Fragment() {

    private val homeViewModel: HomeViewModel by activityViewModels()

    private lateinit var recycler: RecyclerView
    private lateinit var alphabetIndex: AlphabetIndexView
    private lateinit var searchField: TextInputEditText
    private lateinit var adapter: AppsAdapter

    private var sortedApps: List<AppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_all_apps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.all_apps_recycler)
        alphabetIndex = view.findViewById(R.id.alphabet_index)
        searchField = view.findViewById(R.id.search_apps)

        val root = view.findViewById<View>(R.id.all_apps_root)
        val searchInitialTop = (searchField.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        val recyclerInitialBottom = recycler.paddingBottom
        val alphabetIndexInitialBottom = alphabetIndex.paddingBottom
        val bottomBufferPx = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (searchField.layoutParams as ViewGroup.MarginLayoutParams).topMargin = searchInitialTop + bars.top
            searchField.requestLayout()
            recycler.updatePadding(bottom = recyclerInitialBottom + bars.bottom)
            // Compact the rail so the last row ('#') sits clear of the nav bar with a visible gap,
            // rather than dividing the full edge-to-edge height evenly across all 27 rows.
            alphabetIndex.updatePadding(bottom = alphabetIndexInitialBottom + bars.bottom + bottomBufferPx)
            insets
        }

        adapter = AppsAdapter(
            packageManager = requireContext().packageManager,
            onClick = { app ->
                runCatching {
                    requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
                        ?.let { startActivity(it) }
                }
            },
            onLongClick = { app -> showAddToHomeMenu(app) }
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), LauncherSettings.GRID_SIZE)
        recycler.adapter = adapter

        alphabetIndex.setOnLetterSelectedListener { letter ->
            val index = adapter.indexOfFirstWithLetter(letter)
            if (index >= 0) (recycler.layoutManager as GridLayoutManager).scrollToPosition(index)
        }

        searchField.addTextChangedListener { editable ->
            applyFilter(editable?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.state.collect { state ->
                    sortedApps = state.installedApps.sortedBy { it.label.lowercase() }
                    adapter.appearance = state.iconAppearance
                    adapter.notificationBadges = state.notificationBadges
                    adapter.showBadgeCount = state.showBadgeCount
                    applyFilter(searchField.text?.toString().orEmpty())
                }
            }
        }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) sortedApps else sortedApps.filter {
            it.label.contains(query, ignoreCase = true)
        }
        adapter.submitList(filtered)
        alphabetIndex.visibility = if (query.isBlank()) View.VISIBLE else View.INVISIBLE
        if (query.isBlank()) {
            val letters = sortedApps.map { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c in 'A'..'Z' } ?: '#' }.toSet()
            alphabetIndex.setAvailableLetters(letters)
        }
    }

    /** Used by RevealGestureDetector to tell an ordinary list-scroll-up-then-down apart from a
     *  swipe-down-to-close: only the latter should be allowed once nothing's left to scroll into. */
    fun isScrolledToTop(): Boolean = !::recycler.isInitialized || !recycler.canScrollVertically(-1)

    private fun showAddToHomeMenu(app: AppInfo) {
        val anchor = recycler
        PopupContainerWithArrow.show(
            anchor,
            listOf(app.label to {}, "Add to Home Screen" to { homeViewModel.addAppToHome(app.packageName) })
        )
    }
}
