package com.jetpack.stickify.presentation.ui.collection.create_package

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.usecase.GetFavoritesUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SelectStickersDialogFragment : DialogFragment() {

    @Inject
    lateinit var getFavoritesUseCase: GetFavoritesUseCase

    private var initiallySelectedIds: ArrayList<String> = arrayListOf()
    private val currentSelectedIds = mutableSetOf<String>()

    private lateinit var adapter: PickerAdapter
    private val allAvailableStickers = mutableListOf<StickerProject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initiallySelectedIds = arguments?.getStringArrayList(ARG_SELECTED) ?: arrayListOf()
        currentSelectedIds.addAll(initiallySelectedIds)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_select_stickers, container, false)
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

        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearch)
        val rvPickerStickers = view.findViewById<RecyclerView>(R.id.rvPickerStickers)
        val progressBarPicker = view.findViewById<ProgressBar>(R.id.progressBarPicker)
        val btnCancel = view.findViewById<View>(R.id.btnCancelPicker)
        val btnAdd = view.findViewById<View>(R.id.btnAddPicker)

        adapter = PickerAdapter(allAvailableStickers, currentSelectedIds) { sticker, isChecked ->
            if (isChecked) {
                currentSelectedIds.add(sticker.id)
            } else {
                currentSelectedIds.remove(sticker.id)
            }
        }

        rvPickerStickers.layoutManager = LinearLayoutManager(requireContext())
        rvPickerStickers.adapter = adapter

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
                bundleOf(EXTRA_SELECTED_IDS to ArrayList(currentSelectedIds))
            )
            dismiss()
        }

        // Load available stickers asynchronously inside dialog
        progressBarPicker.isVisible = true
        rvPickerStickers.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = getFavoritesUseCase()
            progressBarPicker.isVisible = false
            result.onSuccess { stickers ->
                allAvailableStickers.clear()
                allAvailableStickers.addAll(stickers)
                adapter.updateData(stickers)
                rvPickerStickers.isVisible = true
            }.onFailure { e ->
                Toast.makeText(context, "Lỗi tải sticker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class PickerAdapter(
        private var allStickers: List<StickerProject>,
        private val selectedIds: MutableSet<String>,
        private val onCheckChanged: (StickerProject, Boolean) -> Unit
    ) : RecyclerView.Adapter<PickerAdapter.PickerViewHolder>() {

        private var filteredList: List<StickerProject> = allStickers

        fun updateData(newStickers: List<StickerProject>) {
            allStickers = newStickers
            filteredList = newStickers
            notifyDataSetChanged()
        }

        fun filter(query: String) {
            filteredList = if (query.isBlank()) {
                allStickers
            } else {
                allStickers.filter { it.name.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_sticker, parent, false)
            return PickerViewHolder(view)
        }

        override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
            holder.bind(filteredList[position])
        }

        override fun getItemCount(): Int = filteredList.size

        inner class PickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivThumbnail: ImageView = view.findViewById(R.id.ivPickerThumbnail)
            private val tvName: TextView = view.findViewById(R.id.tvPickerName)
            private val checkbox: MaterialCheckBox = view.findViewById(R.id.checkboxPicker)

            fun bind(sticker: StickerProject) {
                tvName.text = sticker.name
                val isChecked = selectedIds.contains(sticker.id)
                checkbox.isChecked = isChecked

                Glide.with(itemView.context)
                    .load(sticker.thumbnailPath)
                    .placeholder(R.drawable.ic_sticker_cat)
                    .into(ivThumbnail)

                itemView.setOnClickListener {
                    val newState = !checkbox.isChecked
                    checkbox.isChecked = newState
                    onCheckChanged(sticker, newState)
                }

                checkbox.setOnClickListener {
                    val newState = checkbox.isChecked
                    onCheckChanged(sticker, newState)
                }
            }
        }
    }

    companion object {
        const val TAG = "SelectStickersDialogFragment"
        const val REQUEST_KEY = "select_stickers_request_key"
        const val EXTRA_SELECTED_IDS = "extra_selected_ids"

        private const val ARG_SELECTED = "arg_selected"

        fun newInstance(selectedIds: ArrayList<String>): SelectStickersDialogFragment {
            return SelectStickersDialogFragment().apply {
                arguments = bundleOf(
                    ARG_SELECTED to selectedIds
                )
            }
        }
    }
}
