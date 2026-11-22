package com.example.potionsanddungeons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp

internal val LocalAvatars = compositionLocalOf<Map<String, String>> { emptyMap() }

private data class AvatarCrop(
    val uri: String,
    val zoom: Float = 1f,
    val x: Float = 0.5f,
    val y: Float = 0.5f
) {
    fun encode() = "crop:$zoom:$x:$y:$uri"

    companion object {
        fun decode(value: String): AvatarCrop {
            if (!value.startsWith("crop:")) return AvatarCrop(value)
            val parts = value.split(':', limit = 5)
            return AvatarCrop(parts[4], parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
        }
    }

    fun constrained(bitmap: ImageBitmap): AvatarCrop {
        val halfSide = min(bitmap.width, bitmap.height) / (2f * zoom)
        val horizontal = halfSide / bitmap.width
        val vertical = halfSide / bitmap.height
        return copy(x = x.coerceIn(horizontal, 1f - horizontal), y = y.coerceIn(vertical, 1f - vertical))
    }
}

@Composable
private fun CroppedAvatar(bitmap: ImageBitmap, crop: AvatarCrop, modifier: Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        val bounded = crop.constrained(bitmap)
        val side = (min(bitmap.width, bitmap.height) / bounded.zoom).roundToInt().coerceAtLeast(1)
        val left = (bounded.x * bitmap.width - side / 2f).roundToInt().coerceIn(0, bitmap.width - side)
        val top = (bounded.y * bitmap.height - side / 2f).roundToInt().coerceIn(0, bitmap.height - side)
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(side, side),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        )
    }
}

@Composable
internal fun AvatarImage(key: String, modifier: Modifier) {
    val saved = LocalAvatars.current[key] ?: return
    val crop = remember(saved) { AvatarCrop.decode(saved) }
    val bitmap by loadAvatar(crop.uri, 768)
    bitmap?.let { CroppedAvatar(it, crop, modifier) }
}

@Composable
private fun AvatarCropDialog(
    initial: AvatarCrop,
    onDismiss: () -> Unit,
    onSave: (AvatarCrop) -> Unit
) {
    val bitmap by loadAvatar(initial.uri, 2048)
    var zoom by rememberSaveable(initial.uri) { mutableFloatStateOf(initial.zoom) }
    var x by rememberSaveable(initial.uri) { mutableFloatStateOf(initial.x) }
    var y by rememberSaveable(initial.uri) { mutableFloatStateOf(initial.y) }

    fun updateCrop(crop: AvatarCrop) {
        zoom = crop.zoom
        x = crop.x
        y = crop.y
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Кадрирование фото") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val image = bitmap
                if (image == null) {
                    Text("Если фото не появится, выбери другой файл.")
                } else {
                    CroppedAvatar(image, AvatarCrop(initial.uri, zoom, x, y),
                        Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f).pointerInput(image) {
                            detectTransformGestures { centroid, pan, scale, _ ->
                                val width = size.width.toFloat()
                                val nextZoom = (zoom * scale).coerceIn(1f, 6f)
                                val side = min(image.width, image.height).toFloat()
                                val oldScale = side / (zoom * width)
                                val newScale = side / (nextZoom * width)
                                val anchor = centroid - androidx.compose.ui.geometry.Offset(width / 2f, width / 2f)
                                updateCrop(
                                    AvatarCrop(
                                        initial.uri, nextZoom,
                                        x + (anchor.x * (oldScale - newScale) - pan.x * newScale) / image.width,
                                        y + (anchor.y * (oldScale - newScale) - pan.y * newScale) / image.height
                                    ).constrained(image)
                                )
                            }
                        }
                    )
                    Text("Перетащи фото или измени масштаб")
                    Slider(
                        value = zoom,
                        onValueChange = { updateCrop(AvatarCrop(initial.uri, it, x, y).constrained(image)) },
                        valueRange = 1f..6f
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bitmap != null,
                onClick = { bitmap?.let { onSave(AvatarCrop(initial.uri, zoom, x, y).constrained(it)) } }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
@Composable
internal fun AvatarSettings(onDismiss: () -> Unit, colors: DiceColors, onColorsChanged: (DiceColors) -> Unit, onChanged: (String, String?) -> Unit) {
    BackHandler(onBack = onDismiss)
    var section by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf("player0") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCrop by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberAvatarPicker { path -> pendingCrop = AvatarCrop(path).encode() }
    val retainPhoto = rememberAvatarPermission()
    pendingCrop?.let { saved ->
        AvatarCropDialog(AvatarCrop.decode(saved), onDismiss = { pendingCrop = null }) { crop ->
            runCatching {
                retainPhoto(crop.uri)
                onChanged(selected, crop.encode())
                error = null
            }.onFailure { error = "Не удалось сохранить фото. Выбери его ещё раз." }
            pendingCrop = null
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        TextButton(onClick=onDismiss) { Text("Назад") }
        Text("Оформление",fontSize=AppFonts.title,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FilterChip(selected=section==0,onClick={section=0},label={Text("Аватарки")})
            FilterChip(selected=section==1,onClick={section=1},label={Text("Кубики")})
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (section==0) {
            (0..4).forEach { index ->
                val key = if (index == 4) "boss" else "player$index"
                Row(Modifier.fillMaxWidth().background(AppColors.panelBg,RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).background(if(index==4) AppColors.bossAttackZone else playerPalette[index],CircleShape),
                        contentAlignment=Alignment.Center) {
                        Text(if(index==4) "👹" else "${index+1}")
                        AvatarImage(key, Modifier.fillMaxSize())
                    }
                    val saved = LocalAvatars.current[key]
                    Column(Modifier.weight(1f)) {
                        Text(if (index == 4) "Босс" else "Игрок ${index+1}",fontWeight=FontWeight.Bold)
                        TextButton(onClick = { selected = key; pickPhoto() }) { Text("Выбрать фото") }
                        if (saved != null) {
                            TextButton(onClick = { selected = key; pendingCrop = saved }) { Text("Настроить кадр") }
                        }
                    }
                    if (saved != null) TextButton(onClick = { onChanged(key, null) }) { Text("Сбросить") }
                }
            }
            error?.let { Text(it) }
            } else {
            DiceColorSettings(colors, onColorsChanged)
            }
        }
        Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)) { Text("Готово") }
    }
}
