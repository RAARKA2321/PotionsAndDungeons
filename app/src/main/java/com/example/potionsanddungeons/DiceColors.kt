package com.example.potionsanddungeons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

internal val LocalDiceColors = compositionLocalOf { DiceColors() }

internal data class DiceColors(
    val d4: Color = Color(0xFFF0F1FA),
    val d6: Color = Color(0xFFF0F1FA),
    val rainbowD4: Boolean = false,
    val rainbowD6: Boolean = false
)

@Composable
internal fun animatedDiceColor(sides: Int): Color {
    val colors = LocalDiceColors.current
    val rainbow = if (sides == 4) colors.rainbowD4 else colors.rainbowD6
    if (!rainbow) return if (sides == 4) colors.d4 else colors.d6
    val transition = rememberInfiniteTransition(label = "Перелив кубика")
    val hue by transition.animateFloat(
        initialValue=0f, targetValue=360f,
        animationSpec=infiniteRepeatable(tween(5000,easing=LinearEasing)),
        label="Оттенок"
    )
    return Color.hsv(hue%360f,0.65f,1f)
}

internal fun shadedDiceColor(color: Color, brightness: Float): Color =
    Color(color.red*brightness, color.green*brightness, color.blue*brightness, 1f)

internal fun diceNumberColor(color: Color): Int =
    if (color.luminance() > 0.45f) android.graphics.Color.rgb(25,41,56) else android.graphics.Color.WHITE

@Composable
internal fun DiceColorSettings(colors: DiceColors, onChange: (DiceColors) -> Unit) {
    var customSides by remember { mutableStateOf<Int?>(null) }
    val palette = listOf(
        "Белый" to Color(0xFFF0F1FA), "Красный" to Color(0xFFE87171),
        "Синий" to Color(0xFF709EE8), "Зелёный" to Color(0xFF70C8A5),
        "Золотой" to Color(0xFFECC878), "Фиолетовый" to Color(0xFFB28AE0)
    )
    for (sides in listOf(4,6)) {
        Text("Цвет д$sides", style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 480.dp) 1 else 2
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            palette.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (label, color) ->
                        val rainbow = if (sides == 4) colors.rainbowD4 else colors.rainbowD6
                        val selected = !rainbow && (if (sides == 4) colors.d4 else colors.d6) == color
                        FilterChip(
                            selected = selected,
                            onClick = { onChange(if (sides == 4) colors.copy(d4=color,rainbowD4=false) else colors.copy(d6=color,rainbowD6=false)) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor=color.copy(alpha=0.2f), selectedContainerColor=color,
                                selectedLabelColor=if(color.luminance()>0.45f) Color(0xFF192938) else Color.White
                            )
                        )
                    }
                }
            }
            Column {
                FilterChip(
                    selected=if(sides==4) colors.rainbowD4 else colors.rainbowD6,
                    onClick={ onChange(if(sides==4) colors.copy(rainbowD4=true) else colors.copy(rainbowD6=true)) },
                    label={ Text("Радужный") }
                )
                TextButton(onClick={ customSides=sides }) { Text("Свой цвет") }
            }
        }
        Box(Modifier.width(120.dp).height(160.dp),contentAlignment=Alignment.Center) {
            CompositionLocalProvider(LocalDiceColors provides colors) {
                if (sides==4) RollingTetrahedron(4,1f,Modifier.size(120.dp))
                else RollingCube(6,1f,0f,Modifier.size(120.dp))
            }
        }
        }
        }
    }
    customSides?.let { sides ->
        CustomDiceColorDialog(
            initial=if(sides==4) colors.d4 else colors.d6,
            onDismiss={ customSides=null },
            onApply={ color ->
                onChange(if(sides==4) colors.copy(d4=color,rainbowD4=false) else colors.copy(d6=color,rainbowD6=false))
                customSides=null
            }
        )
    }
}

@Composable
private fun CustomDiceColorDialog(initial: Color, onDismiss: () -> Unit, onApply: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.RGBToHSV(
            (initial.red*255).toInt(),(initial.green*255).toInt(),(initial.blue*255).toInt(),it
        ) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue.coerceAtMost(359.99f),saturation,brightness)
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text("Свой цвет кубика") },
        text={ Column {
            Box(Modifier.fillMaxWidth().height(64.dp).background(color,RoundedCornerShape(12.dp)))
            Text("Оттенок")
            Slider(hue,{hue=it},valueRange=0f..360f)
            Text("Насыщенность")
            Slider(saturation,{saturation=it})
            Text("Яркость")
            Slider(brightness,{brightness=it})
        } },
        confirmButton={ TextButton(onClick={onApply(color)}) { Text("Применить") } },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Отмена") } }
    )
}
