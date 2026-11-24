package com.example.potionsanddungeons

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal val defaultRelayEndpoint = BuildConfig.RELAY_ENDPOINT
internal const val supportsMovementKeys = false
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

internal fun androidPreferences(context: Context, name: String): GamePreferences {
    val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    return object : GamePreferences {
        override fun values() = preferences.all.mapNotNull { (key, value) ->
            value?.let { key to it.toString() }
        }.toMap()

        override fun put(changes: Map<String, Any?>) {
            preferences.edit().apply {
                changes.forEach { (key, value) ->
                    when (value) {
                        null -> remove(key)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }.apply()
        }
    }
}

internal fun MatchRecording.save(preferences: SharedPreferences) {
    preferences.edit().putString("match", snapshot().toString()).apply()
}

internal fun MatchRecording.Companion.read(preferences: SharedPreferences) =
    read(preferences.getString("match", null))

@Composable
internal fun rememberGamePreferences(name: String): GamePreferences {
    val context = LocalContext.current
    return remember(context, name) { androidPreferences(context, name) }
}

@Composable
internal fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}

@Composable
internal fun rememberUpdatePlatform(): UpdatePlatform {
    val context = LocalContext.current
    return remember(context) {
        UpdatePlatform(UpdateTarget.ANDROID, File(context.cacheDir, "updates"), true,
            prepare = { update, file -> verifyAndroidUpdate(context, update, file) },
            install = { file ->
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")))
                } else {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                    context.startActivity(Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            },
            openReleases = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GAME_RELEASES_URL))) }
        )
    }
}

@Suppress("DEPRECATION")
private fun verifyAndroidUpdate(context: Context, update: GameUpdate, file: File): File {
    val manager = context.packageManager
    val current = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    val candidate = checkNotNull(manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES))
    val signers = candidate.signatures?.toSet().orEmpty()
    require(candidate.packageName == context.packageName && candidate.versionName == update.version)
    require(PackageInfoCompat.getLongVersionCode(candidate) > PackageInfoCompat.getLongVersionCode(current))
    require(signers.isNotEmpty() && signers == current.signatures?.toSet())
    return file
}

@Composable
internal fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}

@Composable
internal fun MovementKeyHandler(onMove: (Direction) -> Boolean) = Unit

@Composable
internal fun rememberNetworkAccess(): NetworkAccess {
    val context = LocalContext.current
    var permissionMessage by remember { mutableStateOf(false) }
    var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionMessage = !granted
        if (granted) permissionAction?.invoke()
        permissionAction = null
    }
    return NetworkAccess(permissionMessage) { action ->
        if (Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            permissionAction = action
            permission.launch(LOCAL_NETWORK)
        }
    }
}

@Composable
internal fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri.toString())
    }
    return { picker.launch(arrayOf("image/*")) }
}

@Composable
internal fun rememberBoardFiles(
    onImport: (Result<BoardLayout>?) -> Unit, onExport: (Result<Unit>?) -> Unit
): BoardFileActions {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val importResult by rememberUpdatedState(onImport)
    val exportResult by rememberUpdatedState(onExport)
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) importResult(null)
        else scope.launch {
            importResult(withContext(Dispatchers.IO) {
                runCatching { checkNotNull(resolver.openInputStream(uri)).use { it.readBoardFile() } }
            })
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri == null) exportResult(null)
        else scope.launch {
            exportResult(withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = checkNotNull(content)
                    checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
                        it.write(encoded)
                    }
                }
            })
        }
    }
    return BoardFileActions(
        pick = { picker.launch(arrayOf("*/*")) },
        save = { layout ->
            pendingExport = layout.encodeFile()
            saver.launch(layout.fileName())
        }
    )
}

@Composable
internal fun rememberAvatarPermission(): (String) -> Unit {
    val resolver = LocalContext.current.contentResolver
    return { resolver.takePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

internal fun lanAvatarBitmap(pixels: IntArray): ImageBitmap =
    Bitmap.createBitmap(pixels, LAN_AVATAR_SIZE, LAN_AVATAR_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()

@Composable
internal fun loadAvatar(uri: String, maxDimension: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, uri, maxDimension) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val source = Uri.parse(uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val largest = maxOf(info.size.width, info.size.height)
                        if (largest > maxDimension) {
                            val scale = maxDimension.toFloat() / largest
                            decoder.setTargetSize(
                                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                                (info.size.height * scale).roundToInt().coerceAtLeast(1)
                            )
                        }
                    }.asImageBitmap()
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
                    val options = BitmapFactory.Options().apply {
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > maxDimension) {
                            inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                        }
                    }
                    resolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }
}

internal fun rgbToHsv(red: Int, green: Int, blue: Int): FloatArray =
    FloatArray(3).also { android.graphics.Color.RGBToHSV(red, green, blue, it) }
