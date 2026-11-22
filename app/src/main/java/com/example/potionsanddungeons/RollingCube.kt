package com.example.potionsanddungeons

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RollingCube(result: Int, progress: Float, lift: Float, modifier: Modifier = Modifier.size(180.dp)) {
    DrawDie(6, result, progress, lift, modifier)
}
