package com.example.potionsanddungeons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MatchRecapScreen(engine: GameEngine, onBack: () -> Unit) {
    MenuPage("Разбор партии", onBack) {
        MenuPanel {
            Text(engine.statusText, color = AppColors.accent, fontWeight = FontWeight.Bold, fontSize = AppFonts.title)
        }
        if (engine.eliminations.isNotEmpty()) {
            Text("Как выбыли игроки", fontSize = AppFonts.sectionLabel, fontWeight = FontWeight.Bold)
            engine.eliminations.forEach { elimination ->
                MenuPanel(elimination.player.name) {
                    val cell = elimination.cell.recapCoordinates()
                    when (val cause = elimination.cause) {
                        is EliminationCause.Boss -> {
                            val attack = cause.attackFrom.recapCoordinates()
                            val approach = if (cause.from == cause.attackFrom) "" else " после шага из ${cause.from.recapCoordinates()}"
                            val radius = if (cause.radius == 0) "" else " Радиус атаки — ${cause.radius} кл."
                            Text("${cause.name} настиг игрока на клетке $cell. Атака с клетки $attack$approach.$radius")
                        }
                        is EliminationCause.Werewolf -> Text("${cause.name} в облике оборотня атаковал на клетке $cell.")
                    }
                    val details = buildList {
                        elimination.lastTurnCell?.let { add("Конец предыдущего хода: ${it.recapCoordinates()}") }
                        elimination.lastPotion?.let { add("Последнее зелье: «$it»") }
                    }
                    details.forEach { Text(it, fontSize = AppFonts.hint, color = AppColors.textMuted) }
                }
            }
        }
        Text("Зелья и карты игроков", fontSize = AppFonts.sectionLabel, fontWeight = FontWeight.Bold)
        engine.players.forEachIndexed { index, player ->
            val recap = engine.recapFor(player)
            MenuPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).background(playerPalette[index], CircleShape))
                    Text(player.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(if (player.alive) "Выжил" else "Выбыл", fontSize = AppFonts.legend,
                        color = if (player.alive) AppColors.moveHighlight else AppColors.textMuted)
                }
                RecapCounts("Зелья", recap.potions)
                RecapCounts("Сыгранные карты", recap.playedCards.mapKeys { it.key.displayName() })
            }
        }
    }
}

@Composable
private fun RecapCounts(label: String, counts: Map<String, Int>) {
    Text("$label · ${counts.values.sum()}", fontSize = AppFonts.hint, fontWeight = FontWeight.Medium)
    if (counts.isNotEmpty()) {
        Text(counts.entries.joinToString(", ") { (name, count) -> if (count == 1) name else "$name ×$count" },
            fontSize = AppFonts.hint, color = AppColors.textMuted)
    }
}

private fun Cell.recapCoordinates() = "(${row + 1}, ${col + 1})"
