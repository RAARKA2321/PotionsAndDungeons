package com.example.potionsanddungeons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

@Composable
internal fun PartyPresetControls(
    library: PartyPresetLibrary, dlc: Boolean, rules: CustomRules, onApply: (PartyPreset) -> Unit,
    network: Boolean = false
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val available = if (network) library.presets.filter { it.canUseInNetwork } else library.presets

    OutlinedButton(onClick = { name = ""; saveFailed = false; naming = true },
        enabled = rules.board.deckProblem(dlc, rules.choicePotions, rules.bossDuel) == null,
        modifier = Modifier.fillMaxWidth()) {
        Text("Сохранить пресет")
    }
    if (available.isNotEmpty()) MenuLink("Мои пресеты", onClick = { choosing = true })

    if (naming) {
        val trimmed = name.trim()
        val duplicate = library.presets.any { it.name.equals(trimmed, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Название пресета") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(PartyPreset.MAX_NAME); saveFailed = false },
                        singleLine = true, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    if (duplicate) Text("Пресет с таким названием уже есть.", color = AppColors.accent)
                    if (saveFailed) Text("Не удалось сохранить пресет.", color = AppColors.accent)
                }
            },
            confirmButton = {
                TextButton(enabled = trimmed.isNotEmpty() && !duplicate, onClick = {
                    runCatching { library.save(PartyPreset(trimmed, dlc, rules)) }
                        .onSuccess { naming = false }
                        .onFailure { saveFailed = true }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Отмена") } }
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Мои пресеты") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    available.forEach { preset ->
                        MenuLink(preset.name, onClick = { choosing = false; onApply(preset) },
                            detail = "${preset.rules.board.size}×${preset.rules.board.size}" +
                                if (preset.dlc) " · DLC" else "")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Закрыть") } }
        )
    }
}
