package com.jetpack.stickify.presentation.ui.cutimage

import android.content.Intent
import android.content.res.Resources
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
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ActivityCutoutBinding
import com.jetpack.stickify.presentation.ui.cutimage.ContourUtils
import com.jetpack.stickify.presentation.ui.cutimage.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import com.jetpack.stickify.presentation.ui.editsticker.StickerEditActivity
import io.ktor.sse.COLON

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

    private lateinit var cutOutBinding:ActivityCutoutBinding

    private lateinit var overlayView: ContourOverlayView
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnContinue: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var editToolsRow: View
    private lateinit var btnToolPoints: Button
    private lateinit var btnToolBrushAdd: Button
    private lateinit var btnToolBrushErase: Button

    private var sourceBitmap: Bitmap? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cutOutBinding = DataBindingUtil.setContentView(this,R.layout.activity_cutout)

        overlayView = findViewById(R.id.overlayView)
        btnEdit = cutOutBinding.btnEdit
        btnContinue = cutOutBinding.btnContinue
        btnBack = findViewById(R.id.btnBack)
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
        val selectedColor = "#2196F3".toColorInt()
        val defaultColor = ContextCompat.getColor(this, R.color.text_border_t300)

        fun style(button: Button, isSelected: Boolean) {
            button.setTextColor(if (isSelected) selectedColor else defaultColor)
        }
        style(btnToolPoints, selected == EditTool.POINTS)
        style(btnToolBrushAdd, selected == EditTool.BRUSH_ADD)
        style(btnToolBrushErase, selected == EditTool.BRUSH_ERASE)
    }

    private fun loadAndSegment(uri: Uri) {
        cutOutBinding.loadingContainer.visibility = View.VISIBLE
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
                cutOutBinding.loadingContainer.visibility = View.GONE
            }
        }
    }

    private fun onContinueClicked() {
        val bitmap = sourceBitmap ?: return
        val points = overlayView.getContourPointsInBitmapSpace()
        if (points.size < 3) {
            Toast.makeText(this, "Cần ít nhất 3 điểm để cắt ảnh", Toast.LENGTH_SHORT).show()
            return
        }

        cutOutBinding.loadingContainer.visibility = View.VISIBLE
        setButtonsEnabled(false)

        lifecycleScope.launch {
            try {
                val cropped = withContext(Dispatchers.Default) {
                    ContourUtils.cutoutBitmapFromPath(bitmap, points)
                }


                val croppedUri = withContext(Dispatchers.IO) {
                    ImageUtils.saveBitmapAndGetUri(this@CutoutActivity, cropped)
                }

                val intent = Intent(this@CutoutActivity, StickerEditActivity::class.java).apply {
                    putExtra(EXTRA_CROPPED_IMAGE_URI, croppedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@CutoutActivity, "Lỗi khi cắt ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                setButtonsEnabled(true)
            } finally {
                cutOutBinding.loadingContainer.visibility = View.GONE
            }
        }
    }
}