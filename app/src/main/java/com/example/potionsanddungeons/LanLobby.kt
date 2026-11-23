package com.example.potionsanddungeons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class NetworkMode(val title: String) {
    WIFI("Wi‑Fi"), VPN("VPN"), INTERNET("Интернет")
}

@Composable
internal fun LanLobby(room: LanRoom, customCards: List<CustomCard>, onBack: () -> Unit) {
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

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text("Сетевая игра", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
        if (room.connected) {
            if (room.hosting) {
                if (!online) Text("Адрес комнаты: ${room.hostAddress}")
                Text("Код: ${room.roomCode}", fontWeight = FontWeight.Bold)
                if (online) {
                    Text("Передай код друзьям. Можно играть из разных сетей.")
                    if (defaultRelayEndpoint.isBlank()) Text("Сервер: ${endpoint.trim()}")
                } else Text(if (vpn) "Передай VPN-адрес и код друзьям в той же VPN-сети."
                    else "Передай адрес и код игрокам в той же сети.")
            }
            Text("Игроки (${room.names.size}/4)", fontWeight = FontWeight.Bold)
            room.names.forEachIndexed { index, player ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(32.dp).background(playerPalette[index % playerPalette.size], CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(player.take(1), color = AppColors.darkTextOnAccent)
                        room.avatars[index]?.let { LanAvatarImage(it, Modifier.fillMaxSize()) }
                    }
                    Text(if (index == room.seat) "$player (ты)" else player)
                }
            }
            if (room.hosting) {
                CustomRuleSwitch("Дополнительные зелья (DLC)", room.dlcEnabled, room::configureDlc)
                BoardOptionsEditor(room.boardOptions, customCards, room::configureBoard)
                Button(onClick = room::start, enabled = room.names.size >= 2 && !room.paused,
                    modifier = Modifier.fillMaxWidth()) { Text("Начать сетевую игру") }
            } else {
                Text(if (room.dlcEnabled) "Дополнительные зелья (DLC) включены" else "Дополнительные зелья (DLC) выключены")
                val field = room.boardOptions
                Text("Поле ${field.size}×${field.size} · ${field.potionPlacement.label}")
                if (field.customCards.isNotEmpty()) Text("Карт ведущего в колоде: ${field.customCards.size}")
                Text("Ждём, когда ведущий начнёт игру.")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NetworkMode.entries.forEach { option ->
                    FilterChip(selected = mode == option,
                        onClick = { mode = option }, enabled = !room.busy,
                        label = { Text(option.title) })
                }
            }
            Text(when (mode) {
                NetworkMode.WIFI -> "Подключите 2–4 телефона к одной сети Wi‑Fi."
                NetworkMode.VPN -> "Подключитесь к одной VPN-сети, например Tailscale."
                NetworkMode.INTERNET -> "Онлайн-комната для 2–4 игроков."
            })
            if (online && defaultRelayEndpoint.isBlank()) {
                OutlinedTextField(endpoint, { endpoint = it.take(253) }, label = { Text("Сервер (домен:порт)") },
                    enabled = !room.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("Твоё имя") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            LanAvatarPicker(savedAvatar, avatar, enabled = !room.busy) { value ->
                preferences.put(mapOf("crop" to value))
                savedAvatar = value
            }
            if (vpn) {
                OutlinedTextField(vpnAddress, { vpnAddress = it.take(15) },
                    label = { Text("Твой VPN IPv4 (для создания)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = {
                if (online) room.hostOnline(endpoint, name, avatar)
                else network.request { room.host(name, vpnAddress.takeIf { vpn }, avatar) }
            }, enabled = canEnterRoom && (!online || endpoint.isNotBlank()) && (!vpn || vpnAddress.isNotBlank()),
                modifier = Modifier.fillMaxWidth()) { Text("Создать комнату") }
            if (!online) {
                OutlinedTextField(address, { address = it.take(15) }, label = { Text(if (vpn) "VPN IPv4 ведущего" else "IP-адрес ведущего") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Код комнаты") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                if (online) room.joinOnline(endpoint, code, name, avatar)
                else network.request { room.join(address, code, name, viaVpn = vpn, avatar = avatar) }
            }, enabled = canEnterRoom && code.length == 6 && (if (online) endpoint else address).isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Подключиться") }
        }
        if (!online && network.denied) Text("Для сетевой игры разреши доступ к локальной сети в настройках Android.")
        if (room.status.isNotEmpty()) Text(room.status, color = AppColors.textMuted)
    }
}
