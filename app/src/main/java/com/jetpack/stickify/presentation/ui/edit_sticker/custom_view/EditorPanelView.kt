package com.jetpack.stickify.presentation.ui.edit_sticker.custom_view
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jetpack.stickify.R


data class TabItem(
    val id :String,
    val title: String,
    val iconResId: Int
)

class EditorPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // Lắng nghe sự kiện truyền ngược lại Activity để xử lý vùng ảnh
    interface OnTabSelectedListener {
        fun onTabSelected(position: Int, tabName: String)
    }
    private var tabListener: OnTabSelectedListener? = null

    private val viewTabHighlight: View
    private val llTabContainer: LinearLayout

    // Dữ liệu mô phỏng cho Tab (để hiển thị icon và chữ)
    // Khai báo danh sách các tab kèm icon (đảm bảo bạn đã thêm các vector asset này vào thư mục res/drawable)
    private val tabs = listOf(
        TabItem("suggest","Đề xuất", R.drawable.ic_spark),  // Icon ngôi sao lấp lánh
        TabItem("text","Chữ", R.drawable.ic_text),         // Icon chữ T
        TabItem("effect","Hiệu ứng", R.drawable.ic_effect),       // Icon cây đũa phép
        TabItem("decore","Trang trí", R.drawable.ic_decore),     // Icon bông hoa
        TabItem("border","Viền", R.drawable.ic_border)         // Icon khung viền nét đứt
    )
    private var currentTabIndex = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.view_editor_panel, this, true)

        viewTabHighlight = findViewById(R.id.vTabHighlight)
        llTabContainer = findViewById(R.id.llTabContainer)

        setupTabs()
    }

    fun setOnTabSelectedListener(listener: OnTabSelectedListener) {

        this.tabListener = listener
    }



    private fun setupTabs() {
        llTabContainer.removeAllViews()
        for (i in tabs.indices) {
            val tabView = LayoutInflater.from(context).inflate(R.layout.item_top_tab, llTabContainer, false)
            val tvName = tabView.findViewById<TextView>(R.id.tvTabName)
            val ivIcon = tabView.findViewById<ImageView>(R.id.ivTabIcon)

            tvName.text = tabs[i].title
            ivIcon.setImageResource(tabs[i].iconResId)

            // Xử lý sự kiện click để trượt Background
            tabView.setOnClickListener {
                animateTabSelection(i, tabView)
            }
            llTabContainer.addView(tabView)
        }

        // Mặc định chọn tab đầu tiên
        llTabContainer.post {
            animateTabSelection(0, llTabContainer.getChildAt(0))
        }
    }

    private fun animateTabSelection(index: Int, selectedView: View) {
        currentTabIndex = index

        // Gọi listener này khi user click vào một Tab
        tabListener?.onTabSelected(index, tabs[index].title)

        // 1. Animation trượt nền xanh (vTabHighlight) đến vị trí của tab được click
        viewTabHighlight.animate()
            .x(selectedView.x)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(0.8f)) // Hiệu ứng nảy nhẹ
            .start()

        // 2. Thay đổi màu sắc chữ và icon
        for (i in 0 until llTabContainer.childCount) {
            val child = llTabContainer.getChildAt(i)
            val tvName = child.findViewById<TextView>(R.id.tvTabName)
            val ivIcon = child.findViewById<ImageView>(R.id.ivTabIcon)

            if (i == index) {
                // Đang chọn: Đổi sang màu xanh
                tvName.setTextColor(Color.parseColor("#1B85F3"))
                ivIcon.setColorFilter(Color.parseColor("#1B85F3"))
            } else {
                // Bỏ chọn: Trả về màu đen/xám
                tvName.setTextColor(Color.parseColor("#111111"))
                ivIcon.setColorFilter(Color.parseColor("#111111"))
            }
        }

    }

}