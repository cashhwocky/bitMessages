package com.bitdroid.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A shape traced around the center: radius(angle) as a fraction of half the box. */
class PolarShape(private val steps: Int = 180, private val radius: (Double) -> Double) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val s = min(size.width, size.height) / 2f
        val path = Path()
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val r = (radius(t) * s).toFloat()
            val x = cx + r * cos(t).toFloat()
            val y = cy + r * sin(t).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** A polygon with softly rounded corners (round = how far along each edge the curve starts). */
class RoundedPolygonShape(private val sides: Int, private val round: Float = 0.3f, private val rotation: Double = -PI / 2) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = min(size.width, size.height) / 2f
        // Odd polygons sit a little low so they look centered.
        val cy = size.height / 2f + if (sides % 2 == 1) s * 0.12f else 0f
        val cx = size.width / 2f
        val r = s * if (sides == 3) 1.08f else 0.98f
        val v = List(sides) { i ->
            val a = rotation + 2 * PI * i / sides
            Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        val path = Path()
        for (i in 0 until sides) {
            val prev = v[(i - 1 + sides) % sides]
            val cur = v[i]
            val next = v[(i + 1) % sides]
            val p1 = cur + (prev - cur) * round
            val p2 = cur + (next - cur) * round
            if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
            path.quadraticTo(cur.x, cur.y, p2.x, p2.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

private fun superellipse(t: Double, p: Double) = (abs(cos(t)).pow(p) + abs(sin(t)).pow(p)).pow(-1.0 / p)

/** The bitHome shape set: ripples, clovers, bursts and friends, like the Pixel home screen. */
object Shapes {
    val Circle: Shape = CircleShape
    val Ripple: Shape = PolarShape { t -> 0.88 + 0.09 * cos(9 * t) }
    val Ripple7: Shape = PolarShape { t -> 0.86 + 0.12 * cos(7 * t) }
    val Sunny: Shape = PolarShape { t -> 0.92 + 0.06 * cos(12 * t) }
    val Clover: Shape = PolarShape { t -> 0.74 + 0.25 * abs(cos(2 * t)).pow(0.6) }
    val Squircle: Shape = PolarShape { t -> superellipse(t, 4.0) }
    val Gem: Shape = PolarShape { t -> 0.98 * superellipse(t, 1.35) }
    val Triangle: Shape = RoundedPolygonShape(3, 0.32f)
    val Pentagon: Shape = RoundedPolygonShape(5, 0.28f)
}

/** Screen background: solid black, or the bitHome purple-to-teal wallpaper glow. */
fun Modifier.backdrop(): Modifier =
    if (!Bit.expressive) background(Bit.Black) else drawBehind {
        drawRect(Brush.verticalGradient(0f to Color(0xFF221A45), 0.45f to Color(0xFF120F1C), 1f to Color(0xFF0F1419)))
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF5C40C8).copy(alpha = 0.7f), Color.Transparent),
                center = Offset(size.width * 0.15f, 0f), radius = size.width * 1.0f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF1E6E6E).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(size.width, size.height), radius = size.width * 0.9f,
            ),
        )
    }
