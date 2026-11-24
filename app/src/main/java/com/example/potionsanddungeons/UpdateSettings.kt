package com.example.potionsanddungeons

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class UpdatePlatform(
    val target: UpdateTarget,
    val cache: File,
    val canInstall: Boolean,
    val prepare: (GameUpdate, File) -> File,
    val install: (File) -> Unit,
    val openReleases: () -> Unit
)

private sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val update: GameUpdate) : UpdateStatus
    data class Downloading(val progress: Int) : UpdateStatus
    data class Ready(val update: GameUpdate, val file: File) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

internal class UpdateController(
    val version: String, val platform: UpdatePlatform, private val preferences: GamePreferences,
    private val scope: CoroutineScope
) {
    private var state by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
    var automatic by mutableStateOf(preferences.values()["automatic"].toBoolean())
        private set
    var installError by mutableStateOf(false)
        private set
    var preferenceError by mutableStateOf(false)
        private set
    private val ready get() = state as? UpdateStatus.Ready
    val busy get() = state == UpdateStatus.Checking || state is UpdateStatus.Downloading
    val canDownload get() = state is UpdateStatus.Available && platform.canInstall
    val canInstall get() = ready != null
    val hasUpdate get() = state is UpdateStatus.Available || state is UpdateStatus.Ready
    val statusText: String? get() = when (val current = state) {
        UpdateStatus.Idle -> null
        UpdateStatus.Checking -> "Проверяю наличие обновлений…"
        UpdateStatus.Current -> "У тебя последняя версия."
        is UpdateStatus.Available -> "Доступна версия ${current.update.version}."
        is UpdateStatus.Downloading -> if (current.progress < 100) "Скачиваю обновление · ${current.progress}%"
            else "Проверяю скачанное обновление…"
        is UpdateStatus.Ready -> if (platform.target == UpdateTarget.WINDOWS)
            "Версия ${current.update.version} готова. Она запустится при следующем открытии игры."
            else "APK версии ${current.update.version} скачан. Подтверди установку в Android."
        is UpdateStatus.Error -> current.message
    }

    fun check() {
        if (busy || ready != null) return
        state = UpdateStatus.Checking
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) { checkGameUpdate(version, platform.target) }
                state = if (update == null) UpdateStatus.Current else UpdateStatus.Available(update)
                if (automatic) download()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = UpdateStatus.Error("Не удалось проверить обновления. Попробуй ещё раз или открой GitHub.")
            }
        }
    }

    fun changeAutomatic(enabled: Boolean) {
        try {
            preferences.put(mapOf("automatic" to enabled))
            automatic = enabled
            preferenceError = false
            if (enabled) download()
        } catch (_: Exception) {
            preferenceError = true
        }
    }

    fun download() {
        val update = (state as? UpdateStatus.Available)?.update ?: return
        if (!platform.canInstall) return
        state = UpdateStatus.Downloading(0)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val downloaded = downloadGameUpdate(update, platform.cache) { progress ->
                        state = UpdateStatus.Downloading(progress)
                    }
                    platform.prepare(update, downloaded)
                }
                state = UpdateStatus.Ready(update, file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = UpdateStatus.Error("Не удалось скачать или подготовить обновление. Попробуй ещё раз.")
            }
        }
    }

    fun install() {
        val file = ready?.file ?: return
        installError = runCatching { platform.install(file) }.isFailure
    }
}

internal val LocalUpdates = staticCompositionLocalOf<UpdateController?> { null }

@Composable
internal fun rememberUpdateController(): UpdateController {
    val prefs = rememberGamePreferences("updates")
    val scope = rememberCoroutineScope()
    val platform = rememberUpdatePlatform()
    val version = appVersion()
    return remember(version, platform, prefs, scope) { UpdateController(version, platform, prefs, scope) }
}

@Composable
internal fun UpdateSettings(controller: UpdateController) {
    MenuPanel {
        Text("Текущая версия · ${controller.version}")
        if (controller.platform.canInstall) {
            CustomRuleSwitch("Скачивать обновления автоматически", controller.automatic, controller::changeAutomatic)
        }
        controller.statusText?.let { Text(it, color = AppColors.textMuted) }
        if (controller.preferenceError) Text("Не удалось сохранить настройку автообновления.", color = AppColors.accent)
        if (controller.installError) Text("Не удалось запустить установку. Попробуй ещё раз.", color = AppColors.accent)
        if (controller.canInstall) {
            Button(onClick = controller::install, modifier = Modifier.fillMaxWidth()) {
                Text(if (controller.platform.target == UpdateTarget.WINDOWS) "Перезапустить и обновить" else "Установить обновление")
            }
        } else if (controller.canDownload) {
            Button(onClick = controller::download, modifier = Modifier.fillMaxWidth()) { Text("Скачать обновление") }
        } else {
            OutlinedButton(onClick = controller::check, enabled = !controller.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Проверить обновления") }
        }
        TextButton(onClick = { runCatching { controller.platform.openReleases() } }) { Text("Открыть GitHub Releases") }
    }
}
