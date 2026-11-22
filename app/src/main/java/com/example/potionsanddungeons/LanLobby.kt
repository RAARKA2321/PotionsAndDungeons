package com.example.potionsanddungeons

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
internal fun LanLobby(room: LanRoom, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var permissionMessage by remember { mutableStateOf(false) }
    var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionMessage = !granted
        if (granted) permissionAction?.invoke()
        permissionAction = null
    }
    fun withNetwork(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            permissionAction = action
            permission.launch(LOCAL_NETWORK)
        }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text("Игра по Wi‑Fi", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
        if (room.connected) {
            if (room.hosting) {
                Text("Адрес комнаты: ${room.hostAddress}")
                Text("Код: ${room.roomCode}", fontWeight = FontWeight.Bold)
                Text("Передай адрес и код игрокам в той же сети.")
            }
            Text("Игроки (${room.names.size}/4)", fontWeight = FontWeight.Bold)
            room.names.forEachIndexed { index, player ->
                Text(if (index == room.seat) "$player (ты)" else player)
            }
            if (room.hosting) {
                Button(onClick = room::start, enabled = room.names.size >= 2 && !room.paused,
                    modifier = Modifier.fillMaxWidth()) { Text("Начать сетевую игру") }
            } else Text("Ждём, когда ведущий начнёт игру.")
        } else {
            Text("Подключите 2–4 телефона к одной сети Wi‑Fi.")
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("Твоё имя") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { withNetwork { room.host(name) } }, enabled = !room.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Создать комнату") }
            OutlinedTextField(address, { address = it.take(15) }, label = { Text("IP-адрес ведущего") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Код комнаты") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { withNetwork { room.join(address, code, name) } },
                enabled = !room.busy && address.isNotBlank() && code.length == 6,
                modifier = Modifier.fillMaxWidth()) { Text("Подключиться") }
        }
        if (permissionMessage) Text("Для игры по Wi‑Fi разреши доступ к локальной сети в настройках Android.")
        if (room.status.isNotEmpty()) Text(room.status, color = AppColors.textMuted)
    }
}
