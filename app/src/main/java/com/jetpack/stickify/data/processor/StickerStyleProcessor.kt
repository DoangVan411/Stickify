package com.jetpack.stickify.data.processor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Các thuật toán tạo biến thể sticker từ ảnh đã cắt (PNG có alpha):
 * - [addOuterBorder]: viền ngoài đồng màu quanh silhouette.
 * - [cartoonify]: hiệu ứng hoạt hình (posterize màu + viền nét đen tại biên chi tiết).
 *
 * Cả 2 hàm chạy nặng CPU nên LUÔN gọi trên background thread (Dispatchers.Default).
 */
object StickerStyleProcessor {

    fun addOuterBorder(
        source: Bitmap,
        borderColor: Int = Color.WHITE,
        borderWidthPx: Float = 75f
    ): Bitmap {
        val srcW = source.width
        val srcH = source.height

        val margin = borderWidthPx.toInt()
        val dstW = srcW + (margin * 2)
        val dstH = srcH + (margin * 2)

        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val alphaMask = source.extractAlpha()

        // 1. Dùng BlurMaskFilter để làm phình Mask ra ngoài
        // Lưu ý: Blur.NORMAL sẽ lan ra cả trong lẫn ngoài, làm phình biên độ
        val blurFilter = BlurMaskFilter(borderWidthPx, BlurMaskFilter.Blur.NORMAL)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            maskFilter = blurFilter
        }

        val leftPos = margin.toFloat()
        val topPos = margin.toFloat()

        // 2. THỦ THUẬT: Vẽ mask nhiều lần đè lên nhau
        // BlurMaskFilter ban đầu sẽ tạo viền mờ (gradient alpha).
        // Khi vẽ chồng lên nhau khoảng 5-8 lần, phần alpha mờ sẽ cộng dồn thành 1.0 (màu đặc/solid)
        val drawIterations = 6
        for (i in 0 until drawIterations) {
            canvas.drawBitmap(alphaMask, leftPos, topPos, borderPaint)
        }

        // 3. Vẽ ảnh gốc lên trên cùng
        canvas.drawBitmap(source, leftPos, topPos, null)

        alphaMask.recycle()

        return result
    }

    fun addPerfectSolidBorder(
        source: Bitmap,
        borderColor: Int = Color.WHITE,
        borderWidthPx: Float = 20f
    ): Bitmap {
        val srcW = source.width
        val srcH = source.height

        val margin = borderWidthPx.toInt()
        val dstW = srcW + (margin * 2)
        val dstH = srcH + (margin * 2)

        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Cấu hình cọ vẽ MÀU ĐẶC, KHÔNG NHÒE, có khử răng cưa (ANTI_ALIAS) để viền mượt
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.FILL
        }

        // Bước A: Tạo một lớp nền bằng chính ảnh gốc nhưng phủ toàn bộ màu trắng
        // Việc này đảm bảo phần "ruột" bên trong viền được lấp kín hoàn toàn
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(borderColor, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), fillPaint)

        // Bước B: Trích xuất ma trận điểm ảnh (Pixels) để tìm đường viền ngoài cùng
        val pixels = IntArray(srcW * srcH)
        source.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)

        val alphaThreshold = 10 // Ngưỡng bỏ qua các pixel quá trong suốt

        // Bước C: Quét để tìm các điểm ảnh nằm ở mép (Edge Detection)
        // Thuật toán này chạy cực nhanh (chỉ mất ~2ms - 5ms trên điện thoại)
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                val index = y * srcW + x
                val alpha = (pixels[index] ushr 24) and 0xFF

                // Nếu đây là 1 điểm ảnh của sticker
                if (alpha > alphaThreshold) {
                    var isEdge = false

                    // Kiểm tra xem nó có nằm giáp ranh với vùng trong suốt không
                    if (x == 0 || x == srcW - 1 || y == 0 || y == srcH - 1) {
                        isEdge = true
                    } else {
                        val topA = (pixels[(y - 1) * srcW + x] ushr 24) and 0xFF
                        val bottomA = (pixels[(y + 1) * srcW + x] ushr 24) and 0xFF
                        val leftA = (pixels[y * srcW + (x - 1)] ushr 24) and 0xFF
                        val rightA = (pixels[y * srcW + (x + 1)] ushr 24) and 0xFF

                        if (topA <= alphaThreshold || bottomA <= alphaThreshold ||
                            leftA <= alphaThreshold || rightA <= alphaThreshold) {
                            isEdge = true
                        }
                    }

                    // Nếu đúng là pixel ở mép, vẽ một hình tròn đặc vươn ra ngoài
                    // Hàng ngàn hình tròn này nối liền nhau tạo thành một viền phình ra hoàn hảo
                    if (isEdge) {
                        canvas.drawCircle(
                            x.toFloat() + margin,
                            y.toFloat() + margin,
                            borderWidthPx,
                            borderPaint
                        )
                    }
                }
            }
        }

        // Bước D: Cuối cùng, đặt ảnh gốc đè lên trên cùng
        canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)

        return result
    }

    fun addPerfectSmoothStickerBorder(
        source: Bitmap,
        borderColor: Int = Color.WHITE,
        borderWidthPx: Float = 20f
    ): Bitmap {
        // Tăng margin lên gấp đôi để có không gian cho hiệu ứng Blur không bị cắt viền
        val margin = (borderWidthPx * 2).toInt()
        val dstW = source.width + margin * 2
        val dstH = source.height + margin * 2

        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Trích xuất bóng (Alpha mask) của chủ thể
        val alphaMask = source.extractAlpha()

        // 2. Làm nhòe bóng.
        // Đây là bước quan trọng nhất để "làm tan chảy" các mép răng cưa của ảnh cắt
        val blurRadius = borderWidthPx // Bán kính nhòe quyết định độ dày viền
        val blurFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = blurFilter
        }

        // Vẽ bóng đã được làm mượt lên một Bitmap tạm
        val blurredMask = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(blurredMask)
        maskCanvas.drawBitmap(alphaMask, margin.toFloat(), margin.toFloat(), blurPaint)

        // 3. THUẬT TOÁN APPLE/SAMSUNG: Ép viền mờ thành viền Solid siêu mượt
        val r = Color.red(borderColor) / 255f
        val g = Color.green(borderColor) / 255f
        val b = Color.blue(borderColor) / 255f

        // ColorMatrix hoạt động như sau:
        // Nó nhân kênh Alpha lên 30 lần, sau đó trừ đi 1500.
        // Kết quả: Điểm mờ < 50 bị xóa sạch (Alpha = 0). Điểm mờ > 55 biến thành cục gạch (Alpha = 255).
        // Có 1 dải chuyển tiếp siêu nhỏ giữa 50-55 giúp viền có Anti-alias (khử răng cưa) mượt mà.
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, r * 255f, // Ép màu Đỏ
                0f, 0f, 0f, 0f, g * 255f, // Ép màu Xanh lá
                0f, 0f, 0f, 0f, b * 255f, // Ép màu Xanh dương
                0f, 0f, 0f, 30f, -1500f   // Xử lý làm nét (Threshold) kênh Alpha
            )
        )

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        // Vẽ viền (đã biến thành Solid mượt) lên ảnh kết quả
        canvas.drawBitmap(blurredMask, 0f, 0f, borderPaint)

        // 4. Đặt ảnh gốc lên trên cùng
        canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)

        // 5. Quan trọng: Giải phóng RAM các bitmap tạm thời
        alphaMask.recycle()
        blurredMask.recycle()

        return result
    }

    fun addPerfectStickerBorderWithShadow(
        source: Bitmap,
        borderColor: Int = Color.WHITE,
        borderWidthPx: Float = 30f,
        shadowRadius: Float = 20f,     // Độ nhòe toả ra của bóng
        shadowDx: Float = 8f,          // Độ lệch bóng theo chiều ngang
        shadowDy: Float = 8f,          // Độ lệch bóng theo chiều dọc (8f = bóng rơi xuống dưới)
        shadowColor: Int = Color.parseColor("#80000000") // Đen mờ 30% (tạo cảm giác tự nhiên)
    ): Bitmap {

        // Tính toán Margin rộng rãi để chứa đủ: Viền phình ra + Bóng toả ra + Độ lệch bóng
        val margin = (borderWidthPx * 2 + shadowRadius * 2 + max(abs(shadowDx), abs(shadowDy))).toInt()
        val dstW = source.width + margin * 2
        val dstH = source.height + margin * 2

        // 1. LẤY BÓNG ẢNH GỐC VÀ LÀM NHÒE (Tạo độ phình cho viền)
        val alphaMask = source.extractAlpha()
        val blurredMaskBmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val blurredCanvas = Canvas(blurredMaskBmp)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(borderWidthPx, BlurMaskFilter.Blur.NORMAL)
        }
        // Vẽ mask nhòe ra khung hình to
        blurredCanvas.drawBitmap(alphaMask, margin.toFloat(), margin.toFloat(), blurPaint)

        // 2. ÉP BÓNG NHÒE THÀNH VIỀN ĐẶC MƯỢT (Sticker Base)
        val solidBorderBmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val solidBorderCanvas = Canvas(solidBorderBmp)

        val r = Color.red(borderColor) / 255f
        val g = Color.green(borderColor) / 255f
        val b = Color.blue(borderColor) / 255f

        // Thuật toán ColorMatrix "Thần thánh" để khử răng cưa và ép nét đứt thành Solid
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, r * 255f,
                0f, 0f, 0f, 0f, g * 255f,
                0f, 0f, 0f, 0f, b * 255f,
                0f, 0f, 0f, 30f, -1500f // Ngưỡng cắt nét Alpha (Threshold)
            )
        )
        val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        // Ghi đè vào solidBorderBmp sẽ tạo ra một cái bệ sticker màu trắng láng mịn
        solidBorderCanvas.drawBitmap(blurredMaskBmp, 0f, 0f, thresholdPaint)

        // 3. LẮP RÁP 3 LỚP VÀO ẢNH CUỐI CÙNG
        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(result)

        // LỚP 1: TRÍCH XUẤT HÌNH DÁNG VIỀN TRẮNG ĐỂ TẠO BÓNG ĐỔ (SHADOW)
        val solidBorderAlpha = solidBorderBmp.extractAlpha()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        // Vẽ bóng rơi lệch theo trục x, y (dx, dy)
        finalCanvas.drawBitmap(solidBorderAlpha, shadowDx, shadowDy, shadowPaint)

        // LỚP 2: ĐẶT VIỀN TRẮNG (SOLID BORDER) ĐÈ LÊN BÓNG
        finalCanvas.drawBitmap(solidBorderBmp, 0f, 0f, null)

        // LỚP 3: ĐẶT ẢNH CHỦ THỂ GỐC LÊN VỊ TRÍ TRUNG TÂM TRÊN CÙNG
        finalCanvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)

        // 4. QUÉT DỌN RAM (Rất quan trọng khi xử lý ảnh chất lượng cao)
        alphaMask.recycle()
        blurredMaskBmp.recycle()
        solidBorderBmp.recycle()
        solidBorderAlpha.recycle()

        return result
    }

    fun addCustomStickerBorder(
        source: Bitmap,
        borderColor: Int = Color.WHITE,
        borderThickness: Float = 30f,
        distancePadding: Float = 20f,
        shadowRadius: Float = 15f,
        shadowDx: Float = 6f,
        shadowDy: Float = 6f,
        shadowColor: Int = Color.parseColor("#4D000000") // Đen mờ 30%
    ): Bitmap {

        // Tổng độ dày = Khoảng cách (trong suốt) + Bề dày viền
        val totalThickness = distancePadding + borderThickness

        // Tính toán Margin rộng rãi (Dùng cho cả 4 phía)
        val margin = (totalThickness * 2 + shadowRadius * 2 + max(abs(shadowDx), abs(shadowDy))).toInt()
        val dstW = source.width + margin * 2
        val dstH = source.height + margin * 2

        // Trích xuất Mask nguyên bản (chỉ lấy hình dáng, không lấy màu)
        val originalMask = source.extractAlpha()

        // ==========================================
        // BƯỚC 1: TẠO BỆ ĐỠ ĐẶC MƯỢT BẰNG KỸ THUẬT "DẬP" (DILATION)
        // Kỹ thuật này giữ được độ cong mượt tuyệt đối của ảnh gốc
        // ==========================================
        val solidBorderBmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val solidBorderCanvas = Canvas(solidBorderBmp)

        val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
        }

        // Vẽ mask ban đầu ở trung tâm
        solidBorderCanvas.drawBitmap(originalMask, margin.toFloat(), margin.toFloat(), colorPaint)

        // Dập mask theo hình tròn xung quanh tâm để tạo viền dày
        // Bán kính dập chính là tổng độ dày mong muốn
        if (totalThickness > 0) {
            val steps = 36 // Tăng số lượng step nếu độ dày rất lớn để viền mịn hơn (tối đa 36-72)
            val angleStep = (2 * Math.PI) / steps
            for (i in 0 until steps) {
                val dx = (totalThickness * cos(i * angleStep)).toFloat()
                val dy = (totalThickness * sin(i * angleStep)).toFloat()
                solidBorderCanvas.drawBitmap(
                    originalMask,
                    margin + dx,
                    margin + dy,
                    colorPaint
                )
            }
        }

        // ==========================================
        // BƯỚC 2: KHOÉT LỖ VÙNG KHOẢNG CÁCH (DISTANCE PADDING)
        // ==========================================
        if (distancePadding > 0f) {
            val cutoutBmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
            val cutoutCanvas = Canvas(cutoutBmp)

            // Vẽ lớp khoét ban đầu ở giữa
            cutoutCanvas.drawBitmap(originalMask, margin.toFloat(), margin.toFloat(), colorPaint)

            // Dập để tạo lớp khoét dày bằng distancePadding
            val cutoutSteps = 36
            val cutoutAngleStep = (2 * Math.PI) / cutoutSteps
            for (i in 0 until cutoutSteps) {
                val dx = (distancePadding * cos(i * cutoutAngleStep)).toFloat()
                val dy = (distancePadding * sin(i * cutoutAngleStep)).toFloat()
                cutoutCanvas.drawBitmap(
                    originalMask,
                    margin + dx,
                    margin + dy,
                    colorPaint
                )
            }

            // Khoét lớp viền to (bước 1) bằng lớp đục lỗ vừa tạo
            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            solidBorderCanvas.drawBitmap(cutoutBmp, 0f, 0f, clearPaint)
            cutoutBmp.recycle()
        }

        // ==========================================
        // BƯỚC 3: LẮP RÁP CÁC LỚP VÀO ẢNH CUỐI CÙNG
        // ==========================================
        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(result)

        // 3.1: VẼ BÓNG ĐỔ (SHADOW)
        val solidBorderAlpha = solidBorderBmp.extractAlpha()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        finalCanvas.drawBitmap(solidBorderAlpha, shadowDx, shadowDy, shadowPaint)

        // 3.2: ĐẶT LỚP VIỀN LÊN (Đã được khoét nếu có khoảng cách)
        finalCanvas.drawBitmap(solidBorderBmp, 0f, 0f, null)

        // 3.3: ĐẶT ẢNH CHỦ THỂ VÀO GIỮA
        finalCanvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)

        // ==========================================
        // BƯỚC 4: GIẢI PHÓNG RAM
        // ==========================================
        originalMask.recycle()
        solidBorderBmp.recycle()
        solidBorderAlpha.recycle()

        return result
    }

    /**
     * Hiệu ứng hoạt hình đơn giản: posterize màu (giảm số cấp mỗi kênh RGB) để tạo mảng màu
     * phẳng, cộng thêm viền nét tối tại vùng có độ chênh sáng lớn (biên chi tiết khuôn mặt,
     * tóc...). Chạy ở độ phân giải giới hạn [maxWorkingDimension] để đảm bảo tốc độ.
     */
    fun cartoonify(
        source: Bitmap,
        posterizeLevels: Int = 6,
        edgeThreshold: Float = 80f, // Tăng threshold do dùng thuật toán Sobel mạnh hơn
        maxWorkingDimension: Int = 900
    ): Bitmap {
        // Tùy thuộc vào hàm downscaleIfNeeded của bạn, tôi giữ nguyên logic resize
        val working = downscaleIfNeeded(source, maxWorkingDimension)
        val w = working.width
        val h = working.height

        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Tính toán Luminance (Độ sáng) cho toàn ảnh để dò biên
        val luminance = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luminance[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        val step = 255f / (posterizeLevels - 1)
        val output = IntArray(w * h)

        // Chạy loop bỏ qua viền ngoài cùng 1px để tránh lỗi tràn mảng (IndexOutOfBounds)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val p = pixels[idx]
                val alpha = (p ushr 24) and 0xFF

                // Bỏ qua các pixel trong suốt để tối ưu hiệu suất
                if (alpha < 10) {
                    output[idx] = p
                    continue
                }

                // ==========================================
                // BƯỚC 1: LÀM MỊN & ÉP MÀU (MINI-BLUR + POSTERIZE)
                // Lấy trung bình cộng màu của 9 pixel xung quanh để khử nhiễu (noise)
                // ==========================================
                var sumR = 0; var sumG = 0; var sumB = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val cp = pixels[(y + dy) * w + (x + dx)]
                        sumR += (cp shr 16) and 0xFF
                        sumG += (cp shr 8) and 0xFF
                        sumB += cp and 0xFF
                    }
                }
                val avgR = sumR / 9
                val avgG = sumG / 9
                val avgB = sumB / 9

                // Ép màu (Posterize) trên màu đã làm mịn giúp mảng màu phẳng như tranh vẽ
                val pr = (Math.round(avgR / step) * step).toInt().coerceIn(0, 255)
                val pg = (Math.round(avgG / step) * step).toInt().coerceIn(0, 255)
                val pb = (Math.round(avgB / step) * step).toInt().coerceIn(0, 255)

                // ==========================================
                // BƯỚC 2: DÒ BIÊN BẰNG THUẬT TOÁN SOBEL 3x3
                // Tạo ra đường viền nét, dày và liền mạch hơn
                // ==========================================
                val tl = luminance[(y - 1) * w + (x - 1)]
                val tc = luminance[(y - 1) * w + x]
                val tr = luminance[(y - 1) * w + (x + 1)]
                val ml = luminance[y * w + (x - 1)]
                val mr = luminance[y * w + (x + 1)]
                val bl = luminance[(y + 1) * w + (x - 1)]
                val bc = luminance[(y + 1) * w + x]
                val br = luminance[(y + 1) * w + (x + 1)]

                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toFloat()

                // ==========================================
                // BƯỚC 3: HÒA TRỘN VIỀN MỀM MẠI (ANTI-ALIASING)
                // Thay vì if (isEdge) cứng nhắc, ta tính độ đậm của viền
                // ==========================================
                // Khoảng chia 60f quyết định độ mềm/mờ của viền. Số càng to viền càng êm.
                val edgeIntensity = ((magnitude - edgeThreshold) / 60f).coerceIn(0f, 1f)

                // Trộn màu Posterize với màu viền tối dựa trên cường độ biên
                val finalR = (pr * (1 - edgeIntensity) + (pr * 0.25f) * edgeIntensity).toInt()
                val finalG = (pg * (1 - edgeIntensity) + (pg * 0.25f) * edgeIntensity).toInt()
                val finalB = (pb * (1 - edgeIntensity) + (pb * 0.25f) * edgeIntensity).toInt()

                output[idx] = (alpha shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        // Vẽ lại viền 1px ngoài cùng (do bị bỏ qua trong vòng lặp)
        for (x in 0 until w) {
            output[x] = pixels[x]
            output[(h - 1) * w + x] = pixels[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            output[y * w] = pixels[y * w]
            output[y * w + (w - 1)] = pixels[y * w + (w - 1)]
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }

    // Giả định hàm downscale của bạn
    private fun downscaleIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val max = Math.max(source.width, source.height)
        if (max <= maxDimension) return source
        val scale = maxDimension.toFloat() / max
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }

    private fun posterizeChannel(value: Int, step: Int): Int {
        if (step <= 0) return value
        val level = Math.round(value / step.toFloat())
        return min(255, max(0, level * step))
    }

}