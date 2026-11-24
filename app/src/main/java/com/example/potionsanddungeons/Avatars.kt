package com.example.potionsanddungeons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
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
        val (offset, cropSize) = avatarBounds(bitmap, crop)
        drawImage(bitmap, srcOffset = offset, srcSize = cropSize,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

private fun avatarBounds(bitmap: ImageBitmap, crop: AvatarCrop): Pair<IntOffset, IntSize> {
    val bounded = crop.constrained(bitmap)
    val side = (min(bitmap.width, bitmap.height) / bounded.zoom).roundToInt().coerceAtLeast(1)
    val left = (bounded.x * bitmap.width - side / 2f).roundToInt().coerceIn(0, bitmap.width - side)
    val top = (bounded.y * bitmap.height - side / 2f).roundToInt().coerceIn(0, bitmap.height - side)
    return IntOffset(left, top) to IntSize(side, side)
}

@Composable
internal fun loadLanAvatar(saved: String): LanAvatar? = key(saved) {
    val crop = remember { AvatarCrop.decode(saved) }
    val bitmap by loadAvatar(crop.uri, 768)
    remember(bitmap) {
        bitmap?.let { source ->
            val (offset, cropSize) = avatarBounds(source, crop)
            val size = LAN_AVATAR_SIZE
            val thumbnail = ImageBitmap(size, size)
            androidx.compose.ui.graphics.Canvas(thumbnail).drawImageRect(
                source, offset, cropSize, IntOffset.Zero, IntSize(size, size), Paint()
            )
            val pixels = IntArray(size * size)
            thumbnail.readPixels(pixels)
            LanAvatar.fromPixels(pixels)
        }
    }
}

@Composable
internal fun LanAvatarImage(avatar: LanAvatar, modifier: Modifier) {
    val bitmap = remember(avatar) { lanAvatarBitmap(avatar.pixels()) }
    Image(bitmap, contentDescription = null, modifier = modifier.clip(CircleShape), contentScale = ContentScale.FillBounds)
}

@Composable
internal fun LanAvatarPicker(saved: String?, avatar: LanAvatar?, enabled: Boolean, onChanged: (String?) -> Unit) {
    var pendingCrop by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    val pick = rememberAvatarPicker { path -> pendingCrop = AvatarCrop(path).encode() }
    val retain = rememberAvatarPermission()
    pendingCrop?.let { value ->
        AvatarCropDialog(AvatarCrop.decode(value), onDismiss = { pendingCrop = null }) { crop ->
            error = runCatching { retain(crop.uri) }.isFailure
            if (!error) onChanged(crop.encode())
            pendingCrop = null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (avatar != null) LanAvatarImage(avatar, Modifier.size(36.dp))
        TextButton(onClick = pick, enabled = enabled) { Text(if (saved == null) "Выбрать аватарку" else "Сменить фото") }
        if (saved != null) TextButton(onClick = { onChanged(null); error = false }, enabled = enabled) { Text("Убрать") }
    }
    Text("Аватарку увидят участники комнаты.", color = AppColors.textMuted)
    if (saved != null && avatar == null) Text("Дождись предпросмотра или убери фото.")
    if (error) Text("Не удалось сохранить фото. Выбери его ещё раз.")
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
    val updates = LocalUpdates.current
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
    MenuPage("Настройки", onDismiss, scroll = false, footer = {
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
    }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = section == 0, onClick = { section = 0 }, label = { Text("Аватарки") })
            FilterChip(selected = section == 1, onClick = { section = 1 }, label = { Text("Кубики") })
            if (updates != null) {
                FilterChip(selected = section == 2, onClick = { section = 2 },
                    label = { Text(if (updates.hasUpdate) "Обновления · есть новое" else "Обновления") })
            }
        }
        key(section) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (section == 0) {
                    (0..4).forEach { index ->
                        val isBoss = index == 4
                        val avatarKey = if (isBoss) "boss" else "player$index"
                        MenuPanel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(52.dp).background(if (isBoss) AppColors.bossAttackZone else playerPalette[index], CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (isBoss) "👹" else "${index + 1}", color = AppColors.darkTextOnAccent)
                                    AvatarImage(avatarKey, Modifier.fillMaxSize())
                                }
                                val saved = LocalAvatars.current[avatarKey]
                                Column(Modifier.weight(1f)) {
                                    Text(if (isBoss) "Босс" else "Игрок ${index + 1}", fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { selected = avatarKey; pickPhoto() }) {
                                        Text(if (saved == null) "Выбрать фото" else "Сменить фото")
                                    }
                                    if (saved != null) {
                                        TextButton(onClick = { selected = avatarKey; pendingCrop = saved }) { Text("Настроить кадр") }
                                        TextButton(onClick = { onChanged(avatarKey, null) }) { Text("Сбросить") }
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = AppColors.accent) }
                } else if (section == 2 && updates != null) {
                    UpdateSettings(updates)
                } else {
                    DiceColorSettings(colors, onColorsChanged)
                }
            }
        }
    }
}
