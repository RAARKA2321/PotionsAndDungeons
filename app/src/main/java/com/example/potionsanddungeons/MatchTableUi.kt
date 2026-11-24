package com.example.potionsanddungeons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun MatchTableLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    board: @Composable () -> Unit,
    roster: @Composable (Boolean) -> Unit,
    controls: @Composable () -> Unit
) {
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 680.dp
        val sidebarWidth = 220.dp
        val gap = 16.dp
        val tableWidth = if (wide) maxWidth - sidebarWidth - gap else maxWidth
        val tableSide = minOf(tableWidth, (maxHeight * if (wide) 0.72f else 0.58f).coerceAtLeast(220.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            header()
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(tableSide)) { board() }
                        controls()
                    }
                    Column(
                        Modifier.width(sidebarWidth).height(tableSide)
                            .background(AppColors.panelBg, RoundedCornerShape(18.dp))
                            .padding(12.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Игроки", color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        roster(true)
                    }
                }
            } else {
                roster(false)
                Box(Modifier.fillMaxWidth().height(tableSide)) { board() }
                controls()
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MatchRoster(
    engine: GameEngine, avatars: Map<Int, LanAvatar>?, detailed: Boolean, onPlayerClick: (Player) -> Unit
) {
    val current = engine.turnOrder.getOrNull(engine.currentTurnIndex).takeUnless { engine.awaitingBossMove || engine.gameOver }
    val choice = engine.pendingChoice as? PendingChoice.ChoosePlayer
    val entry: @Composable (Int, Player, Dp) -> Unit = { index, player, width ->
        val targeted = choice?.allowed?.invoke(player) == true
        val active = player === current
        val color by animateColorAsState(if (targeted) AppColors.accent else if (active) AppColors.moveHighlight else Color.Transparent)
        val shape = RoundedCornerShape(12.dp)
        Column(
            (if (detailed) Modifier.fillMaxWidth() else Modifier.width(width))
                .background(AppColors.boardBg, shape).border(1.dp, color, shape)
                .clickable(enabled = choice == null || targeted) { onPlayerClick(player) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = if (detailed) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val name = if (player.isBot) "${player.name} · бот" else player.name
            val portrait: @Composable () -> Unit = {
                Box(Modifier.size(28.dp).background(playerPalette[index], CircleShape), contentAlignment = Alignment.Center) {
                    Text(player.name.take(1), color = AppColors.darkTextOnAccent, fontWeight = FontWeight.Bold)
                    if (avatars == null) AvatarImage("player$index", Modifier.fillMaxSize())
                    else avatars[index]?.let { LanAvatarImage(it, Modifier.fillMaxSize()) }
                }
            }
            if (detailed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    portrait()
                    Text(name, color = if (player.alive) AppColors.textPrimary else AppColors.textMuted,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayerEffectSummary(player)
            } else {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(Modifier.padding(horizontal = 5.dp)) { portrait() }
                    val count = player.effects.size + if (player.stunnedTurns > 0 || player.skipNextTurn) 1 else 0
                    if (count > 0 && player.alive) {
                        Text("$count", color = AppColors.darkTextOnAccent, fontSize = AppFonts.chipCounter,
                            fontWeight = FontWeight.Bold, modifier = Modifier.background(AppColors.accent, CircleShape)
                                .padding(horizontal = 4.dp).semantics { contentDescription = "Эффектов: $count" })
                    }
                }
                Text(if (player.alive) player.name else "${player.name} · выбыл",
                    color = if (player.alive) AppColors.textPrimary else AppColors.textMuted,
                    fontSize = AppFonts.chipCounter, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (detailed) engine.players.forEachIndexed { index, player -> entry(index, player, Dp.Unspecified) }
    else BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = engine.players.size.coerceAtLeast(1)
        val spacing = 6.dp
        val width = ((maxWidth - spacing * (count - 1)) / count).coerceIn(58.dp, 86.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            engine.players.forEachIndexed { index, player -> entry(index, player, width) }
        }
    }
}

@Composable
private fun PlayerEffectSummary(player: Player, showEmpty: Boolean = false) {
    if (!player.alive) {
        Text("Выбыл", color = AppColors.textMuted, fontSize = AppFonts.legend)
        return
    }
    if (player.stunnedTurns > 0 || player.skipNextTurn) {
        Text("Пропуск хода", color = AppColors.accent, fontSize = AppFonts.legend)
    }
    val effects = player.effects.groupingBy { effect ->
        effect.id to effect.turnsLeft.takeIf { effect.category == EffectCategory.TEMPORARY }
    }.eachCount()
    effects.forEach { (effect, count) ->
        val (id, turns) = effect
        val name = id.displayName() + if (count > 1) " ×$count" else ""
        Text(if (turns == null) name else "$name · ходов: $turns", color = AppColors.textMuted, fontSize = AppFonts.legend)
    }
    if (showEmpty && effects.isEmpty() && player.stunnedTurns == 0 && !player.skipNextTurn) {
        Text("Нет активных эффектов", color = AppColors.textMuted, fontSize = AppFonts.legend)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatchPlayerSheet(player: Player, onDismiss: () -> Unit, onOpenHand: (() -> Unit)?) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.panelBg) {
        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(player.name, fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
            PlayerEffectSummary(player, showEmpty = true)
            if (onOpenHand != null) {
                OutlinedButton(onClick = onOpenHand) { Text("Карты в руке (${player.activeCards.size})") }
            }
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    }
}
