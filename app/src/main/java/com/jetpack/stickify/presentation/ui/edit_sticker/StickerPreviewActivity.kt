package com.jetpack.stickify.presentation.ui.edit_sticker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.jetpack.stickify.R
import com.jetpack.stickify.presentation.ui.collection.favourite.SelectPacksDialogFragment
import com.jetpack.stickify.presentation.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StickerPreviewActivity : AppCompatActivity() {

    private val viewModel: StickerPreviewViewModel by viewModels()

    private lateinit var ivPreviewSticker: ImageView
    private lateinit var cardFavorite: MaterialCardView
    private lateinit var ivFavoriteIcon: ImageView
    private lateinit var tvFavoriteLabel: TextView
    private lateinit var cardAddToKeyboard: MaterialCardView
    private lateinit var cardAddToPack: MaterialCardView
    private lateinit var btnDone: MaterialButton

    private var stickerUri: Uri? = null
    private var projectId: String? = null
    private var isAnimated: Boolean = false
    private var isBookmarked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_preview)

        val uriExtra: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_STICKER_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_STICKER_URI)
        }
        val uriString = intent.getStringExtra(EXTRA_STICKER_URI)
        stickerUri = uriExtra ?: uriString?.let { Uri.parse(it) } ?: intent.data
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        isAnimated = intent.getBooleanExtra(EXTRA_IS_ANIMATED, false)
        isBookmarked = intent.getBooleanExtra(EXTRA_IS_BOOKMARKED, false)

        initViews()
        setupListeners()
        updateFavoriteUI()

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
                .setDuration(1000)
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
                            animateFlyToTarget(bottomSheet, targetLoc[0].toFloat(), targetLoc[1].toFloat(), targetW, targetH) {
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

    private fun animateFlyToTarget(
        bottomSheet: KeyboardStickersBottomSheetDialogFragment,
        endX: Float,
        endY: Float,
        targetW: Int,
        targetH: Int,
        onComplete: () -> Unit
    ) {
        val container = (bottomSheet.dialog?.window?.decorView as? ViewGroup)
            ?: (window.decorView as? ViewGroup)
            ?: findViewById<ViewGroup>(android.R.id.content)
            ?: run {
                onComplete()
                return
            }

        val containerLoc = IntArray(2)
        container.getLocationOnScreen(containerLoc)

        val startLoc = IntArray(2)
        ivPreviewSticker.getLocationOnScreen(startLoc)

        val localStartX = (startLoc[0] - containerLoc[0]).toFloat()
        val localStartY = (startLoc[1] - containerLoc[1]).toFloat()
        val startW = ivPreviewSticker.width
        val startH = ivPreviewSticker.height

        if (startW <= 0 || startH <= 0) {
            onComplete()
            return
        }

        val localTargetX = endX - containerLoc[0]
        val localTargetY = endY - containerLoc[1]

        // Tọa độ x, y đích để tâm của animView khớp chính xác với tâm của ô mục tiêu
        val finalX = localTargetX + (targetW - startW) / 2f
        val finalY = localTargetY + (targetH - startH) / 2f

        val scaleX = targetW.toFloat() / startW
        val scaleY = targetH.toFloat() / startH
        val scale = minOf(scaleX, scaleY)

        val animView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(startW, startH)
            x = localStartX
            y = localStartY
            pivotX = startW / 2f
            pivotY = startH / 2f
            elevation = 9999f
            translationZ = 9999f
            scaleType = ImageView.ScaleType.FIT_CENTER
            stickerUri?.let { uri ->
                Glide.with(this@StickerPreviewActivity)
                    .load(uri)
                    .into(this)
            }
        }
        container.addView(animView)
        animView.bringToFront()

        animView.animate()
            .x(finalX)
            .y(finalY)
            .scaleX(scale)
            .scaleY(scale)
            .alpha(1.0f)
            .setDuration(1200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                container.removeView(animView)
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
        const val EXTRA_IS_BOOKMARKED = "extra_is_bookmarked"
    }
}
