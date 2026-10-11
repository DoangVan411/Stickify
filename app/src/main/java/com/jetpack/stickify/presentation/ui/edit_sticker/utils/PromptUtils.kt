package com.jetpack.stickify.presentation.ui.edit_sticker.utils

import androidx.fragment.app.FragmentManager
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object PromptUtils {

    /**
     * Enum định nghĩa kết quả validate prompt
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val title: String, val message: String) : ValidationResult()
    }

    /**
     * Hàm kiểm tra prompt người dùng nhập vào
     */
    fun validatePrompt(prompt: String?): ValidationResult {
        val safePrompt = prompt?.trim() ?: ""

        if (safePrompt.isEmpty()) {
            return ValidationResult.Invalid("Thiếu thông tin", "Vui lòng nhập mô tả cho sticker của bạn.")
        }

        // Kiểm tra độ dài (Ví dụ: phải lớn hơn 3 ký tự)
        if (safePrompt.length < 15) {
            return ValidationResult.Invalid("Quá ngắn", "Mô tả của bạn quá ngắn. Vui lòng nhập ít nhất 5 từ.")
        }

        // Kiểm tra quá dài (Tuỳ chọn)
        if (safePrompt.length > 200) {
            return ValidationResult.Invalid("Quá dài", "Mô tả chỉ được phép tối đa 200 ký tự.")
        }

        // Kiểm tra ký tự đặc biệt không hợp lệ (Chỉ cho phép chữ cái, số, dấu câu cơ bản tiếng Việt/Anh)
        // Nếu chuỗi chứa các ký tự như <, >, {, }, \, ^, ~
        val specialCharRegex = Regex("[<>{}\\\\^~|#]")
        if (specialCharRegex.containsMatchIn(safePrompt)) {
            return ValidationResult.Invalid("Ký tự không hợp lệ", "Mô tả không được chứa các ký tự đặc biệt lạ (ví dụ: < > { } ^ ~ \\).")
        }

        return ValidationResult.Valid
    }

    /**
     * Xử lý Exception chung từ Server, Network, hoặc Hệ thống
     */
    fun handleException(fragmentManager: FragmentManager, exception: Throwable) {
        val title: String
        val message: String

        when (exception) {
            is UnknownHostException, is ConnectException -> {
                title = "Lỗi mạng"
                message = "Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại Internet."
            }
            is SocketTimeoutException -> {
                title = "Hết thời gian kết nối"
                message = "Máy chủ phản hồi quá chậm, vui lòng thử lại sau."
            }
            is IllegalStateException -> {
                // Ví dụ xử lý lỗi backstack hoặc state không hợp lệ
                title = "Lỗi trạng thái"
                message = "Hệ thống đang bận hoặc trạng thái không hợp lệ, vui lòng thử lại."
            }
            // Thêm các custom Server Exception (Ví dụ: ApiException) nếu dự án bạn có định nghĩa riêng
            else -> {
                title = "Đã xảy ra lỗi"
                message = exception.message ?: "Lỗi hệ thống không xác định. Vui lòng thử lại."
            }
        }

        showErrorDialog(fragmentManager, title, message)
    }

    /**
     * Hiển thị Dialog an toàn (Tránh crash IllegalStateException: Can not perform this action after onSaveInstanceState)
     */
    fun showErrorDialog(fragmentManager: FragmentManager, title: String, message: String) {
        if (fragmentManager.isStateSaved || fragmentManager.isDestroyed) {
            return // Chặn crash backstack khi Activity đã bị destroy hoặc stop
        }

        // Tránh hiển thị trùng lặp
        val existingDialog = fragmentManager.findFragmentByTag(PromptErrorDialogFragment.TAG)
        if (existingDialog == null) {
            val dialog = PromptErrorDialogFragment.newInstance(title, message)
            dialog.show(fragmentManager, PromptErrorDialogFragment.TAG)
        }
    }
}