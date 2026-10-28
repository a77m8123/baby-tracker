package com.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.data.FeedType
import com.babytracker.util.TimeFmt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: MainUiState,
    onFeed: (FeedType, Int?) -> Unit,
    onDiaper: (Boolean) -> Unit,
    onStartSleep: () -> Unit,
    onWakeUp: () -> Unit,
    onOpenSounds: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // тик раз в секунду для живой секундомерной строки
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val currentSleepMinutes = state.sleepStartedAt?.let { ((now - it) / 60_000L).toInt() } ?: 0
    val lastFeed = state.lastFeedAt
    val sinceFeedMinutes = lastFeed?.let { ((now - it) / 60_000L).toInt() } ?: 0
    val untilFeedMinutes = (state.feedIntervalMinutes - sinceFeedMinutes).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        state.settings.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        TimeFmt.ageText(state.settings.ageDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenStats) { Text("Неделя") }
                    OutlinedButton(onClick = onOpenSettings) { Text("Настройки") }
                }
            }
        }

        // ---------- Сон ----------
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isSleeping)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (state.isSleeping) "Спит" else "Не спит",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    if (state.isSleeping) {
                        BigStat(
                            value = TimeFmt.duration(currentSleepMinutes),
                            label = "текущий сон",
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Проснётся примерно в ${TimeFmt.clock(now + state.wakeUpInMinutes * 60_000L)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        BigStat(
                            value = TimeFmt.duration(state.todaySleepMinutes),
                            label = "сон за сегодня",
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Цель на сегодня: ${TimeFmt.duration(state.sleepGoalMinutes)} · " +
                            "осталось ${TimeFmt.duration((state.sleepGoalMinutes - state.todaySleepMinutes).coerceAtLeast(0))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    if (state.isSleeping) {
                        Button(
                            onClick = onWakeUp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Проснулся", fontSize = 17.sp)
                        }
                    } else {
                        Button(
                            onClick = onStartSleep,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Filled.Bedtime, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Уснул", fontSize = 17.sp)
                        }
                    }
                }
            }
        }

        // ---------- Еда ----------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ChildCare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Кормление", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(14.dp))

                    BigStat(
                        value = if (state.feedsToday == 0) "—"
                        else "через ${TimeFmt.duration(untilFeedMinutes)}",
                        label = when {
                            state.feedsToday == 0 -> "сегодня ещё не кормили"
                            untilFeedMinutes == 0 -> "пора кормить"
                            else -> "следующее кормление"
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Сегодня: ${state.feedsToday} раз · интервал ${TimeFmt.duration(state.feedIntervalMinutes)}" +
                            if (state.feedAmountTodayMl > 0) " · ${state.feedAmountTodayMl} мл" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeedButton(
                            "Грудь", Icons.Filled.ChildCare, Modifier.weight(1f)
                        ) { onFeed(FeedType.BREAST, null) }
                        FeedButton(
                            "Бутылочка", Icons.Filled.LocalDrink, Modifier.weight(1f)
                        ) { onFeed(FeedType.BOTTLE, state.settings.defaultBottleMl) }
                        FeedButton(
                            "Прикорм", Icons.Filled.Restaurant, Modifier.weight(1f)
                        ) { onFeed(FeedType.SOLIDS, null) }
                    }
                }
            }
        }

        // ---------- Подгузник + звуки ----------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onDiaper(false) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.BabyChangingStation, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Подгузник")
                }
                Button(
                    onClick = onOpenSounds,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Звуки")
                }
            }
        }

        // ---------- История ----------
        item {
            Text(
                "История",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (state.events.isEmpty()) {
            item {
                Text(
                    "Пока пусто. Отмечай сон и кормления — здесь появится лента.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.events) { ev ->
            EventRow(ev)
        }
    }
}

@Composable
private fun BigStat(value: String, label: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EventRow(ev: FeedEvent) {
    val (icon, tint, title, subtitle) = when (ev) {
        is FeedEvent.Sleep -> {
            val s = ev.entity
            val dur = if (s.end != null) TimeFmt.duration(((s.end - s.start) / 60_000L).toInt()) + " сна"
            else "спит сейчас"
            Quad(Icons.Filled.Bedtime, MaterialTheme.colorScheme.primary,
                "Сон ${TimeFmt.clock(s.start)}", dur)
        }
        is FeedEvent.Feed -> {
            val f = ev.entity
            val name = when (f.type) {
                FeedType.BREAST -> "Грудь"
                FeedType.BOTTLE -> "Бутылочка"
                FeedType.SOLIDS -> "Прикорм"
            }
            val extra = f.amountMl?.let { " · $it мл" } ?: ""
            Quad(Icons.Filled.ChildCare, MaterialTheme.colorScheme.secondary,
                "$name ${TimeFmt.clock(f.timestamp)}", TimeFmt.relative(f.timestamp) + extra)
        }
        is FeedEvent.Diaper -> Quad(
            Icons.Filled.BabyChangingStation, MaterialTheme.colorScheme.tertiary,
            "Подгузник ${TimeFmt.clock(ev.entity.timestamp)}",
            if (ev.entity.withStool) "со стулом" else TimeFmt.relative(ev.entity.timestamp),
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Quad(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
)
