package com.example.potionsanddungeons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class Point3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(p: Point3) = Point3(x + p.x, y + p.y, z + p.z)
    operator fun minus(p: Point3) = Point3(x - p.x, y - p.y, z - p.z)
    operator fun times(scale: Float) = Point3(x * scale, y * scale, z * scale)
    fun dot(p: Point3) = x * p.x + y * p.y + z * p.z
    fun cross(p: Point3) = Point3(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x)
    fun rotate(xAngle: Float, yAngle: Float): Point3 {
        val yy = y * cos(xAngle) - z * sin(xAngle)
        val zz = y * sin(xAngle) + z * cos(xAngle)
        return Point3(x * cos(yAngle) + zz * sin(yAngle), yy, -x * sin(yAngle) + zz * cos(yAngle))
    }
}

private data class DieSurface(val corners: List<Point3>, val number: Int? = null)
private data class VisibleSurface(val number: Int?, val corners: List<Offset>, val depths: List<Float>, val light: Float, val depth: Float)
private val cubePoints = listOf(
    Point3(-1f, -1f, 1f), Point3(1f, -1f, 1f), Point3(1f, 1f, 1f), Point3(-1f, 1f, 1f),
    Point3(-1f, -1f, -1f), Point3(1f, -1f, -1f), Point3(1f, 1f, -1f), Point3(-1f, 1f, -1f)
)
private val tetraPoints = listOf(
    Point3(0f, -1.1547f, 0.4082f), Point3(-1f, 0.57735f, 0.4082f),
    Point3(1f, 0.57735f, 0.4082f), Point3(0f, 0f, -1.2247f)
)

private val cube = bevelMesh(cubePoints, listOf(
    listOf(0, 1, 2, 3), listOf(1, 5, 6, 2), listOf(4, 5, 1, 0),
    listOf(3, 2, 6, 7), listOf(4, 0, 3, 7), listOf(5, 4, 7, 6)
))
private val tetrahedron = bevelMesh(tetraPoints, listOf(
    listOf(0, 1, 2), listOf(0, 3, 1), listOf(0, 2, 3), listOf(1, 3, 2)
))

private fun bevelMesh(vertices: List<Point3>, faces: List<List<Int>>): List<DieSurface> {
    fun center(points: List<Point3>) = points.reduce(Point3::plus) * (1f / points.size)
    val faceCorners = faces.map { face ->
        val middle = center(face.map(vertices::get))
        face.associateWith { vertex -> vertices[vertex] * 0.88f + middle * 0.12f }
    }
    val mesh = faces.mapIndexed { index, face ->
        DieSurface(face.map(faceCorners[index]::getValue), index + 1)
    }.toMutableList()
    // Bridge the inset faces along each edge, then close the remaining three-way corners.
    faces.forEachIndexed { index, face ->
        face.forEachIndexed { i, a ->
            val b = face[(i + 1) % face.size]
            val other = faces.indices.firstOrNull { it > index && a in faces[it] && b in faces[it] }
            if (other != null) {
                val near = faceCorners[index]
                val far = faceCorners[other]
                mesh.add(DieSurface(listOf(near.getValue(a), near.getValue(b), far.getValue(b), far.getValue(a))))
            }
        }
    }
    vertices.indices.forEach { vertex -> mesh.add(DieSurface(faceCorners.mapNotNull { it[vertex] })) }
    return mesh
}

@Composable
internal fun DrawDie(sides: Int, result: Int, progress: Float, lift: Float, modifier: Modifier) {
    val triangular = sides == 4
    val mesh = if (triangular) tetrahedron else cube
    val body = animatedDiceColor(sides)
    val quarter = (PI / 2).toFloat()
    val orientation = when {
        triangular -> 0f to 0f
        result == 2 -> 0f to -quarter
        result == 3 -> -quarter to 0f
        result == 4 -> quarter to 0f
        result == 5 -> 0f to quarter
        result == 6 -> 0f to 2 * quarter
        else -> 0f to 0f
    }
    val resultX by animateFloatAsState(orientation.first, tween(180))
    val resultY by animateFloatAsState(orientation.second, tween(180))
    Canvas(modifier) {
        val remaining = 1f - progress
        val x = (if (triangular) -0.38f else -0.40f) + remaining * 8 * quarter
        val y = (if (triangular) 0.48f else 0.46f) + remaining * 12 * quarter
        val bounce = if (triangular) sin(progress * PI.toFloat() * 2).let { it * it * remaining } else lift
        val center = Offset(size.width * (0.5f - 0.44f * remaining), size.height * (0.52f - 0.20f * bounce))
        val scale = size.minDimension * (if (triangular) 0.31f else 0.26f)
        val camera = Point3(0f, 0f, 5f)
        val light = Point3(-0.44f, -0.64f, 0.63f)
        val surfaces = mesh.mapNotNull { face ->
            val rotated = face.corners.map { it.rotate(resultX, resultY).rotate(x, y) }
            val middle = rotated.reduce(Point3::plus) * (1f / rotated.size)
            val cross = (rotated[1] - rotated[0]).cross(rotated[2] - rotated[0])
            val normal = cross * ((if (cross.dot(middle) < 0) -1f else 1f) / sqrt(cross.dot(cross)))
            if (normal.dot(camera - middle) <= 0f) return@mapNotNull null
            val depths = rotated.map { 5f - it.z }
            val points = rotated.mapIndexed { i, p -> center + Offset(p.x, p.y) * (5f * scale / depths[i]) }
            VisibleSurface(face.number, points, depths, normal.dot(light).coerceAtLeast(0f), middle.z)
        }.sortedBy { it.depth }

        val shadowRadius = size.width * (0.24f + 0.08f * bounce)
        for (layer in 1..6) {
            val spread = shadowRadius + layer * size.width * 0.012f
            drawOval(Color.Black.copy(alpha = 0.04f * (1 - bounce * 0.7f)),
                Offset(center.x - spread, size.height * 0.86f - spread * 0.20f), Size(spread * 2, spread * 0.4f))
        }
        surfaces.forEach { face ->
            val path = Path().apply {
                moveTo(face.corners[0].x, face.corners[0].y)
                face.corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val brightness = 0.51f + 0.43f * face.light
            val base = shadedDiceColor(body, brightness)
            drawPath(path, base, style = Stroke(width = 1.1f))
            drawPath(path, Brush.linearGradient(
                listOf(shadedDiceColor(body, (brightness + 0.09f).coerceAtMost(1f)), base),
                Offset(center.x - scale, center.y - scale), Offset(center.x + scale, center.y + scale)
            ))
        }
        surfaces.forEach { face ->
            val number = face.number ?: return@forEach
            val label = when {
                triangular && result in 1..4 -> (result + number - 2) % 4 + 1
                !triangular && number == 1 && (result == 0 || result == 7) -> result
                else -> number
            }
            drawDieNumber(label, face.corners, { face.depths }, diceNumberColor(body))
        }
    }
}
