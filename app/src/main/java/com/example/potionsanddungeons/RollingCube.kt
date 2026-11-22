package com.example.potionsanddungeons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class CubePoint(val x: Float, val y: Float, val z: Float)
private data class CubeFace(val value: Int, val corners: List<CubePoint>)

private val cubeFaces = listOf(
    CubeFace(1, listOf(CubePoint(-1f,-1f,1f), CubePoint(1f,-1f,1f), CubePoint(1f,1f,1f), CubePoint(-1f,1f,1f))),
    CubeFace(6, listOf(CubePoint(1f,-1f,-1f), CubePoint(-1f,-1f,-1f), CubePoint(-1f,1f,-1f), CubePoint(1f,1f,-1f))),
    CubeFace(2, listOf(CubePoint(1f,-1f,1f), CubePoint(1f,-1f,-1f), CubePoint(1f,1f,-1f), CubePoint(1f,1f,1f))),
    CubeFace(5, listOf(CubePoint(-1f,-1f,-1f), CubePoint(-1f,-1f,1f), CubePoint(-1f,1f,1f), CubePoint(-1f,1f,-1f))),
    CubeFace(3, listOf(CubePoint(-1f,-1f,-1f), CubePoint(1f,-1f,-1f), CubePoint(1f,-1f,1f), CubePoint(-1f,-1f,1f))),
    CubeFace(4, listOf(CubePoint(-1f,1f,1f), CubePoint(1f,1f,1f), CubePoint(1f,1f,-1f), CubePoint(-1f,1f,-1f)))
)

@Composable
internal fun RollingCube(result: Int, progress: Float, lift: Float, modifier: Modifier = Modifier.size(180.dp)) {
    // Orient the actual result toward the camera, then retain a slight view of the side faces.
    val bodyColor = animatedDiceColor(6)
    val orientation = when (result) {
        2 -> 0f to -1.5708f
        5 -> 0f to 1.5708f
        3 -> -1.5708f to 0f
        4 -> 1.5708f to 0f
        6 -> 0f to 3.14159f
        else -> 0f to 0f
    }
    val resultX by animateFloatAsState(orientation.first, tween(180))
    val resultY by animateFloatAsState(orientation.second, tween(180))
    Canvas(modifier) {
        fun rotate(point: CubePoint, xAngle: Float, yAngle: Float): CubePoint {
            val y = point.y * cos(xAngle) - point.z * sin(xAngle)
            val z = point.y * sin(xAngle) + point.z * cos(xAngle)
            return CubePoint(point.x * cos(yAngle) + z * sin(yAngle), y, -point.x * sin(yAngle) + z * cos(yAngle))
        }
        fun transform(point: CubePoint): CubePoint {
            val oriented = rotate(point, resultX, resultY)
            return rotate(oriented, -0.25f + (1f-progress)*12.566f, 0.35f + (1f-progress)*18.85f)
        }
        fun project(point: CubePoint): Offset {
            val perspective = 5f / (5f - point.z)
            return Offset(size.width/2 + point.x*size.width*0.24f*perspective - (1f-progress)*size.width*0.6f,
                size.height/2 + point.y*size.height*0.24f*perspective - lift*size.height*0.2f)
        }
        val shadowCenter = Offset(size.width * (0.55f - (1f-progress)*0.6f), size.height*0.84f)
        for (layer in 8 downTo 1) {
            val spread = size.width * (0.20f + layer*0.012f + lift*0.08f)
            drawOval(
                Color.Black.copy(alpha = 0.035f*(1f-lift*0.65f)),
                Offset(shadowCenter.x-spread, shadowCenter.y-spread*0.24f),
                androidx.compose.ui.geometry.Size(spread*2,spread*0.48f)
            )
        }
        cubeFaces.sortedBy { face -> face.corners.sumOf { transform(it).z.toDouble() } }.forEach { face ->
            val corners = face.corners.map(::transform)
            val points = corners.map(::project)
            val cross = (points[1].x-points[0].x)*(points[2].y-points[0].y) -
                (points[1].y-points[0].y)*(points[2].x-points[0].x)
            if (cross <= 0f) return@forEach
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val edgeA = CubePoint(corners[1].x-corners[0].x,corners[1].y-corners[0].y,corners[1].z-corners[0].z)
            val edgeB = CubePoint(corners[3].x-corners[0].x,corners[3].y-corners[0].y,corners[3].z-corners[0].z)
            val normal = CubePoint(edgeA.y*edgeB.z-edgeA.z*edgeB.y,
                edgeA.z*edgeB.x-edgeA.x*edgeB.z,edgeA.x*edgeB.y-edgeA.y*edgeB.x)
            val length = sqrt(normal.x*normal.x+normal.y*normal.y+normal.z*normal.z)
            val light = ((-normal.x*0.4f-normal.y*0.6f+normal.z*0.7f)/length).coerceIn(0f,1f)
            val brightness = 0.57f + light*0.37f
            val base = shadedDiceColor(bodyColor, brightness)
            drawPath(path, Brush.linearGradient(
                listOf(shadedDiceColor(bodyColor, (brightness+0.07f).coerceAtMost(1f)),base),
                points[0],points[2]
            ))
            val faceValue = if (face.value == 1 && result in listOf(0, 7)) result else face.value
            val native = drawContext.canvas.nativeCanvas
            val matrix = android.graphics.Matrix()
            val source = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
            val destination = points.flatMap { listOf(it.x, it.y) }.toFloatArray()
            matrix.setPolyToPoly(source, 0, destination, 0, 4)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = diceNumberColor(bodyColor)
                textSize = 60f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            native.save()
            native.concat(matrix)
            val baseline = 50f - (paint.ascent() + paint.descent()) / 2f
            native.drawText(faceValue.toString(), 50f, baseline, paint)
            native.restore()
        }
    }
}
