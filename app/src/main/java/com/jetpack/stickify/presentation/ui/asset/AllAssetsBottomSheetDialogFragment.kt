package com.jetpack.stickify.presentation.ui.asset

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jetpack.stickify.domain.model.AssetEntity

class AllAssetsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var title: String = ""
    private var assets: List<AssetEntity> = emptyList()
    var onItemClick: ((AssetEntity) -> Unit)? = null
    var onAddClick: (() -> Unit)? = null
    var onItemLongClick: ((AssetEntity, View) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = arguments?.getString(ARG_TITLE) ?: ""
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

        val recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = GridLayoutManager(context, 3)
            adapter = AssetAdapter(
                assets = assets.sortedByDescending { it.createdAt },
                onAddClick = {
                    dismiss()
                    onAddClick?.invoke()
                },
                onItemClick = { asset ->
                    dismiss()
                    onItemClick?.invoke(asset)
                },
                onItemLongClick = onItemLongClick
            )
        }

        root.addView(recyclerView)
        return root
    }

    fun setAssetsData(assetList: List<AssetEntity>) {
        assets = assetList
    }

    companion object {
        const val TAG = "AllAssetsBottomSheetDialogFragment"
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String, assets: List<AssetEntity>): AllAssetsBottomSheetDialogFragment {
            return AllAssetsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
                setAssetsData(assets)
            }
        }
    }
}
