package com.jetpack.stickify.presentation.ui.edit_sticker.border


import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerSharedViewModel
import androidx.core.graphics.toColorInt

class BorderToolFragment : Fragment() {

    // KẾT NỐI VỚI VIEWMODEL CHUNG CỦA ACTIVITY (CLEAN ARCHITECTURE)
    private val sharedViewModel: StickerSharedViewModel by activityViewModels()

    private lateinit var sbThickness: SeekBar
    private lateinit var sbDistance: SeekBar
    private lateinit var rvColors: RecyclerView

    // Dải màu mẫu theo thiết kế: Trắng, Đỏ, Cam, Vàng, Xanh lá, Xanh lơ, Xanh biển, Hồng, Nâu
    private val defaultColors = listOf(
        "#FFFFFF".toColorInt(),
        "#F44336".toColorInt(),
        "#FF9800".toColorInt(),
        "#FFEB3B".toColorInt(),
        "#4CAF50".toColorInt(),
        "#00BCD4".toColorInt(),
        "#2196F3".toColorInt(),
        "#E91E63".toColorInt(),
        "#795548".toColorInt()
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_border_tool, container, false)
        sbThickness = view.findViewById(R.id.sbThickness)
        sbDistance = view.findViewById(R.id.sbDistance)
        rvColors = view.findViewById(R.id.rvColors)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setBorderInit()
        setupColorPicker()
        setupSeekBars()
    }

    private fun setBorderInit() {
        val thickness = sharedViewModel.currentBorderThickness.value ?: 30
        val distance = sharedViewModel.currentBorderDistance.value ?: 20
        val color = sharedViewModel.currentBorderColor.value ?: Color.WHITE
        sharedViewModel.updateBorderConfig(thickness, distance, color)
    }

    private fun setupColorPicker() {
        val adapter = ColorPickerAdapter(
            colors = defaultColors,
            selectedColor = sharedViewModel.currentBorderColor.value ?: Color.WHITE
        ) { selectedColor ->
            // Báo ViewModel cập nhật Màu Sắc
            sharedViewModel.updateBorderConfig(color = selectedColor)
        }
        rvColors.adapter = adapter
    }

    private fun setupSeekBars() {
        // Đồng bộ giá trị hiện tại từ ViewModel ra UI
        sbThickness.progress = sharedViewModel.currentBorderThickness.value ?: 30
        sbDistance.progress = sharedViewModel.currentBorderDistance.value ?: 20

        val changeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    when (seekBar?.id) {
                        R.id.sbThickness -> sharedViewModel.updateBorderConfig(thickness = progress)
                        R.id.sbDistance -> sharedViewModel.updateBorderConfig(distance = progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sbThickness.setOnSeekBarChangeListener(changeListener)
        sbDistance.setOnSeekBarChangeListener(changeListener)
    }
}