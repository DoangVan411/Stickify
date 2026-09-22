package com.jetpack.stickify.presentation.ui.cutimage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jetpack.stickify.R
import com.jetpack.stickify.presentation.ui.cutimage.ContourUtils
import com.jetpack.stickify.presentation.ui.cutimage.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình "Chọn vùng ảnh":
 * - Nhận ảnh qua Intent extra EXTRA_IMAGE_URI.
 * - Tự động cắt chủ thể (ML Kit Subject Segmentation), có xử lý EXIF nên hoạt động
 *   với ảnh chụp ở bất kỳ hướng xoay nào.
 * - Vẽ contour dạng nét đứt chạy (marching ants), làm tối vùng không được chọn khi vào
 *   chế độ chỉnh sửa.
 * - 2 cách chỉnh sửa: kéo từng điểm biên, hoặc vẽ tự do (thêm/tẩy vùng chọn bằng brush).
 * - Khi bấm "Tiếp tục": cắt ảnh theo mask hiện tại (đã có thể bị chỉnh tay), lưu file,
 *   và mở Activity kế tiếp kèm Uri ảnh đã cắt.
 */
class CutoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_CROPPED_IMAGE_URI = "extra_cropped_image_uri"
    }

    private lateinit var overlayView: ContourOverlayView
    private lateinit var btnEdit: Button
    private lateinit var btnContinue: Button
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var editToolsRow: View
    private lateinit var btnToolPoints: Button
    private lateinit var btnToolBrushAdd: Button
    private lateinit var btnToolBrushErase: Button

    private var sourceBitmap: Bitmap? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cutout)

        overlayView = findViewById(R.id.overlayView)
        btnEdit = findViewById(R.id.btnEdit)
        btnContinue = findViewById(R.id.btnContinue)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)
        editToolsRow = findViewById(R.id.editToolsRow)
        btnToolPoints = findViewById(R.id.btnToolPoints)
        btnToolBrushAdd = findViewById(R.id.btnToolBrushAdd)
        btnToolBrushErase = findViewById(R.id.btnToolBrushErase)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        btnEdit.setOnClickListener { toggleEditMode() }
        btnToolPoints.setOnClickListener { selectTool(EditTool.POINTS) }
        btnToolBrushAdd.setOnClickListener { selectTool(EditTool.BRUSH_ADD) }
        btnToolBrushErase.setOnClickListener { selectTool(EditTool.BRUSH_ERASE) }

        btnContinue.setOnClickListener { onContinueClicked() }

        setButtonsEnabled(false)

        val imageUri: Uri? = intent.getParcelableExtra(EXTRA_IMAGE_URI)
        if (imageUri == null) {
            Toast.makeText(this, "Không nhận được ảnh đầu vào", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadAndSegment(imageUri)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnEdit.isEnabled = enabled
        btnContinue.isEnabled = enabled
        btnContinue.alpha = if (enabled) 1f else 0.5f
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        overlayView.editMode = isEditMode
        btnEdit.text = if (isEditMode) "Xong" else "Chỉnh sửa"
        editToolsRow.visibility = if (isEditMode) View.VISIBLE else View.GONE
        if (isEditMode) selectTool(EditTool.POINTS) // mặc định vào chế độ kéo điểm trước
    }

    private fun selectTool(tool: EditTool) {
        overlayView.currentTool = tool
        updateToolButtonStyles(tool)
    }

    private fun updateToolButtonStyles(selected: EditTool) {
        val selectedColor = Color.parseColor("#2196F3")
        val defaultColor = Color.parseColor("#F0F0F0")

        fun style(button: Button, isSelected: Boolean) {
            button.setBackgroundColor(if (isSelected) selectedColor else defaultColor)
            button.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#333333"))
        }
        style(btnToolPoints, selected == EditTool.POINTS)
        style(btnToolBrushAdd, selected == EditTool.BRUSH_ADD)
        style(btnToolBrushErase, selected == EditTool.BRUSH_ERASE)
    }

    private fun loadAndSegment(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.loadUprightBitmap(this@CutoutActivity, uri)
                }
                sourceBitmap = bitmap
                overlayView.setImageBitmap(bitmap)

                // Ảnh đã upright -> rotationDegrees = 0.
                val maskResult = SegmentationHelper.segment(bitmap)

                val initialMask = withContext(Dispatchers.Default) {
                    ContourUtils.createMaskFromBooleanArray(
                        maskResult.foregroundMask, maskResult.width, maskResult.height
                    )
                }
                // setSelectionMask tự dò lại polygon để vẽ nét đứt + handle.
                overlayView.setSelectionMask(initialMask)
                overlayView.startDashAnimation()
                setButtonsEnabled(true)
            } catch (e: Exception) {
                Toast.makeText(
                    this@CutoutActivity,
                    "Lỗi khi xử lý ảnh: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun onContinueClicked() {
        val bitmap = sourceBitmap ?: return
        val mask = overlayView.getSelectionMask()
        if (mask == null) {
            Toast.makeText(this, "Chưa có vùng chọn nào", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        setButtonsEnabled(false)

        lifecycleScope.launch {
            try {
                val croppedUri = withContext(Dispatchers.Default) {
                    val cropped = ContourUtils.cutoutBitmapFromMask(bitmap, mask)
                    ImageUtils.saveBitmapAndGetUri(this@CutoutActivity, cropped)
                }

//                val intent = Intent(this@CutoutActivity, NextActivity::class.java).apply {
//                    putExtra(EXTRA_CROPPED_IMAGE_URI, croppedUri)
//                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CutoutActivity, "Lỗi khi cắt ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                setButtonsEnabled(true)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}