package com.jetpack.stickify

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class cho Hilt DI.
 * Bắt buộc phải có annotation @HiltAndroidApp.
 */
@HiltAndroidApp
class StickifyApplication : Application()
