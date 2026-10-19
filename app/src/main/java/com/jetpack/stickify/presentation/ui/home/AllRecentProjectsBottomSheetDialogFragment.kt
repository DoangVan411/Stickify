package com.jetpack.stickify.presentation.ui.home

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

    private val projects = mutableListOf<ProjectEntity>()
    private var adapter: RecentProjectAdapter? = null
    var onItemClick: ((ProjectEntity) -> Unit)? = null
    var onActionClick: ((ProjectEntity, RecentAction) -> Unit)? = null

    fun setProjects(list: List<ProjectEntity>) {
        projects.clear()
        projects.addAll(list)
        adapter?.submitList(projects)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dlg ->
            val bsd = dlg as BottomSheetDialog
            val bottomSheet = bsd.findViewById<FrameLayout>(R.id.design_bottom_sheet)
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            setPadding(24, 32, 24, 24)
        }

        adapter = RecentProjectAdapter(
            isVertical = true,
            onItemClick = { project ->
                dismiss()
                onItemClick?.invoke(project)
            },
            onActionClick = { project, action ->
                onActionClick?.invoke(project, action)
            }
        ).apply {
            submitList(projects)
        }

        val recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = this@AllRecentProjectsBottomSheetDialogFragment.adapter
        }

        root.addView(recyclerView)
        return root
    }

    companion object {
        const val TAG = "AllRecentProjectsBottomSheetDialogFragment"

        fun newInstance(projects: List<ProjectEntity>): AllRecentProjectsBottomSheetDialogFragment {
            return AllRecentProjectsBottomSheetDialogFragment().apply {
                setProjects(projects)
            }
        }
    }
}
