package com.jetpack.stickify.presentation.ui.edit_sticker

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jetpack.stickify.R
import com.jetpack.stickify.data.source.local.dao.KeyboardStickerDao
import com.jetpack.stickify.presentation.ui.keyboard.StickifyKeyboardService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardStickersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var keyboardStickerDao: KeyboardStickerDao

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var rvStickers: RecyclerView
    private lateinit var gridAdapter: StickifyKeyboardService.GridStickerAdapter

    var onShownListener: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                bottomSheet.background = ColorDrawable(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                val displayMetrics = requireContext().resources.displayMetrics
                val height = (displayMetrics.heightPixels * 7) / 16
                bottomSheet.layoutParams.height = height
                behavior.peekHeight = height
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            onShownListener?.invoke()
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_keyboard_stickers_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvStickers = view.findViewById(R.id.rvBottomSheetStickers)
        rvStickers.layoutManager = GridLayoutManager(requireContext(), 2)

        gridAdapter = StickifyKeyboardService.GridStickerAdapter { _ ->
            dismiss()
        }
        gridAdapter.hideFirstItemImage = true // Đặt ô vị trí đầu tiên thành trắng/trống khi đang chờ bay vào
        rvStickers.adapter = gridAdapter

        scope.launch {
            keyboardStickerDao.observeAll().collectLatest { list ->
                gridAdapter.submitList(list)
            }
        }
    }

    fun getTopLeftItemScreenLocation(onReady: (IntArray, Int, Int) -> Unit) {
        rvStickers.post {
            val viewHolder = rvStickers.findViewHolderForAdapterPosition(0)
            if (viewHolder != null) {
                val loc = IntArray(2)
                viewHolder.itemView.getLocationOnScreen(loc)
                onReady(loc, viewHolder.itemView.width, viewHolder.itemView.height)
            } else {
                val loc = IntArray(2)
                rvStickers.getLocationOnScreen(loc)
                onReady(loc, rvStickers.width / 2, 120)
            }
        }
    }

    fun setFlyingCompleted() {
        gridAdapter.hideFirstItemImage = false
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val TAG = "KeyboardStickersBottomSheetDialogFragment"
        fun newInstance() = KeyboardStickersBottomSheetDialogFragment()
    }
}
