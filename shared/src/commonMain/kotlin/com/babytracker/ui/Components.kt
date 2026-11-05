package com.babytracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.logic.nowMs
import kotlinx.coroutines.delay

val CardShape = RoundedCornerShape(24.dp)
val TileShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(50)

/** Текущее время, обновляется каждые [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = nowMs()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun BabyCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Baby.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color ?: c.card)
            .then(if (color == null && !c.isDark) Modifier.border(1.dp, c.border, CardShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Baby.colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Кружок с иконкой в цвет раздела. */
@Composable
fun IconBadge(glyph: Glyph, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.soft(Baby.colors)),
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, color, size = size * 0.55f) }
}

@Composable
fun PrimaryButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    enabled: Boolean = true,
) {
    val c = Baby.colors
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(PillShape)
            .background(if (enabled) color else c.cardSoft)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.onAccent else c.muted
        if (glyph != null) {
            Icon(glyph, fg, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
) {
    val c = Baby.colors
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(PillShape)
            .background(color.soft(c))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Icon(glyph, color, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Большая плитка быстрого действия на главном экране. */
@Composable
fun ActionTile(title: String, subtitle: String, glyph: Glyph, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Baby.colors
    Column(
        modifier
            .clip(TileShape)
            .background(color.soft(c))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(glyph, c.onAccent, size = 22.dp)
        }
        Column {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Сегменты-«таблетки»: выбор одного из вариантов. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Baby.colors.sleep,
) {
    val c = Baby.colors
    Row(
        modifier.fillMaxWidth().clip(PillShape).background(c.cardSoft).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (active) color else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) c.onAccent else c.text,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Чипы с быстрым выбором значения. */
@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, color: Color) {
    val c = Baby.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (active) color else color.soft(c))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) c.onAccent else color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** − значение + */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int,
    range: IntRange,
    color: Color,
    format: (Int) -> String = { it.toString() },
) {
    val c = Baby.colors
    Row(
        Modifier.fillMaxWidth().clip(PillShape).background(c.cardSoft).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton("−", color) { onChange((value - step).coerceIn(range)) }
        Text(
            format(value),
            color = c.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton("+", color) { onChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun RoundIconButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(color.soft(Baby.colors)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun IconButton(glyph: Glyph, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.soft(Baby.colors)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, color, size = size * 0.5f) }
}

/** Кольцо прогресса; [progress] 0…1 (больше 1 — полное кольцо). */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    content: @Composable () -> Unit = {},
) {
    val track = Baby.colors.cardSoft
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = strokeWidth.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Тонкая полоса прогресса. */
@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(PillShape).background(Baby.colors.cardSoft)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(PillShape).background(color))
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
) {
    val c = Baby.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.cardSoft).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(TextStyle(color = c.text, fontSize = 16.sp)),
                cursorBrush = SolidColor(c.sleep),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (suffix != null) Text(suffix, color = c.muted, fontSize = 15.sp)
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, color = Baby.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

/** Строка настроек: заголовок, подпись и что-то справа. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = Baby.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun BabySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Baby.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.sleep,
            uncheckedThumbColor = c.muted,
            uncheckedTrackColor = c.cardSoft,
            uncheckedBorderColor = c.border,
        ),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val c = Baby.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (danger) c.danger else c.sleep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.text) } },
        title = { Text(title) },
        text = { Text(text) },
        containerColor = c.card,
        titleContentColor = c.text,
        textContentColor = c.muted,
    )
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        color = Baby.colors.muted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}

/** Метка со значением для сводок: «9 ч 20 мин / сон». */
@Composable
fun StatCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Baby.colors.muted, fontSize = 12.sp, maxLines = 1)
    }
}
