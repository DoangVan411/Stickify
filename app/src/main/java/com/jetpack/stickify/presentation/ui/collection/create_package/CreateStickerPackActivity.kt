package com.jetpack.stickify.presentation.ui.collection.create_package

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerProject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateStickerPackActivity : AppCompatActivity() {

    private val viewModel: CreateStickerPackViewModel by viewModels()

    private val selectedStickers = mutableListOf<StickerProject>()
    private lateinit var packEditAdapter: PackStickerEditAdapter

    private lateinit var btnBack: ImageView
    private lateinit var btnSave: MaterialButton
    private lateinit var etPackName: EditText
    private lateinit var etAuthorName: EditText
    private lateinit var rvPackStickers: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_sticker_pack)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        setupDialogListener()

        val packId = intent.getStringExtra(EXTRA_PACK_ID)
        if (!packId.isNullOrEmpty()) {
            viewModel.loadPack(packId) { pack ->
                etPackName.setText(pack.name)
                etAuthorName.setText(pack.author)
                btnSave.text = "Lưu"
                selectedStickers.clear()
                selectedStickers.addAll(pack.stickers)
                packEditAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        etPackName = findViewById(R.id.etPackName)
        etAuthorName = findViewById(R.id.etAuthorName)
        rvPackStickers = findViewById(R.id.rvPackStickers)
    }

    private fun setupToolbar() {
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        packEditAdapter = PackStickerEditAdapter(
            selectedStickers = selectedStickers,
            onAddClick = {
                showStickerPickerDialog()
            },
            onRemoveClick = { position ->
                if (position in selectedStickers.indices) {
                    selectedStickers.removeAt(position)
                    packEditAdapter.notifyDataSetChanged()
                }
            }
        )
        rvPackStickers.apply {
            layoutManager = GridLayoutManager(this@CreateStickerPackActivity, 3)
            adapter = packEditAdapter
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            val name = etPackName.text?.toString().orEmpty()
            val author = etAuthorName.text?.toString().orEmpty()

            viewModel.savePack(name, author, selectedStickers) { success, errorMsg ->
                if (success) {
                    Toast.makeText(this, "Đã lưu bộ sticker thành công", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, errorMsg ?: "Lỗi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupDialogListener() {
        supportFragmentManager.setFragmentResultListener(
            SelectStickersDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val ids = bundle.getStringArrayList(SelectStickersDialogFragment.EXTRA_SELECTED_IDS) ?: emptyList()
            viewModel.loadAvailableStickers { available ->
                selectedStickers.clear()
                selectedStickers.addAll(available.filter { ids.contains(it.id) })
                packEditAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showStickerPickerDialog() {
        val selectedIds = ArrayList(selectedStickers.map { it.id })
        val dialog = SelectStickersDialogFragment.newInstance(selectedIds)
        dialog.show(supportFragmentManager, SelectStickersDialogFragment.TAG)
    }

    class PackStickerEditAdapter(
        private val selectedStickers: List<StickerProject>,
        private val onAddClick: () -> Unit,
        private val onRemoveClick: (Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_ADD = 0
            private const val TYPE_ITEM = 1
        }

        override fun getItemCount(): Int = selectedStickers.size + 1

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) TYPE_ADD else TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_ADD) {
                val view = inflater.inflate(R.layout.item_add_sticker, parent, false)
                AddViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_pack_sticker_thumbnail, parent, false)
                ItemViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is AddViewHolder) {
                holder.itemView.setOnClickListener { onAddClick() }
            } else if (holder is ItemViewHolder) {
                val sticker = selectedStickers[position - 1]
                holder.bind(sticker, position - 1)
            }
        }

        class AddViewHolder(view: View) : RecyclerView.ViewHolder(view)
        inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivThumbnail: ImageView = view.findViewById(R.id.ivStickerThumbnail)
            private val btnRemove: ImageView = view.findViewById(R.id.btnRemoveSticker)

            fun bind(sticker: StickerProject, position: Int) {
                Glide.with(itemView.context)
                    .load(sticker.thumbnailPath)
                    .placeholder(R.drawable.ic_sticker_cat)
                    .into(ivThumbnail)

                btnRemove.setOnClickListener {
                    onRemoveClick(position)
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACK_ID = "extra_pack_id"
    }
}
