package com.example.potionsanddungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.abs

internal fun DrawScope.drawDieNumber(
    value: Int, points: List<Offset>, depths: () -> List<Float>, color: Int
) {
    val digit = robotoDiceDigits.getOrNull(value) ?: return
    val triangle = points.size == 3
    val top = points[0]
    val right = points[1]
    val bottom = points[2]
    val area = (right.x - top.x) * (bottom.y - top.y) - (right.y - top.y) * (bottom.x - top.x)
    if (abs(area) < maxOf((right - top).getDistance(), (bottom - top).getDistance()) * 3f) return

    val textScale = if (triangle) 0.00042f else 0.00060f
    val faceDepths = depths()
    fun project(x: Int, y: Int): Offset {
        val u = 0.5f + x * textScale
        val v = (if (triangle) 0.64f else 0.5f) + y * textScale
        return projectDieFace(points, faceDepths, u, v)
    }
    val path = Path().apply {
        // Roboto uses overlapping outlines for some digits, including four.
        fillType = PathFillType.NonZero
        digit.forEach { contour ->
            val initial = project(contour[0], contour[1])
            moveTo(initial.x, initial.y)
            for (index in 2 until contour.size step 2) {
                val pixel = project(contour[index], contour[index + 1])
                lineTo(pixel.x, pixel.y)
            }
            close()
        }
    }
    val center = points.reduce(Offset::plus) / points.size.toFloat()
    val inset = Path().apply {
        points.forEachIndexed { index, corner ->
            val inner = center + (corner - center) * 0.88f
            if (index == 0) moveTo(inner.x, inner.y) else lineTo(inner.x, inner.y)
        }
        close()
    }
    clipPath(inset) { drawPath(path, Color(color)) }
}

internal fun projectDieFace(points: List<Offset>, depths: List<Float>, u: Float, v: Float): Offset {
    val weights = if (points.size == 3) {
        floatArrayOf(1 - v, v / 2 - (u - 0.5f), v / 2 + (u - 0.5f))
    } else {
        floatArrayOf((1 - u) * (1 - v), u * (1 - v), u * v, (1 - u) * v)
    }
    var projected = Offset.Zero
    var depth = 0f
    // Undo the perspective divide before interpolating on the face, then apply it again.
    points.forEachIndexed { index, point ->
        val contribution = weights[index] * depths[index]
        projected += point * contribution
        depth += contribution
    }
    return projected / depth
}
