package com.example.potionsanddungeons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

@Composable
internal fun PotionIcon(source: DeckSource, variant: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dlc = source == DeckSource.DLC
        val glass = Path().apply {
            moveTo(w*0.39f,h*0.17f)
            lineTo(w*0.39f,h*0.34f)
            if (dlc) {
                when (variant) {
                    0 -> { lineTo(w*0.16f,h*0.52f);lineTo(w*0.22f,h*0.89f);lineTo(w*0.78f,h*0.89f);lineTo(w*0.84f,h*0.52f) }
                    1 -> { lineTo(w*0.12f,h*0.66f);lineTo(w*0.5f,h*0.96f);lineTo(w*0.88f,h*0.66f) }
                    2 -> { lineTo(w*0.22f,h*0.45f);lineTo(w*0.22f,h*0.92f);lineTo(w*0.78f,h*0.92f);lineTo(w*0.78f,h*0.45f) }
                    else -> { lineTo(w*0.32f,h*0.45f);lineTo(w*0.10f,h*0.88f);lineTo(w*0.90f,h*0.88f);lineTo(w*0.68f,h*0.45f) }
                }
                lineTo(w*0.61f,h*0.34f)
            } else {
                when (variant) {
                    0 -> {
                        cubicTo(w*0.03f,h*0.43f,w*0.04f,h*0.94f,w*0.50f,h*0.94f)
                        cubicTo(w*0.96f,h*0.94f,w*0.97f,h*0.43f,w*0.61f,h*0.34f)
                    }
                    1 -> {
                        cubicTo(w*0.18f,h*0.48f,w*0.12f,h*0.90f,w*0.36f,h*0.92f)
                        quadraticBezierTo(w*0.50f,h*0.98f,w*0.64f,h*0.92f)
                        cubicTo(w*0.88f,h*0.90f,w*0.82f,h*0.48f,w*0.61f,h*0.34f)
                    }
                    2 -> {
                        cubicTo(w*0.0f,h*0.56f,w*0.09f,h*0.84f,w*0.28f,h*0.90f)
                        quadraticBezierTo(w*0.50f,h*0.98f,w*0.72f,h*0.90f)
                        cubicTo(w*0.91f,h*0.84f,w*1.0f,h*0.56f,w*0.61f,h*0.34f)
                    }
                    else -> {
                        cubicTo(w*0.16f,h*0.39f,w*0.24f,h*0.61f,w*0.19f,h*0.75f)
                        cubicTo(w*0.10f,h*1.0f,w*0.90f,h*1.0f,w*0.81f,h*0.75f)
                        cubicTo(w*0.76f,h*0.61f,w*0.84f,h*0.39f,w*0.61f,h*0.34f)
                    }
                }
            }
            lineTo(w*0.61f,h*0.17f)
            close()
        }
        val liquid = if (dlc) Color(0xFFB68BFA) else Color(0xFF67D7BC)
        drawPath(glass,Color(0xFFBCE7F0).copy(alpha=0.18f))
        clipPath(glass) {
            drawRect(Brush.verticalGradient(listOf(liquid,liquid.copy(alpha=0.55f)),h*0.53f,h),
                Offset(0f,h*0.53f),Size(w,h*0.47f))
            drawLine(Color.White.copy(alpha=0.5f),Offset(w*0.30f,h*0.47f),Offset(w*0.27f,h*0.73f),w*0.055f)
            drawCircle(Color.White.copy(alpha=0.55f),w*0.035f,Offset(w*0.59f,h*0.70f))
        }
        drawPath(glass,Color(0xFFC3E4EC),style=Stroke(w*0.035f))
        drawRoundRect(Color(0xFFBA8A58),Offset(w*0.35f,h*0.07f),Size(w*0.30f,h*0.12f),
            androidx.compose.ui.geometry.CornerRadius(w*0.03f))
        drawLine(Color(0xFFE3B878),Offset(w*0.38f,h*0.09f),Offset(w*0.60f,h*0.09f),w*0.025f)
    }
}
