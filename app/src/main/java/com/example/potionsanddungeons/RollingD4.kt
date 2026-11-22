package com.example.potionsanddungeons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
internal fun D4RollOverlay(result: Int, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1100))
        delay(700)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)).clickable {},
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingTetrahedron(result, progress.value)
            Text(if (progress.value < 1f) "Бросаем д4…" else "Д4: выпало $result",color=Color.White)
        }
    }
}

@Composable
internal fun RollingTetrahedron(result: Int, progress: Float, modifier: Modifier = Modifier.size(180.dp)) {
    val bodyColor = animatedDiceColor(4)
            Canvas(modifier) {
                val t = progress
                val bounce = abs(sin(t*PI.toFloat()*3))*(1-t)
                val vertices = listOf(
                    floatArrayOf(0f,-1.1547005f,0.4082483f),
                    floatArrayOf(-1f,0.5773503f,0.4082483f),
                    floatArrayOf(1f,0.5773503f,0.4082483f),
                    floatArrayOf(0f,0f,-1.2247449f)
                )
                val angle = 0.58f + (1-t)*PI.toFloat()*6
                val tilt = -0.25f + (1-t)*PI.toFloat()*4
                val rotated = vertices.map { v ->
                    val y = v[1]*cos(tilt)-v[2]*sin(tilt)
                    val z = v[1]*sin(tilt)+v[2]*cos(tilt)
                    floatArrayOf(v[0]*cos(angle)+z*sin(angle),y,-v[0]*sin(angle)+z*cos(angle))
                }
                val points = rotated.map { v ->
                    val scale = size.width*0.29f*5/(5-v[2])
                    Offset(size.width/2+v[0]*scale-(1-t)*size.width*0.4f,
                        size.height/2+v[1]*scale-bounce*size.height*0.35f)
                }
                val shadowCenter = Offset(size.width*(0.55f-(1-t)*0.4f),size.height*0.84f)
                for (layer in 8 downTo 1) {
                    val spread = size.width*(0.20f+layer*0.012f+bounce*0.08f)
                    drawOval(
                        Color.Black.copy(alpha=0.035f*(1f-bounce*0.65f)),
                        Offset(shadowCenter.x-spread,shadowCenter.y-spread*0.24f),
                        androidx.compose.ui.geometry.Size(spread*2,spread*0.48f)
                    )
                }
                val faces = listOf(listOf(0,1,2),listOf(0,3,1),listOf(0,2,3),listOf(1,3,2))
                faces.withIndex().sortedBy { face -> face.value.sumOf { rotated[it][2].toDouble() } }.forEach { (index, indices) ->
                    val a=points[indices[0]]; val b=points[indices[1]]; val c=points[indices[2]]
                    if ((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x) >= 0f) return@forEach
                    val path=Path().apply { moveTo(a.x,a.y);lineTo(b.x,b.y);lineTo(c.x,c.y);close() }
                    val origin = rotated[indices[0]]
                    val edgeA = FloatArray(3) { rotated[indices[1]][it]-origin[it] }
                    val edgeB = FloatArray(3) { rotated[indices[2]][it]-origin[it] }
                    val normal = floatArrayOf(
                        edgeA[1]*edgeB[2]-edgeA[2]*edgeB[1],
                        edgeA[2]*edgeB[0]-edgeA[0]*edgeB[2],
                        edgeA[0]*edgeB[1]-edgeA[1]*edgeB[0]
                    )
                    val length = sqrt(normal.sumOf { (it*it).toDouble() }).toFloat()
                    val light = ((normal[0]*0.4f+normal[1]*0.6f-normal[2]*0.7f)/length).coerceIn(0f,1f)
                    val brightness = 0.57f+light*0.37f
                    val base = shadedDiceColor(bodyColor, brightness)
                    val highlight = shadedDiceColor(bodyColor, (brightness+0.07f).coerceAtMost(1f))
                    drawPath(path,Brush.linearGradient(listOf(highlight,base),a,c))
                    val matrix = android.graphics.Matrix()
                    matrix.setPolyToPoly(
                        floatArrayOf(50f,0f,0f,100f,100f,100f),0,
                        floatArrayOf(a.x,a.y,b.x,b.y,c.x,c.y),0,3
                    )
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color=diceNumberColor(bodyColor)
                        textSize=42f
                        textAlign=android.graphics.Paint.Align.CENTER
                        typeface=android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val native = drawContext.canvas.nativeCanvas
                    native.save()
                    native.concat(matrix)
                    val faceValue = if (result < 1) index+1 else ((result-1+index)%4)+1
                    val baseline = 64f-(paint.ascent()+paint.descent())/2f
                    native.drawText(faceValue.toString(),50f,baseline,paint)
                    native.restore()
                }
            }

}
