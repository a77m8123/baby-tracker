package com.babytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.data.BabyStore
import com.babytracker.logic.ageDays
import com.babytracker.logic.ageText
import com.babytracker.logic.localDateTime
import com.babytracker.logic.nowMs
import com.babytracker.model.NightMode
import com.babytracker.model.ThemeMode
import com.babytracker.ui.Baby
import com.babytracker.ui.BabyColors
import com.babytracker.ui.BabyTheme
import com.babytracker.ui.DarkColors
import com.babytracker.ui.DiaryScreen
import com.babytracker.ui.EventSheets
import com.babytracker.ui.Glyph
import com.babytracker.ui.HealthScreen
import com.babytracker.ui.HomeScreen
import com.babytracker.ui.Icon
import com.babytracker.ui.LightColors
import com.babytracker.ui.MoreScreen
import com.babytracker.ui.NightColors
import com.babytracker.ui.ProfileEditor
import com.babytracker.ui.Sheet
import com.babytracker.ui.SoundsScreen
import com.babytracker.ui.rememberNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val title: String, val glyph: Glyph) {
    Home("Сегодня", Glyph.Home),
    Diary("Дневник", Glyph.Diary),
    Sounds("Звуки", Glyph.Note),
    Health("Здоровье", Glyph.Heart),
    More("Ещё", Glyph.Gear),
}

/** Общая точка входа для Android, iOS и веб-версии. [fontFamily] задаёт только веб — там нет системных шрифтов. */
@Composable
fun App(
    store: BabyStore,
    platform: Platform,
    onThemeChange: (dark: Boolean) -> Unit = {},
    fontFamily: FontFamily? = null,
) {
    val colors = resolveColors(store)
    LaunchedEffect(colors.isDark) { onThemeChange(colors.isDark) }

    LaunchedEffect(Unit) { platform.requestNotifications() }

    // Напоминания и виджет — после паузы, чтобы не дёргать систему на каждое движение ползунка
    LaunchedEffect(store.data) {
        delay(400)
        platform.sync(store.data, nowMs())
    }

    BabyTheme(colors, fontFamily) {
        val c = Baby.colors
        var tab by rememberSaveable { mutableStateOf(Tab.Home) }
        var sheet by remember { mutableStateOf<Sheet?>(null) }
        var profile by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val toast: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

        Scaffold(
            containerColor = c.bg,
            topBar = { Header(store, tab) },
            bottomBar = { BottomBar(tab) { tab = it } },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        store,
                        open = { sheet = it },
                        openDiary = { tab = Tab.Diary },
                        openHealth = { tab = Tab.Health },
                        openProfile = { profile = true },
                    )
                    Tab.Diary -> DiaryScreen(store) { sheet = it }
                    Tab.Sounds -> SoundsScreen(store, platform)
                    Tab.Health -> HealthScreen(store) { profile = true }
                    Tab.More -> MoreScreen(store, platform, openProfile = { profile = true }, toast = toast)
                }
            }
        }

        sheet?.let { EventSheets(it, store) { next -> sheet = next } }
        if (profile) ProfileEditor(store) { profile = false }
        if (store.saveFailed) {
            LaunchedEffect(Unit) { snackbar.showSnackbar("Не удалось сохранить данные") }
        }
    }
}

@Composable
private fun resolveColors(store: BabyStore): BabyColors {
    val s = store.data.settings
    val systemDark = isSystemInDarkTheme()
    val now = rememberNow(60_000)
    val hour = localDateTime(now).hour
    val night = when (s.night) {
        NightMode.ON -> true
        NightMode.AUTO -> hour >= 21 || hour < 7
        NightMode.OFF -> false
    }
    return when {
        night -> NightColors
        s.theme == ThemeMode.DARK -> DarkColors
        s.theme == ThemeMode.LIGHT -> LightColors
        systemDark -> DarkColors
        else -> LightColors
    }
}

@Composable
private fun Header(store: BabyStore, tab: Tab) {
    val c = Baby.colors
    val s = store.data.settings
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (tab == Tab.Home) {
                Text(s.name, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (s.birthDate > 0) {
                    Text(ageText(ageDays(s.birthDate, nowMs())), color = c.muted, fontSize = 14.sp)
                }
            } else {
                Text(tab.title, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (c.isNight) {
            Icon(Glyph.Moon, c.muted, size = 22.dp)
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Baby.colors
    NavigationBar(containerColor = c.card, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.glyph, if (selected) c.sleep else c.muted, size = 24.dp) },
                label = { Text(tab.title, fontSize = 11.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = c.sleep,
                    indicatorColor = c.sleep.copy(alpha = 0.16f),
                    unselectedTextColor = c.muted,
                ),
            )
        }
    }
}
