package com.jetpack.stickify.presentation.ui.edit_sticker

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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

                if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                    onShownListener?.invoke()
                } else {
                    behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                        override fun onStateChanged(bottomSheet: View, newState: Int) {
                            if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                                behavior.removeBottomSheetCallback(this)
                                onShownListener?.invoke()
                            }
                        }

                        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                    })
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            } else {
                onShownListener?.invoke()
            }
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
        val measureAction = Runnable {
            if (!isAdded || isRemoving) return@Runnable
            val vh = rvStickers.findViewHolderForAdapterPosition(0)
            if (vh != null && vh.itemView.width > 0) {
                val loc = IntArray(2)
                vh.itemView.getLocationOnScreen(loc)
                onReady(loc, vh.itemView.width, vh.itemView.height)
            } else {
                val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (!isAdded || isRemoving) return
                        val holder = rvStickers.findViewHolderForAdapterPosition(0)
                        if (holder != null && holder.itemView.width > 0) {
                            try {
                                rvStickers.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            val loc = IntArray(2)
                            holder.itemView.getLocationOnScreen(loc)
                            onReady(loc, holder.itemView.width, holder.itemView.height)
                        }
                    }
                }
                rvStickers.viewTreeObserver.addOnGlobalLayoutListener(listener)
            }
        }

        // Đợi 150ms để Samsung One UI (Galaxy Note 10) hoàn tất hiệu ứng spring/settling
        rvStickers.postDelayed(measureAction, 150)
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
