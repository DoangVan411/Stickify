package com.jetpack.stickify.presentation.ui.home

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.presentation.ui.home.adapter.RecentAction
import com.jetpack.stickify.presentation.ui.home.adapter.RecentProjectAdapter

class AllRecentProjectsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var projects: List<ProjectEntity> = emptyList()

    var onItemClick: ((ProjectEntity) -> Unit)? = null
    var onActionClick: ((ProjectEntity, RecentAction) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dlg ->
            val bottomSheetDialog = dlg as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isFitToContents = false
                behavior.expandedOffset = 0
                bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                bottomSheet.requestLayout()
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val root = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding((16 * resources.displayMetrics.density).toInt())
        }

        val title = TextView(context).apply {
            text = "Tất cả dự án gần đây"
            textSize = 18f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
        }

        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(context)
            adapter = RecentProjectAdapter(
                isVertical = true,
                onItemClick = { project ->
                    dismiss()
                    onItemClick?.invoke(project)
                },
                onActionClick = { project, action ->
                    dismiss()
                    onActionClick?.invoke(project, action)
                }
            ).apply {
                submitList(projects.sortedByDescending { it.updatedAt })
            }
        }

        root.addView(title)
        root.addView(recyclerView)
        return root
    }

    private fun setProjectsData(projectList: List<ProjectEntity>) {
        projects = projectList
    }

    companion object {
        const val TAG = "AllRecentProjectsBottomSheetDialogFragment"

        fun newInstance(projects: List<ProjectEntity>): AllRecentProjectsBottomSheetDialogFragment {
            return AllRecentProjectsBottomSheetDialogFragment().apply {
                setProjectsData(projects)
            }
        }
    }
}
