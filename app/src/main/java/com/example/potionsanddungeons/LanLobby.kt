package com.example.potionsanddungeons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class NetworkMode(val title: String) {
    WIFI("Wi‑Fi"), VPN("VPN"), INTERNET("Интернет")
}

@Composable
internal fun LanLobby(
    room: LanRoom, customCards: List<CustomCard>, layouts: List<BoardLayout>,
    presetLibrary: PartyPresetLibrary, onBack: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(NetworkMode.WIFI) }
    val online = mode == NetworkMode.INTERNET
    val vpn = mode == NetworkMode.VPN
    var endpoint by rememberSaveable { mutableStateOf(defaultRelayEndpoint) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var vpnAddress by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val network = rememberNetworkAccess()
    val preferences = rememberGamePreferences("online_avatar")
    var savedAvatar by remember { mutableStateOf(preferences.values()["crop"]) }
    val avatar = if (!room.connected) savedAvatar?.let { loadLanAvatar(it) } else null
    val canEnterRoom = !room.busy && (savedAvatar == null || avatar != null)
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    var joining by rememberSaveable { mutableStateOf(false) }
    var showDeckEditor by rememberSaveable { mutableStateOf(false) }
    val deckProblem = room.boardOptions.deckProblem(room.dlcEnabled)

    if (showDeckEditor && room.connected && room.hosting) {
        DeckEditorScreen(room.boardOptions, customCards, room.dlcEnabled,
            onApply = { room.configureBoard(it); showDeckEditor = false }, onBack = { showDeckEditor = false })
        return
    }

    MenuPage(if (room.connected) "Сетевая комната" else "Сетевая игра", onBack,
        scroll = !room.connected, footer = {
            if (room.connected && room.hosting) {
                if (room.paused) Text(room.status, color = AppColors.accent)
                else if (room.names.size < 2) Text("Ждём подключения игроков.", color = AppColors.textMuted)
                if (deckProblem != null) Text(deckProblem, color = AppColors.accent, fontSize = AppFonts.hint)
                Button(onClick = room::start, enabled = room.names.size >= 2 && !room.paused && deckProblem == null,
                    modifier = Modifier.fillMaxWidth()) { Text("Начать игру") }
            } else if (room.connected) {
                Text("Ждём, когда ведущий начнёт игру.")
            } else {
                if (room.status.isNotEmpty()) Text(room.status, color = AppColors.textMuted)
                Button(onClick = {
                    if (joining) {
                        if (online) room.joinOnline(endpoint, code, name, avatar)
                        else network.request { room.join(address, code, name, viaVpn = vpn, avatar = avatar) }
                    } else {
                        if (online) room.hostOnline(endpoint, name, avatar)
                        else network.request { room.host(name, vpnAddress.takeIf { vpn }, avatar) }
                    }
                }, enabled = canEnterRoom && if (joining) {
                    code.length == 6 && (if (online) endpoint else address).isNotBlank()
                } else {
                    (!online || endpoint.isNotBlank()) && (!vpn || vpnAddress.isNotBlank())
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (joining) "Подключиться" else "Создать комнату")
                }
            }
        }
    ) {
        if (room.connected) {
            MenuPanel("Код комнаты") {
                SelectionContainer {
                    Text(if (room.hosting) room.roomCode else code, color = AppColors.accent,
                        fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
                }
                if (room.hosting && (!online || defaultRelayEndpoint.isBlank())) {
                    SelectionContainer { Text(if (online) endpoint.trim() else room.hostAddress, color = AppColors.textMuted) }
                }
            }
            Text("Игроки · ${room.names.size}/4", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                room.names.forEachIndexed { index, player ->
                    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(34.dp).background(playerPalette[index], CircleShape), contentAlignment = Alignment.Center) {
                            Text(player.take(1), color = AppColors.darkTextOnAccent)
                            room.avatars[index]?.let { LanAvatarImage(it, Modifier.fillMaxSize()) }
                        }
                        Text(if (index == room.seat) "$player (ты)" else player, fontSize = AppFonts.legend,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val field = room.boardOptions
                BoardRulesSummary(field, room.dlcEnabled)
                if (room.hosting) {
                    OutlinedButton(onClick = { rulesExpanded = !rulesExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (rulesExpanded) "Правила партии ▴" else "Правила партии ▾")
                    }
                    if (rulesExpanded) {
                        CustomRuleSwitch("Дополнительные зелья (DLC)", room.dlcEnabled, room::configureDlc)
                        BoardOptionsEditor(field, customCards, layouts, room::configureBoard)
                        OutlinedButton(onClick = { showDeckEditor = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (field.deckRecipe == null) "Настроить колоду" else "Изменить колоду")
                        }
                        PartyPresetControls(presetLibrary, room.dlcEnabled, CustomRules(board = field),
                            onApply = { room.configureOptions(it.dlc, it.rules.board) }, network = true)
                    }
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !joining, onClick = { joining = false }, enabled = !room.busy, label = { Text("Создать") })
                FilterChip(selected = joining, onClick = { joining = true }, enabled = !room.busy, label = { Text("Подключиться") })
            }
            MenuPanel("Подключение") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NetworkMode.entries.forEach { option ->
                        FilterChip(selected = mode == option,
                            onClick = { mode = option }, enabled = !room.busy,
                            label = { Text(option.title) })
                    }
                }
                Text(when (mode) {
                    NetworkMode.WIFI -> "Подключитесь к одной сети Wi‑Fi."
                    NetworkMode.VPN -> "Подключитесь к одной VPN-сети."
                    NetworkMode.INTERNET -> "Можно играть из разных сетей."
                }, color = AppColors.textMuted)
                if (online && defaultRelayEndpoint.isBlank()) {
                    OutlinedTextField(endpoint, { endpoint = it.take(253) }, label = { Text("Сервер (домен:порт)") },
                        enabled = !room.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            MenuPanel("Игрок") {
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("Твоё имя") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                LanAvatarPicker(savedAvatar, avatar, enabled = !room.busy) { value ->
                    preferences.put(mapOf("crop" to value))
                    savedAvatar = value
                }
            }
            if (joining) {
                MenuPanel("Данные комнаты") {
                    if (!online) {
                        OutlinedTextField(address, { address = it.take(15) }, label = { Text(if (vpn) "VPN IPv4 ведущего" else "IP-адрес ведущего") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Код комнаты") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            } else {
                if (vpn) MenuPanel("Адрес комнаты") {
                    OutlinedTextField(vpnAddress, { vpnAddress = it.take(15) }, label = { Text("Твой VPN IPv4") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (!online && network.denied) Text("Для сетевой игры разреши доступ к локальной сети в настройках Android.")
    }
}
