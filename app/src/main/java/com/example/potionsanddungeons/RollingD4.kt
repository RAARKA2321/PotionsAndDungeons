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
    DrawDie(4, result, progress, 0f, modifier)
}
