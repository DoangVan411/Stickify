package com.jetpack.stickify.presentation.ui.home

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ActivityHomeBinding
import com.jetpack.stickify.presentation.ui.gallery.GalleryActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity chính cho HomeScreen.
 * Chứa floating bottom bar với nút [+] và thanh pill navigation bo góc.
 */
@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var navController: NavController

    enum class BottomNavTab {
        HOME, COLLECTION, RESOURCES
    }

    private var currentTab: BottomNavTab = BottomNavTab.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Xử lý edge-to-edge insets cho floating bottom bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            val baseMarginPx = (16 * resources.displayMetrics.density).toInt()
            val lp = binding.layoutBottomBar.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = systemBars.bottom + baseMarginPx
            binding.layoutBottomBar.layoutParams = lp

            insets
        }

        setupNavigation()
        setupBottomBar()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragmentHome) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun setupBottomBar() {
        // Nút tròn [+] tạo mới
        binding.btnCreate.setOnClickListener {
            var gallery = Intent(this, GalleryActivity::class.java)
            startActivity(gallery)
        }

        // Click các tab
        binding.tabHome.setOnClickListener { selectTab(BottomNavTab.HOME) }
        binding.tabCollection.setOnClickListener { selectTab(BottomNavTab.COLLECTION) }
        binding.tabResources.setOnClickListener { selectTab(BottomNavTab.RESOURCES) }

        // Khởi tạo tab mặc định
        selectTab(BottomNavTab.HOME)
    }

    private fun selectTab(tab: BottomNavTab) {
        currentTab = tab

        val primaryColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val unselectedColor = ContextCompat.getColor(this, R.color.text_border_75)

        updateTabItem(
            tabView = binding.tabHome,
            iconView = binding.ivTabHome,
            textView = binding.tvTabHome,
            isSelected = tab == BottomNavTab.HOME,
            selectedColor = primaryColor,
            unselectedColor = unselectedColor
        )

        updateTabItem(
            tabView = binding.tabCollection,
            iconView = binding.ivTabCollection,
            textView = binding.tvTabCollection,
            isSelected = tab == BottomNavTab.COLLECTION,
            selectedColor = primaryColor,
            unselectedColor = unselectedColor
        )

        updateTabItem(
            tabView = binding.tabResources,
            iconView = binding.ivTabResources,
            textView = binding.tvTabResources,
            isSelected = tab == BottomNavTab.RESOURCES,
            selectedColor = primaryColor,
            unselectedColor = unselectedColor
        )

        when (tab) {
            BottomNavTab.HOME -> {
                if (navController.currentDestination?.id != R.id.homeFragment) {
                    navController.popBackStack(R.id.homeFragment, false)
                }
            }
            BottomNavTab.COLLECTION -> {
                Toast.makeText(this, getString(R.string.tab_collection), Toast.LENGTH_SHORT).show()
            }
            BottomNavTab.RESOURCES -> {
                Toast.makeText(this, getString(R.string.tab_resources), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTabItem(
        tabView: LinearLayout,
        iconView: ImageView,
        textView: TextView,
        isSelected: Boolean,
        selectedColor: Int,
        unselectedColor: Int
    ) {
        if (isSelected) {
            tabView.setBackgroundResource(R.drawable.bg_nav_item_selected)
            ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(selectedColor))
            textView.setTextColor(selectedColor)
            textView.setTypeface(null, Typeface.BOLD)
        } else {
            tabView.setBackgroundResource(android.R.color.transparent)
            ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(unselectedColor))
            textView.setTextColor(unselectedColor)
            textView.setTypeface(null, Typeface.NORMAL)
        }
    }
}
