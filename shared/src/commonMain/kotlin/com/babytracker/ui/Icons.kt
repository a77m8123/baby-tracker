package com.babytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Иконки, нарисованные вручную на сетке 24×24, — без тяжёлой библиотеки material-icons. */
enum class Glyph {
    Home, Diary, Note, Heart, Gear,
    Moon, Sun, Breast, Bottle, Spoon, Diaper,
    Plus, Play, Stop, Pause, Swap, Trash, Left, Right, Check, Close,
    Ruler, Pill, Syringe, Chart, Doc, Share, Download, Bell, Clock,
}

@Composable
fun Icon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val u = size.width / 24f
    val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
    fun path(fill: Boolean = false, block: Path.() -> Unit) {
        val path = Path().apply(block)
        if (fill) drawPath(path, color) else drawPath(path, color, style = stroke)
    }
    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx * u, cy * u, x * u, y * u)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) =
        cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x * u, y * u)
    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
        if (fill) drawCircle(color, r * u, p(cx, cy)) else drawCircle(color, r * u, p(cx, cy), style = stroke)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) =
        if (fill) drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        else drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)

    when (glyph) {
        Glyph.Home -> path { m(4f, 11f); l(12f, 4f); l(20f, 11f); m(6f, 9.5f); l(6f, 20f); l(18f, 20f); l(18f, 9.5f) }
        Glyph.Diary -> {
            rrect(4.5f, 3.5f, 15f, 17f, 3f)
            line(8.5f, 9f, 15.5f, 9f); line(8.5f, 13f, 15.5f, 13f); line(8.5f, 17f, 12.5f, 17f)
        }
        Glyph.Note -> {
            path { m(9f, 18f); l(9f, 5.5f); l(19f, 3.5f); l(19f, 16f) }
            circle(6.8f, 18f, 2.3f, fill = true); circle(16.8f, 16f, 2.3f, fill = true)
        }
        Glyph.Heart -> path {
            m(12f, 20f); c(4f, 14.5f, 2.5f, 10.5f, 4.5f, 7.5f); c(6.5f, 4.5f, 10.5f, 5f, 12f, 8f)
            c(13.5f, 5f, 17.5f, 4.5f, 19.5f, 7.5f); c(21.5f, 10.5f, 20f, 14.5f, 12f, 20f)
        }
        Glyph.Gear -> {
            circle(12f, 12f, 3f)
            for (i in 0 until 8) {
                val a = i * kotlin.math.PI / 4
                val cx = 12f + kotlin.math.cos(a).toFloat() * 7.5f
                val cy = 12f + kotlin.math.sin(a).toFloat() * 7.5f
                val ix = 12f + kotlin.math.cos(a).toFloat() * 5.5f
                val iy = 12f + kotlin.math.sin(a).toFloat() * 5.5f
                line(ix, iy, cx, cy)
            }
            circle(12f, 12f, 5.8f)
        }
        Glyph.Moon -> path(fill = true) {
            m(15.5f, 3.5f); c(10f, 4f, 6.5f, 8.5f, 7.5f, 13.5f); c(8.5f, 18.5f, 14f, 21.5f, 19.5f, 19f)
            c(14.5f, 18.5f, 11.5f, 14f, 12.5f, 9.5f); c(13f, 7f, 14f, 5f, 15.5f, 3.5f); close()
        }
        Glyph.Sun -> {
            circle(12f, 12f, 4.2f, fill = true)
            for (i in 0 until 8) {
                val a = i * kotlin.math.PI / 4
                line(
                    12f + kotlin.math.cos(a).toFloat() * 7f, 12f + kotlin.math.sin(a).toFloat() * 7f,
                    12f + kotlin.math.cos(a).toFloat() * 9.3f, 12f + kotlin.math.sin(a).toFloat() * 9.3f,
                )
            }
        }
        Glyph.Breast -> path(fill = true) {
            // капля молока
            m(12f, 3f); c(12f, 3f, 5.5f, 10.5f, 5.5f, 14.5f); c(5.5f, 18.2f, 8.4f, 21f, 12f, 21f)
            c(15.6f, 21f, 18.5f, 18.2f, 18.5f, 14.5f); c(18.5f, 10.5f, 12f, 3f, 12f, 3f); close()
        }
        Glyph.Bottle -> {
            rrect(7.5f, 9f, 9f, 12f, 3f)
            path { m(9f, 9f); l(9.5f, 6f); l(14.5f, 6f); l(15f, 9f) }
            rrect(10.5f, 2.5f, 3f, 3.5f, 1.4f, fill = true)
            line(7.5f, 13f, 11f, 13f); line(7.5f, 16.5f, 11f, 16.5f)
        }
        Glyph.Spoon -> {
            drawOval(color, p(8f, 2.5f), Size(8f * u, 9.5f * u), style = stroke)
            line(12f, 12f, 12f, 21f)
        }
        Glyph.Diaper -> path {
            m(3.5f, 7f); l(20.5f, 7f); l(20.5f, 10f); c(20.5f, 15f, 16.5f, 19f, 12f, 19f)
            c(7.5f, 19f, 3.5f, 15f, 3.5f, 10f); close()
            m(8f, 7f); c(8f, 12f, 10f, 14f, 12f, 14f); c(14f, 14f, 16f, 12f, 16f, 7f)
        }
        Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Glyph.Play -> path(fill = true) { m(8f, 5f); l(19f, 12f); l(8f, 19f); close() }
        Glyph.Stop -> rrect(6.5f, 6.5f, 11f, 11f, 2.5f, fill = true)
        Glyph.Pause -> { rrect(7f, 5.5f, 3.5f, 13f, 1.2f, fill = true); rrect(13.5f, 5.5f, 3.5f, 13f, 1.2f, fill = true) }
        Glyph.Swap -> {
            path { m(4f, 8.5f); l(19f, 8.5f); m(15.5f, 5f); l(19f, 8.5f); l(15.5f, 12f) }
            path { m(20f, 15.5f); l(5f, 15.5f); m(8.5f, 12f); l(5f, 15.5f); l(8.5f, 19f) }
        }
        Glyph.Trash -> {
            line(4.5f, 6.5f, 19.5f, 6.5f)
            path { m(9.5f, 6.5f); l(9.5f, 4f); l(14.5f, 4f); l(14.5f, 6.5f) }
            path { m(6.5f, 6.5f); l(7.5f, 20f); l(16.5f, 20f); l(17.5f, 6.5f) }
        }
        Glyph.Left -> path { m(14.5f, 5.5f); l(8f, 12f); l(14.5f, 18.5f) }
        Glyph.Right -> path { m(9.5f, 5.5f); l(16f, 12f); l(9.5f, 18.5f) }
        Glyph.Check -> path { m(5f, 12.5f); l(10f, 17.5f); l(19f, 7f) }
        Glyph.Close -> { line(6.5f, 6.5f, 17.5f, 17.5f); line(17.5f, 6.5f, 6.5f, 17.5f) }
        Glyph.Ruler -> {
            rrect(3f, 8f, 18f, 8f, 1.8f)
            for (i in 0..4) line(6f + i * 3f, 8f, 6f + i * 3f, if (i % 2 == 0) 12.5f else 11f)
        }
        Glyph.Pill -> {
            drawRoundRect(color, p(3.5f, 8.5f), Size(17f * u, 7f * u), CornerRadius(3.5f * u), style = stroke)
            line(12f, 8.5f, 12f, 15.5f)
            rrect(3.5f, 8.5f, 8.5f, 7f, 3.5f, fill = true)
        }
        Glyph.Syringe -> {
            path { m(14f, 4f); l(20f, 10f); m(17f, 7f); l(8.5f, 15.5f); l(6.5f, 15.5f); l(6.5f, 13.5f); l(15f, 5f) }
            line(6.5f, 15.5f, 4f, 18f)
            line(11f, 9.5f, 12.5f, 11f)
        }
        Glyph.Chart -> {
            rrect(4f, 12f, 4f, 8f, 1.2f, fill = true)
            rrect(10f, 6f, 4f, 14f, 1.2f, fill = true)
            rrect(16f, 9f, 4f, 11f, 1.2f, fill = true)
        }
        Glyph.Doc -> {
            path { m(6f, 3.5f); l(14f, 3.5f); l(18.5f, 8f); l(18.5f, 20.5f); l(6f, 20.5f); close() }
            path { m(13.5f, 3.5f); l(13.5f, 8.5f); l(18.5f, 8.5f) }
            line(9f, 13f, 15.5f, 13f); line(9f, 16.5f, 15.5f, 16.5f)
        }
        Glyph.Share -> {
            path { m(12f, 14f); l(12f, 3.5f); m(8f, 7.5f); l(12f, 3.5f); l(16f, 7.5f) }
            path { m(8f, 10.5f); l(5.5f, 10.5f); l(5.5f, 20.5f); l(18.5f, 20.5f); l(18.5f, 10.5f); l(16f, 10.5f) }
        }
        Glyph.Download -> {
            path { m(12f, 3.5f); l(12f, 14.5f); m(7.5f, 10f); l(12f, 14.5f); l(16.5f, 10f) }
            line(5f, 19.5f, 19f, 19.5f)
        }
        Glyph.Bell -> {
            path { m(6f, 16.5f); l(6f, 10.5f); c(6f, 7f, 8.7f, 4.5f, 12f, 4.5f); c(15.3f, 4.5f, 18f, 7f, 18f, 10.5f); l(18f, 16.5f); l(19.5f, 18f); l(4.5f, 18f); close() }
            line(10.5f, 20.5f, 13.5f, 20.5f)
        }
        Glyph.Clock -> {
            circle(12f, 12f, 8.5f)
            path { m(12f, 7f); l(12f, 12f); l(15.5f, 14f) }
        }
    }
}
