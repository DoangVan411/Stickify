package com.jetpack.stickify.presentation.ui.edit_sticker.effect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerEditActivity
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerEditViewModel
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerSharedViewModel
import kotlinx.coroutines.launch

/**
 * Fragment hiển thị bảng chọn hiệu ứng animation cho sticker.
 * Được load khi user bấm tab "Hiệu ứng" (position = 2) trong EditorPanelView.
 *
 * Hiển thị grid 3 cột với các hiệu ứng: Không, Nhún nhảy, Rung lắc,
 * Xoay tròn, Co giãn, Lắc lư.
 */
class EffectToolFragment : Fragment() {

    private val editViewModel: StickerEditViewModel by activityViewModels()
    private val sharedViewModel: StickerSharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_effect_tool, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvEffects = view.findViewById<RecyclerView>(R.id.rvEffects)

        val effectItems = listOf(
            EffectAdapter.EffectItem(
                StickerAnimationType.NONE,
                R.drawable.ic_effect_none,
                "Không"
            ),
            EffectAdapter.EffectItem(
                StickerAnimationType.BOUNCE,
                R.drawable.ic_effect_bounce,
                "Nhún nhảy"
            ),
            EffectAdapter.EffectItem(
                StickerAnimationType.SHAKE,
                R.drawable.ic_effect_shake,
                "Rung lắc"
            ),
            EffectAdapter.EffectItem(
                StickerAnimationType.SPIN,
                R.drawable.ic_effect_spin,
                "Xoay tròn"
            ),
            EffectAdapter.EffectItem(
                StickerAnimationType.PULSE,
                R.drawable.ic_effect_pulse,
                "Co giãn"
            ),
            EffectAdapter.EffectItem(
                StickerAnimationType.WOBBLE,
                R.drawable.ic_effect_wobble,
                "Lắc lư"
            )
        )

        val initialType = sharedViewModel.currentAnimation.value ?: StickerAnimationType.NONE

        val adapter = EffectAdapter(effectItems, initialType) { animationType ->
            val act = activity as? StickerEditActivity
            if (act?.isProjectMode == true) {
                if (act.hasAiAnimation()) {
                    Toast.makeText(context, "Sticker AI không áp dụng hiệu ứng chuyển động", Toast.LENGTH_SHORT).show()
                } else {
                    editViewModel.setEffect(animationType)
                }
            } else {
                sharedViewModel.setAnimation(animationType)
            }
        }

        rvEffects.layoutManager = GridLayoutManager(requireContext(), 3)
        rvEffects.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                editViewModel.uiState.collect { state ->
                    val animType = state.editorSession.content.playback?.animationType ?: StickerAnimationType.NONE
                    adapter.setSelectedType(animType)
                }
            }
        }
    }
}
