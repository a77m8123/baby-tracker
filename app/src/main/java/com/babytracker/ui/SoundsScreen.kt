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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.sound.SoundType

@Composable
fun SoundsScreen(
    currentSound: SoundType?,
    timerMinutes: Int,
    onPlay: (SoundType, Int) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val timers = listOf(0, 15, 30, 60)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Звуки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "назад",
                    Modifier.clickable(onClick = onBack).padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Text(
                "Выключится через",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                timers.forEach { t ->
                    FilterChip(
                        selected = timerMinutes == t,
                        onClick = { currentSound?.let { onPlay(it, t) } },
                        label = { Text(if (t == 0) "бесконечно" else "$t мин") },
                    )
                }
            }
        }

        items(SoundType.entries.size) { index ->
            val type = SoundType.entries[index]
            SoundCard(
                type = type,
                playing = currentSound == type,
                onPlay = { onPlay(type, timerMinutes) },
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun SoundCard(
    type: SoundType,
    playing: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        onClick = if (playing) onStop else onPlay,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (playing) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    RoundedCornerShape(14.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(type.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    type.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (playing) {
                Button(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Стоп")
                }
            }
        }
    }
}

private fun iconFor(type: SoundType): ImageVector = when (type) {
    SoundType.WHITE_NOISE -> Icons.Filled.Air
    SoundType.RAIN -> Icons.Filled.WaterDrop
    SoundType.DROPS -> Icons.Filled.GraphicEq
    SoundType.LULLABY -> Icons.Filled.MusicNote
}
