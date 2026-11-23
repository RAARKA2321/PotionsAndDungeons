package com.example.potionsanddungeons

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import javax.imageio.ImageIO

internal const val defaultRelayEndpoint = ""
internal const val supportsMovementKeys = true
private val gameDirectory = File(
    System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
    "PotionsAndDungeons"
)
private val LocalBackActions = staticCompositionLocalOf<MutableList<() -> Unit>> { error("Missing window") }
private val LocalMovementAction = staticCompositionLocalOf<MutableState<((Direction) -> Boolean)?>?> { null }

fun main() = application {
    val session = remember { GameSession(FilePreferences("saved_match")) }
    val backActions = remember { mutableListOf<() -> Unit>() }
    val movement = remember { mutableStateOf<((Direction) -> Boolean)?>(null) }
    DisposableEffect(session) { onDispose(session::closeLanRoom) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Potions and Dungeons",
        state = rememberWindowState(width = 900.dp, height = 820.dp),
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyUp) return@Window false
            if (event.key == Key.Escape) {
                val action = backActions.lastOrNull() ?: return@Window false
                action()
                return@Window true
            }
            if (event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) return@Window false
            val direction = when (event.key) {
                Key.W -> Direction.UP
                Key.A -> Direction.LEFT
                Key.S -> Direction.DOWN
                Key.D -> Direction.RIGHT
                else -> return@Window false
            }
            movement.value?.invoke(direction) == true
        }
    ) {
        CompositionLocalProvider(LocalBackActions provides backActions, LocalMovementAction provides movement) {
            GameApp(session)
        }
    }
}

private class FilePreferences(name: String) : GamePreferences {
    private val path = gameDirectory.toPath().resolve("$name.properties")
    private val properties = Properties().apply {
        if (Files.isRegularFile(path)) Files.newBufferedReader(path).use { load(it) }
    }

    override fun values() = properties.stringPropertyNames().associateWith(properties::getProperty)

    override fun put(changes: Map<String, Any?>) {
        changes.forEach { (key, value) ->
            if (value == null) properties.remove(key) else properties.setProperty(key, value.toString())
        }
        Files.createDirectories(path.parent)
        val pending = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        try {
            Files.newBufferedWriter(pending).use { properties.store(it, null) }
            try {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }
}

@Composable
internal fun rememberGamePreferences(name: String): GamePreferences = remember(name) { FilePreferences(name) }

@Composable
internal fun appVersion() = remember { System.getProperty("potions.version", "dev") }

@Composable
internal fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val actions = LocalBackActions.current
    val current by rememberUpdatedState(onBack)
    DisposableEffect(actions, enabled) {
        val action = { current() }
        if (enabled) actions.add(action)
        onDispose { actions.remove(action) }
    }
}

@Composable
internal fun MovementKeyHandler(onMove: (Direction) -> Boolean) {
    val action = LocalMovementAction.current ?: return
    val current by rememberUpdatedState(onMove)
    DisposableEffect(action) {
        val handler: (Direction) -> Boolean = { current(it) }
        action.value = handler
        onDispose { if (action.value === handler) action.value = null }
    }
}

@Composable
internal fun rememberNetworkAccess() = remember { NetworkAccess(false) { it() } }

@Composable
internal fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit = {
    val owner = Frame.getFrames().firstOrNull { it.isActive }
    val dialog = FileDialog(owner, "Выбрать фото", FileDialog.LOAD)
    try {
        dialog.isVisible = true
        dialog.file?.let { onPicked(File(dialog.directory, it).absolutePath) }
    } finally {
        dialog.dispose()
    }
}

@Composable
internal fun rememberAvatarPermission(): (String) -> Unit = { }

internal fun lanAvatarBitmap(pixels: IntArray): ImageBitmap {
    val side = LAN_AVATAR_SIZE
    return BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, side, side, pixels, 0, side)
    }.toComposeImageBitmap()
}

@Composable
internal fun loadAvatar(uri: String, maxDimension: Int): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, uri, maxDimension) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val image = ImageIO.read(File(uri)) ?: return@runCatching null
                val scale = maxDimension.toDouble() / maxOf(image.width, image.height)
                if (scale >= 1.0) return@runCatching image.toComposeImageBitmap()
                val resized = BufferedImage(
                    (image.width * scale).toInt().coerceAtLeast(1),
                    (image.height * scale).toInt().coerceAtLeast(1),
                    BufferedImage.TYPE_INT_ARGB
                )
                val graphics = resized.createGraphics()
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    graphics.drawImage(image, 0, 0, resized.width, resized.height, null)
                } finally {
                    graphics.dispose()
                }
                resized.toComposeImageBitmap()
            }.getOrNull()
        }
    }

internal fun rgbToHsv(red: Int, green: Int, blue: Int): FloatArray =
    java.awt.Color.RGBtoHSB(red, green, blue, null).also { it[0] *= 360f }
