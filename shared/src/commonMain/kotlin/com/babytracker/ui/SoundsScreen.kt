package com.babytracker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.Platform
import com.babytracker.data.BabyStore
import com.babytracker.logic.timer
import com.babytracker.sound.SoundType

@Composable
fun SoundsScreen(store: BabyStore, platform: Platform) {
    val c = Baby.colors
    val player = platform.sound
    val playing = player.nowPlaying
    val settings = store.data.settings
    val now = rememberNow()

    ScreenColumn {
        BabyCard(color = c.sound.soft(c)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Equalizer(playing != null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(playing?.type?.title ?: "Тишина", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            playing == null -> "Выберите звук ниже"
                            playing.endsAt == null -> "играет без таймера"
                            else -> "затихнет через ${timer(playing.endsAt - now)}"
                        },
                        color = c.muted, fontSize = 14.sp,
                    )
                }
                if (playing != null) IconButton(Glyph.Stop, c.sound, { player.stop() }, size = 52.dp)
            }
            Spacer(Modifier.height(14.dp))
            FieldLabel("Громкость")
            Slider(
                value = settings.volume,
                onValueChange = { v ->
                    store.updateSettings { it.copy(volume = v) }
                    player.setVolume(v)
                },
                colors = SliderDefaults.colors(
                    thumbColor = c.sound,
                    activeTrackColor = c.sound,
                    inactiveTrackColor = c.cardSoft,
                ),
            )
            FieldLabel("Выключить через")
            ChipRow(
                listOf(15 to "15 м", 30 to "30 м", 60 to "1 ч", 120 to "2 ч", 0 to "∞"),
                settings.soundTimerMinutes,
                { minutes ->
                    store.updateSettings { it.copy(soundTimerMinutes = minutes) }
                    playing?.let { player.play(it.type, minutes, settings.volume) }
                },
                c.sound,
            )
        }

        Text(
            "Звук продолжит играть при выключенном экране и в последнюю минуту плавно затихнет.",
            color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )

        SoundType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { type ->
                    SoundTile(type, active = playing?.type == type, Modifier.weight(1f)) {
                        if (playing?.type == type) player.stop()
                        else player.play(type, settings.soundTimerMinutes, settings.volume)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SoundTile(type: SoundType, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Baby.colors
    Column(
        modifier
            .clip(TileShape)
            .background(if (active) c.sound else c.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (active) c.onAccent.copy(alpha = 0.2f) else c.sound.soft(c)),
            contentAlignment = Alignment.Center,
        ) { Icon(if (active) Glyph.Pause else Glyph.Play, if (active) c.onAccent else c.sound, size = 20.dp) }
        Text(type.title, color = if (active) c.onAccent else c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            type.subtitle, color = if (active) c.onAccent.copy(alpha = 0.8f) else c.muted, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
        )
    }
}

/** Три полоски «эквалайзера», которые пляшут, пока играет звук. */
@Composable
private fun Equalizer(playing: Boolean) {
    val c = Baby.colors
    val transition = rememberInfiniteTransition(label = "eq")
    val phases = (0..2).map { i ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Box(Modifier.size(52.dp).clip(CircleShape).background(c.sound), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width / 5
            phases.forEachIndexed { i, state ->
                val h = size.height * (if (playing) state.value else 0.3f)
                val x = w * (0.5f + i * 2f) + w / 2
                drawLine(c.onAccent, Offset(x, (size.height + h) / 2), Offset(x, (size.height - h) / 2), w, StrokeCap.Round)
            }
        }
    }
}
