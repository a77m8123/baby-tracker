package com.babytracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babytracker.alarms.AlarmKind
import com.babytracker.alarms.Alarms
import com.babytracker.notifications.Notifications
import com.babytracker.sound.SoundService
import com.babytracker.sound.SoundType
import com.babytracker.ui.BabyTheme
import com.babytracker.ui.HomeScreen
import com.babytracker.ui.MainViewModel
import com.babytracker.ui.SettingsScreen
import com.babytracker.ui.SoundsScreen
import com.babytracker.ui.StatsScreen
import com.babytracker.ui.StatsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не критичен */ }

    private val exactAlarmPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* перезапланируем при возврате */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            BabyTheme {
                AppRoot(
                    onRequestExactAlarms = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(android.net.Uri.parse("package:$packageName"))
                            exactAlarmPermission.launch(intent)
                        }
                    },
                )
            }
        }
    }
}

private enum class Screen { HOME, SOUNDS, STATS, SETTINGS }

@Composable
private fun AppRoot(onRequestExactAlarms: () -> Unit) {
    val vm: MainViewModel = viewModel()
    val statsVm: StatsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val statsState by statsVm.state.collectAsState()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var currentSound by remember { mutableStateOf<SoundType?>(null) }
    var soundTimer by remember { mutableStateOf(30) }

    val ctx = androidx.compose.ui.platform.LocalContext.current

    val settings = state.settings

    // планируем будильники, когда включены напоминания
    LaunchedEffect(state.lastFeedAt, settings.feedIntervalMinutes, settings.notificationsEnabled, state.isSleeping, state.sleepStartedAt) {
        if (!settings.notificationsEnabled) {
            Alarms.cancel(ctx, AlarmKind.FEED, FEED_ID)
            Alarms.cancel(ctx, AlarmKind.WAKE, WAKE_ID)
            return@LaunchedEffect
        }
        state.lastFeedAt?.let {
            val next = it + settings.feedIntervalMinutes * 60_000L
            if (next > System.currentTimeMillis()) {
                Alarms.schedule(ctx, AlarmKind.FEED, FEED_ID, next)
            }
        }
        state.sleepStartedAt?.let {
            val cycle = com.babytracker.data.SleepNorm.cycleMinutes(settings) * 60_000L
            Alarms.schedule(ctx, AlarmKind.WAKE, WAKE_ID, it + cycle)
        }
    }

    // подтягиваем актуальный таймер звука из настроек
    LaunchedEffect(settings.soundTimerMinutes) {
        if (soundTimer == 30 && settings.soundTimerMinutes != 0) soundTimer = settings.soundTimerMinutes
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            state = state,
            onFeed = { type, amount ->
                vm.logFeeding(type, amount)
                currentSound = null
            },
            onDiaper = { withStool -> vm.logDiaper(withStool) },
            onStartSleep = { vm.startSleep() },
            onWakeUp = { vm.wakeUp() },
            onOpenSounds = { screen = Screen.SOUNDS },
            onOpenStats = { statsVm.load(); screen = Screen.STATS },
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.SOUNDS -> SoundsScreen(
            currentSound = currentSound,
            timerMinutes = soundTimer,
            onPlay = { type, minutes ->
                currentSound = type
                soundTimer = minutes
                SoundService.play(ctx, type, minutes)
            },
            onStop = {
                currentSound = null
                SoundService.stop(ctx)
            },
            onBack = { screen = Screen.HOME },
        )

        Screen.STATS -> StatsScreen(state = statsState, onBack = { screen = Screen.HOME })

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            canScheduleExact = Alarms.canScheduleExact(ctx),
            onName = { vm.setName(it) },
            onBirthDate = { vm.setBirthDate(it) },
            onSleepGoal = { vm.setSleepGoal(it) },
            onFeedInterval = { vm.setFeedInterval(it) },
            onSleepCycle = { vm.setSleepCycle(it) },
            onDefaultBottle = { vm.setDefaultBottleMl(it) },
            onSoundTimer = { vm.setSoundTimer(it); soundTimer = it },
            onNotifications = { vm.setNotificationsEnabled(it) },
            onBack = { screen = Screen.HOME },
        )
    }
}

private const val FEED_ID = 1
private const val WAKE_ID = 2
