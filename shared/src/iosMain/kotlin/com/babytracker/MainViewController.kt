package com.babytracker

import androidx.compose.ui.window.ComposeUIViewController
import com.babytracker.data.BabyStore
import com.babytracker.data.UserDefaultsStorage
import com.babytracker.platform.IosBridge
import com.babytracker.platform.IosPlatform
import platform.UIKit.UIViewController

/** Вызывается из Swift: MainViewControllerKt.MainViewController(bridge: AppBridge()) */
fun MainViewController(bridge: IosBridge): UIViewController {
    val store = BabyStore(UserDefaultsStorage())
    val platform = IosPlatform(bridge)
    return ComposeUIViewController { App(store, platform) }
}
