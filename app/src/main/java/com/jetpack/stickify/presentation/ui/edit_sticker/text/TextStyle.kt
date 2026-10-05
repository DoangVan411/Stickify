package com.jetpack.stickify.presentation.ui.edit_sticker.text

/** Font chữ có thể chọn trong màn hình thêm chữ. */
enum class TextFont(val key: String) {
    DEFAULT("default"),
    TYPEWRITER("typewriter"),
    BOLD("bold");

    companion object {
        fun fromKey(key: String?): TextFont = values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Kiểu nền của chữ (giống story Facebook):
 *  - NONE: chỉ có chữ, màu chữ = màu đã chọn.
 *  - FILL: nền bo góc = màu đã chọn, chữ tự đổi sang trắng/đen để dễ đọc.
 *  - OUTLINE: chữ + khung viền bo góc, cả hai cùng màu đã chọn.
 */
enum class TextBackgroundMode(val key: String) {
    NONE("none"),
    FILL("fill"),
    OUTLINE("outline");

    /** Xoay vòng: NONE -> FILL -> OUTLINE -> NONE. */
    fun next(): TextBackgroundMode = values()[(ordinal + 1) % values().size]

    companion object {
        fun fromKey(key: String?): TextBackgroundMode =
            values().firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * Kiểu chữ (font + nền). Được mã hóa vào trường `TextLayer.fontId` ("bold|fill") nên được lưu trong
 * project, hoàn tác/làm lại được mà không cần đổi model hay serializer.
 * Nếu sau này TextLayer có trường riêng cho nền chữ thì chỉ cần sửa encode/decode ở đây.
 */
data class TextStyleSpec(
    val font: TextFont = TextFont.DEFAULT,
    val background: TextBackgroundMode = TextBackgroundMode.NONE
) {
    fun encode(): String = "${font.key}|${background.key}"

    companion object {
        val DEFAULT = TextStyleSpec()

        /** Chấp nhận cả giá trị cũ không có dấu '|' (vd "default") -> nền NONE. */
        fun decode(fontId: String?): TextStyleSpec {
            if (fontId.isNullOrBlank()) return DEFAULT
            val parts = fontId.split('|')
            return TextStyleSpec(
                font = TextFont.fromKey(parts.getOrNull(0)),
                background = TextBackgroundMode.fromKey(parts.getOrNull(1))
            )
        }
    }
}