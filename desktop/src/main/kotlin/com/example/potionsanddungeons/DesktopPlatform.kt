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
private val gameDirectory = File(
    System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
    "PotionsAndDungeons"
)
private val LocalBackActions = staticCompositionLocalOf<MutableList<() -> Unit>> { error("Missing window") }

fun main() = application {
    val session = remember { GameSession(FilePreferences("saved_match")) }
    val backActions = remember { mutableListOf<() -> Unit>() }
    DisposableEffect(session) { onDispose(session::closeLanRoom) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Potions and Dungeons",
        state = rememberWindowState(width = 900.dp, height = 820.dp),
        onPreviewKeyEvent = { event ->
            val action = backActions.lastOrNull()
            if (event.key == Key.Escape && event.type == KeyEventType.KeyUp && action != null) {
                action()
                true
            } else false
        }
    ) {
        CompositionLocalProvider(LocalBackActions provides backActions) { GameApp(session) }
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
