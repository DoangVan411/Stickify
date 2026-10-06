package com.jetpack.stickify.presentation.ui.edit_sticker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.jetpack.stickify.R
import com.jetpack.stickify.presentation.ui.collection.favourite.SelectPacksDialogFragment
import com.jetpack.stickify.presentation.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StickerPreviewActivity : AppCompatActivity() {

    private val viewModel: StickerPreviewViewModel by viewModels()

    private lateinit var ivPreviewSticker: ImageView
    private lateinit var cardFavorite: LinearLayout
    private lateinit var ivFavoriteIcon: ImageView
    private lateinit var tvFavoriteLabel: TextView
    private lateinit var cardAddToKeyboard: LinearLayout
    private lateinit var cardAddToPack: LinearLayout
    private lateinit var btnDone: MaterialButton

    private var stickerUri: Uri? = null
    private var projectId: String? = null
    private var isAnimated: Boolean = false
    private var isBookmarked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_preview)

        stickerUri = intent.getParcelableExtra(EXTRA_STICKER_URI)
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        isAnimated = intent.getBooleanExtra(EXTRA_IS_ANIMATED, false)

        initViews()
        setupListeners()

        // Entry animation for preview sticker
        ivPreviewSticker.alpha = 0f
        ivPreviewSticker.scaleX = 0.3f
        ivPreviewSticker.scaleY = 0.3f
        ivPreviewSticker.translationY = 150f

        stickerUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.ic_sticker_cat)
                .into(ivPreviewSticker)

            ivPreviewSticker.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(1500)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }

        setupDialogListener()
    }

    private fun initViews() {
        ivPreviewSticker = findViewById(R.id.ivPreviewSticker)
        cardFavorite = findViewById(R.id.cardFavorite)
        ivFavoriteIcon = findViewById(R.id.ivFavoriteIcon)
        tvFavoriteLabel = findViewById(R.id.tvFavoriteLabel)
        cardAddToKeyboard = findViewById(R.id.cardAddToKeyboard)
        cardAddToPack = findViewById(R.id.cardAddToPack)
        btnDone = findViewById(R.id.btnDone)
    }

    private fun setupListeners() {

        cardFavorite.setOnClickListener {
            val id = projectId ?: return@setOnClickListener
            isBookmarked = !isBookmarked
            updateFavoriteUI()
            viewModel.toggleBookmark(id, isBookmarked) { success ->
                if (success) {
                    val msg = if (isBookmarked) "Đã thêm vào yêu thích" else "Đã xóa khỏi yêu thích"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        cardAddToKeyboard.setOnClickListener {
            val uriStr = stickerUri?.toString() ?: return@setOnClickListener
            viewModel.addToKeyboard(uriStr, isAnimated, projectId) { success ->
                if (success) {
                    val bottomSheet = KeyboardStickersBottomSheetDialogFragment.newInstance()
                    bottomSheet.onShownListener = {
                        bottomSheet.getTopLeftItemScreenLocation { targetLoc, targetW, targetH ->
                            animateFlyToTarget(targetLoc[0].toFloat(), targetLoc[1].toFloat(), targetW, targetH) {
                                bottomSheet.setFlyingCompleted()
                            }
                        }
                    }
                    bottomSheet.show(supportFragmentManager, KeyboardStickersBottomSheetDialogFragment.TAG)
                } else {
                    Toast.makeText(this, "Lỗi khi thêm vào bàn phím", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cardAddToPack.setOnClickListener {
            val stickerId = projectId
            if (stickerId.isNullOrEmpty()) {
                Toast.makeText(this, "Không tìm thấy thông tin sticker", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dialog = SelectPacksDialogFragment.newInstance()
            dialog.show(supportFragmentManager, SelectPacksDialogFragment.TAG)
        }

        btnDone.setOnClickListener {
            navigateToHome()
        }
    }

    private fun animateFlyToTarget(endX: Float, endY: Float, targetW: Int, targetH: Int, onComplete: () -> Unit) {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content) ?: run {
            onComplete()
            return
        }

        val startLoc = IntArray(2)
        ivPreviewSticker.getLocationOnScreen(startLoc)
        val startX = startLoc[0].toFloat()
        val startY = startLoc[1].toFloat()
        val startW = ivPreviewSticker.width
        val startH = ivPreviewSticker.height

        if (startW <= 0 || startH <= 0) {
            onComplete()
            return
        }

        val animView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(startW, startH)
            x = startX
            y = startY
            scaleType = ImageView.ScaleType.FIT_CENTER
            stickerUri?.let { uri ->
                Glide.with(this@StickerPreviewActivity)
                    .load(uri)
                    .into(this)
            }
        }
        rootLayout.addView(animView)

        val targetScaleX = if (startW > 0) targetW.toFloat() / startW else 0.35f
        val targetScaleY = if (startH > 0) targetH.toFloat() / startH else 0.35f

        animView.animate()
            .x(endX)
            .y(endY)
            .scaleX(targetScaleX)
            .scaleY(targetScaleY)
            .alpha(0.85f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                rootLayout.removeView(animView)
                onComplete()
            }
            .start()
    }

    private fun setupDialogListener() {
        supportFragmentManager.setFragmentResultListener(
            SelectPacksDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val packIds = bundle.getStringArrayList(SelectPacksDialogFragment.EXTRA_SELECTED_PACK_IDS) ?: emptyList()
            val stickerId = projectId
            if (!stickerId.isNullOrEmpty() && packIds.isNotEmpty()) {
                for (packId in packIds) {
                    viewModel.addStickerToPack(packId, stickerId) { _ -> }
                }
                Toast.makeText(this, "Đã thêm vào bộ sticker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFavoriteUI() {
        if (isBookmarked) {
            ivFavoriteIcon.setImageResource(R.drawable.ic_bookmark)
            tvFavoriteLabel.text = "Đã yêu thích"
        } else {
            ivFavoriteIcon.setImageResource(R.drawable.ic_bookmark)
            tvFavoriteLabel.text = "Yêu thích"
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_STICKER_URI = "extra_sticker_uri"
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_IS_ANIMATED = "extra_is_animated"
    }
}
