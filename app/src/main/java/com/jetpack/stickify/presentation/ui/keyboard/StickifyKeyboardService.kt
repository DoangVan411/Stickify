package com.jetpack.stickify.presentation.ui.keyboard

import android.content.ClipDescription
import android.content.Intent
import android.content.res.ColorStateList
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.ViewAnimator
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jetpack.stickify.R
import com.jetpack.stickify.data.source.local.dao.KeyboardStickerDao
import com.jetpack.stickify.domain.model.KeyboardStickerEntity
import com.jetpack.stickify.presentation.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class StickifyKeyboardService : InputMethodService() {

    @Inject
    lateinit var keyboardStickerDao: KeyboardStickerDao

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var viewAnimator: ViewAnimator
    private lateinit var btnToggleStickerPanel: ImageView
    private lateinit var cardRecentToolbar: CardView
    private lateinit var ivRecentToolbar: ImageView
    private lateinit var rvStickerGrid: RecyclerView

    private lateinit var gridAdapter: GridStickerAdapter

    private var isUppercase = false
    private var latestSticker: KeyboardStickerEntity? = null

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_sticker_view, null)

        viewAnimator = view.findViewById(R.id.viewAnimator)
        btnToggleStickerPanel = view.findViewById(R.id.btnToggleStickerPanel)
        cardRecentToolbar = view.findViewById(R.id.cardRecentToolbarSticker)
        ivRecentToolbar = view.findViewById(R.id.ivRecentToolbarSticker)
        rvStickerGrid = view.findViewById(R.id.rvStickerGrid2Col)

        btnToggleStickerPanel.setOnClickListener {
            if (viewAnimator.displayedChild == 0) {
                viewAnimator.displayedChild = 1
                btnToggleStickerPanel.setImageResource(R.drawable.ic_keyboard)
                btnToggleStickerPanel.imageTintList = ColorStateList
                    .valueOf(ContextCompat.getColor(this, R.color.text_border_200))
            } else {
                viewAnimator.displayedChild = 0
                btnToggleStickerPanel.setImageResource(R.drawable.ic_sticker_in_keyboard)
                btnToggleStickerPanel.imageTintList = ColorStateList
                    .valueOf(ContextCompat.getColor(this, R.color.text_border_200))
            }
        }

        cardRecentToolbar.setOnClickListener {
            latestSticker?.let { sticker ->
                commitSticker(sticker)
            }
        }


        // Setup Sticker Grid RecyclerView (2 columns)
        rvStickerGrid.layoutManager = GridLayoutManager(this, 2)
        gridAdapter = GridStickerAdapter { sticker ->
            commitSticker(sticker)
            viewAnimator.displayedChild = 0 // Quay lại bàn phím chữ sau khi chọn sticker
            btnToggleStickerPanel.setImageResource(R.drawable.ic_sticker_in_keyboard)
        }
        rvStickerGrid.adapter = gridAdapter

        setupQwertyKeys(view)
        observeStickers()

        return view
    }

    private fun setupQwertyKeys(view: View) {
        val keyIds = listOf(
            R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5,
            R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9, R.id.key_0,
            R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
            R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
            R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
            R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
            R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v, R.id.key_b,
            R.id.key_n, R.id.key_m, R.id.key_comma, R.id.key_period
        )

        for (id in keyIds) {
            view.findViewById<Button>(id)?.setOnClickListener { v ->
                val btn = v as Button
                val text = btn.text.toString()
                val charToSend = if (isUppercase) text.uppercase() else text
                currentInputConnection?.commitText(charToSend, 1)
            }
        }

        view.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }

        view.findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            val ic = currentInputConnection
            if (ic != null) {
                val selectedText = ic.getSelectedText(0)
                if (!selectedText.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
        }

        view.findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            isUppercase = !isUppercase
            updateKeysUppercase(view)
        }

        view.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            val ic = currentInputConnection
            val editorInfo = currentInputEditorInfo
            if (ic != null && editorInfo != null) {
                val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                when (action) {
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_DONE -> ic.performEditorAction(action)
                    else -> ic.commitText("\n", 1)
                }
            } else {
                currentInputConnection?.commitText("\n", 1)
            }
        }
    }

    private fun updateKeysUppercase(view: View) {
        val letterIds = listOf(
            R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
            R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
            R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
            R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
            R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v, R.id.key_b,
            R.id.key_n, R.id.key_m
        )
        for (id in letterIds) {
            view.findViewById<Button>(id)?.let { btn ->
                val t = btn.text.toString()
                btn.text = if (isUppercase) t.uppercase() else t.lowercase()
            }
        }
    }

    private fun observeStickers() {
        // Single most recent sticker for top toolbar
        serviceScope.launch {
            keyboardStickerDao.observeAll().collectLatest { list ->
                latestSticker = list.firstOrNull()
                if (latestSticker != null) {
                    cardRecentToolbar.isVisible = true
                    Glide.with(this@StickifyKeyboardService)
                        .load(File(latestSticker!!.fileName))
                        .placeholder(R.drawable.ic_sticker_cat)
                        .into(ivRecentToolbar)
                } else {
                    cardRecentToolbar.isVisible = false
                }
            }
        }

        // All stickers for 2-column grid view
        serviceScope.launch {
            keyboardStickerDao.observeAll().collectLatest { list ->
                gridAdapter.submitList(list)
            }
        }
    }

    private fun commitSticker(sticker: KeyboardStickerEntity) {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return

        val file = File(sticker.fileName)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val inputContentInfo = InputContentInfoCompat(
            uri,
            ClipDescription("Sticker", arrayOf(sticker.mimeType)),
            null
        )

        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        val commitSuccess = InputConnectionCompat.commitContent(
            ic,
            editorInfo,
            inputContentInfo,
            flags,
            null
        )

        if (commitSuccess) {
            serviceScope.launch {
                keyboardStickerDao.markUsed(sticker.id, System.currentTimeMillis())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    // Grid Sticker Adapter (2 columns)
    class GridStickerAdapter(
        private val onClick: (KeyboardStickerEntity) -> Unit
    ) : ListAdapter<KeyboardStickerEntity, GridStickerAdapter.ViewHolder>(object : DiffUtil.ItemCallback<KeyboardStickerEntity>() {
        override fun areItemsTheSame(oldItem: KeyboardStickerEntity, newItem: KeyboardStickerEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: KeyboardStickerEntity, newItem: KeyboardStickerEntity) = oldItem == newItem
    }) {

        var hideFirstItemImage: Boolean = false
            set(value) {
                field = value
                notifyItemChanged(0)
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_keyboard_sticker_grid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position), onClick, position == 0 && hideFirstItemImage)
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivThumb: ImageView = view.findViewById(R.id.ivGridStickerThumb)

            fun bind(sticker: KeyboardStickerEntity, onClick: (KeyboardStickerEntity) -> Unit, hideImage: Boolean = false) {
                if (hideImage) {
                    ivThumb.setImageDrawable(null)
                } else {
                    Glide.with(itemView.context)
                        .load(File(sticker.fileName))
                        .placeholder(R.drawable.ic_sticker_cat)
                        .into(ivThumb)
                }

                itemView.setOnClickListener { onClick(sticker) }
            }
        }
    }
}
