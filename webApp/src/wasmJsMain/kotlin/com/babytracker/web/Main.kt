package com.babytracker.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeViewport
import com.babytracker.App
import com.babytracker.data.BabyStore
import com.babytracker.web.resources.Res
import com.babytracker.web.resources.inter_400regular
import com.babytracker.web.resources.inter_500medium
import com.babytracker.web.resources.inter_600semibold
import com.babytracker.web.resources.inter_700bold
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@JsFun("() => { const l = document.getElementById('loading'); if (l) l.remove(); }")
private external fun hideLoading()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val store = BabyStore(LocalStorage())
    val platform = WebPlatform()
    ComposeViewport("app") {
        val fonts = rememberInter()
        if (fonts == null) {
            // пока грузится шрифт с кириллицей, вместо текста были бы квадратики
            Box(Modifier.fillMaxSize().background(Color(0xFFF7F3EE)))
        } else {
            LaunchedEffect(Unit) { hideLoading() }
            App(store, platform, fontFamily = fonts)
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberInter(): FontFamily? {
    val regular by preloadFont(Res.font.inter_400regular, FontWeight.Normal)
    val medium by preloadFont(Res.font.inter_500medium, FontWeight.Medium)
    val semibold by preloadFont(Res.font.inter_600semibold, FontWeight.SemiBold)
    val bold by preloadFont(Res.font.inter_700bold, FontWeight.Bold)
    val all = listOf(regular, medium, semibold, bold)
    if (all.any { it == null }) return null
    return remember(all) { FontFamily(all.filterNotNull()) }
}
