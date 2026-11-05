package com.babytracker.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.babytracker.App

class MainActivity : ComponentActivity() {

    private var pendingPick: ((String?) -> Unit)? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingPick
        pendingPick = null
        val text = uri?.let { runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes().decodeToString() } }.getOrNull() }
        callback?.invoke(text)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = AppGraph.store(this)
        val platform = AppGraph.platform(this)
        platform.activity = this
        setContent {
            App(store, platform, onThemeChange = ::applySystemBars)
        }
    }

    override fun onResume() {
        super.onResume()
        AppGraph.platform(this).activity = this
        AppGraph.store(this).reload()
    }

    override fun onDestroy() {
        val platform = AppGraph.platform(this)
        if (platform.activity === this) platform.activity = null
        super.onDestroy()
    }

    fun pickTextFile(onResult: (String?) -> Unit) {
        pendingPick = onResult
        openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Иконки статус-бара должны быть видны и на тёмной, и на светлой теме приложения
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
