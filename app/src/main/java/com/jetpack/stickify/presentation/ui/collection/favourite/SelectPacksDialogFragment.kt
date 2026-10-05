package com.jetpack.stickify.presentation.ui.collection.favourite

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.presentation.ui.collection.CollectionFragment

class SelectPacksDialogFragment : DialogFragment() {

    private val selectedPackIds = mutableSetOf<String>()
    private lateinit var adapter: PackPickerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_select_packs, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val displayMetrics = requireContext().resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.92).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearchPack)
        val rvPickerPacks = view.findViewById<RecyclerView>(R.id.rvPickerPacks)
        val btnCancel = view.findViewById<View>(R.id.btnCancelPackPicker)
        val btnAdd = view.findViewById<View>(R.id.btnAddPackPicker)

        val available = CollectionFragment.tempAvailablePacks

        adapter = PackPickerAdapter(available, selectedPackIds) { pack, isChecked ->
            if (isChecked) {
                selectedPackIds.add(pack.id)
            } else {
                selectedPackIds.remove(pack.id)
            }
        }

        rvPickerPacks.layoutManager = LinearLayoutManager(requireContext())
        rvPickerPacks.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnAdd.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(EXTRA_SELECTED_PACK_IDS to ArrayList(selectedPackIds))
            )
            dismiss()
        }
    }

    class PackPickerAdapter(
        private val allPacks: List<StickerPack>,
        private val selectedIds: MutableSet<String>,
        private val onCheckChanged: (StickerPack, Boolean) -> Unit
    ) : RecyclerView.Adapter<PackPickerAdapter.PackViewHolder>() {

        private var filteredList: List<StickerPack> = allPacks

        fun filter(query: String) {
            filteredList = if (query.isBlank()) {
                allPacks
            } else {
                allPacks.filter { it.name.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_pack, parent, false)
            return PackViewHolder(view)
        }

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            holder.bind(filteredList[position])
        }

        override fun getItemCount(): Int = filteredList.size

        inner class PackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivThumbnail: ImageView = view.findViewById(R.id.ivPickerPackThumbnail)
            private val tvName: TextView = view.findViewById(R.id.tvPickerPackName)
            private val tvCount: TextView = view.findViewById(R.id.tvPickerPackCount)
            private val checkbox: MaterialCheckBox = view.findViewById(R.id.checkboxPackPicker)

            fun bind(pack: StickerPack) {
                tvName.text = pack.name
                tvCount.text = "${pack.itemCount} stickers"
                val isChecked = selectedIds.contains(pack.id)
                checkbox.isChecked = isChecked

                Glide.with(itemView.context)
                    .load(pack.trayImagePath.ifEmpty { pack.stickers.firstOrNull()?.thumbnailPath })
                    .placeholder(R.drawable.ic_sticker_cat)
                    .into(ivThumbnail)

                itemView.setOnClickListener {
                    val newState = !checkbox.isChecked
                    checkbox.isChecked = newState
                    onCheckChanged(pack, newState)
                }

                checkbox.setOnClickListener {
                    val newState = checkbox.isChecked
                    onCheckChanged(pack, newState)
                }
            }
        }
    }

    companion object {
        const val TAG = "SelectPacksDialogFragment"
        const val REQUEST_KEY = "select_packs_request_key"
        const val EXTRA_SELECTED_PACK_IDS = "extra_selected_pack_ids"

        fun newInstance(): SelectPacksDialogFragment {
            return SelectPacksDialogFragment()
        }
    }
}