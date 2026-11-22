package com.example.potionsanddungeons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

private const val PORT = 48473
private const val PROTOCOL = 1
private const val MAX_FRAME = 2 * 1024 * 1024

internal fun readLanFrame(input: DataInputStream): String {
    val size = input.readInt()
    if (size !in 1..MAX_FRAME) throw IOException("Invalid LAN frame size")
    val bytes = ByteArray(size)
    input.readFully(bytes)
    return bytes.toString(Charsets.UTF_8)
}

internal fun writeLanFrame(output: DataOutputStream, message: String) {
    val bytes = message.toByteArray(Charsets.UTF_8)
    if (bytes.size !in 1..MAX_FRAME) throw IOException("Invalid LAN frame size")
    output.writeInt(bytes.size)
    output.write(bytes)
    output.flush()
}

private class Connection(val socket: Socket) {
    val outgoing = Channel<String>(64)
    fun send(message: JSONObject) {
        if (outgoing.trySend(message.toString()).isFailure) close()
    }
    fun close() {
        outgoing.close()
        runCatching { socket.close() }
    }
}

internal data class LanRoll(val revision: Int, val value: Int, val sides: Int)

internal class LanRoom {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connections = mutableSetOf<Connection>()
    private data class Seat(val name: String, val token: String, var connection: Connection?)
    private val seats = mutableListOf<Seat>()
    private var server: ServerSocket? = null
    private var hostConnection: Connection? = null
    private var match: MatchRecording? = null
    private var revision = 0
    private var token = UUID.randomUUID().toString()
    private var address = ""
    private var joinName = ""
    private var joinCode = ""
    private var waitingForReply by mutableStateOf(false)
    private var driving: Job? = null
    private var closed = false

    var engine by mutableStateOf<GameEngine?>(null)
        private set
    var hosting by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var connected by mutableStateOf(false)
        private set
    var hostAddress by mutableStateOf("")
        private set
    var roomCode by mutableStateOf("")
        private set
    var names by mutableStateOf(emptyList<String>())
        private set
    var status by mutableStateOf("")
        private set
    var seat by mutableStateOf(-1)
        private set
    var paused by mutableStateOf(false)
        private set
    var lastRoll by mutableStateOf<LanRoll?>(null)
        private set

    val canAct: Boolean
        get() = connected && !paused && !waitingForReply &&
            engine?.let { game -> game.turnOrder.getOrNull(game.currentTurnIndex) === game.players.getOrNull(seat) } == true

    fun host(name: String) {
        if (busy || connected) return
        hosting = true
        busy = true
        status = "Создаю комнату…"
        scope.launch {
            try {
                val listener = withContext(Dispatchers.IO) {
                    val ip = findLocalAddress() ?: throw IOException("Нет адреса в локальной сети")
                    ServerSocket().apply {
                        try {
                            reuseAddress = true
                            bind(InetSocketAddress(ip, PORT))
                        } catch (e: IOException) {
                            close()
                            throw e
                        }
                    }
                }
                server = listener
                hostAddress = listener.inetAddress.hostAddress.orEmpty()
                roomCode = String.format(Locale.ROOT, "%06d", SecureRandom().nextInt(1_000_000))
                seat = 0
                seats.add(Seat(cleanName(name, 0), token, null))
                names = seats.map { it.name }
                connected = true
                busy = false
                status = "Ожидаем игроков"
                while (isActive) {
                    val socket = withContext(Dispatchers.IO) { listener.accept() }
                    if (connections.size >= 8) socket.close()
                    else attach(Connection(socket))
                }
            } catch (_: IOException) {
                if (!closed) {
                    if (server == null) hosting = false
                    connected = false
                    busy = false
                    paused = engine != null
                    status = "Не удалось открыть комнату. Проверь подключение к Wi‑Fi."
                }
            }
        }
    }

    fun join(host: String, code: String, name: String) {
        if (busy || connected || hosting) return
        address = host.trim()
        joinCode = code.trim()
        joinName = name
        connect()
    }

    fun reconnect() {
        if (!hosting && !connected && !busy && address.isNotEmpty()) connect()
    }

    private fun connect() {
        busy = true
        status = "Подключаюсь…"
        scope.launch {
            val socket = Socket()
            try {
                withContext(Dispatchers.IO) {
                    val ip = parseLocalAddress(address) ?: throw IOException("Нужен локальный IPv4-адрес")
                    socket.connect(InetSocketAddress(ip, PORT), 4500)
                }
                val connection = Connection(socket)
                hostConnection = connection
                attach(connection)
                connection.send(JSONObject().put("type", "join").put("protocol", PROTOCOL)
                    .put("code", joinCode).put("token", token).put("name", joinName))
            } catch (_: IOException) {
                runCatching { socket.close() }
                busy = false
                status = "Не удалось подключиться. Проверь адрес и общую Wi‑Fi сеть."
            } finally {
                if (closed) runCatching { socket.close() }
            }
        }
    }

    private fun attach(connection: Connection) {
        connections.add(connection)
        scope.launch(Dispatchers.IO) {
            try {
                val output = DataOutputStream(BufferedOutputStream(connection.socket.getOutputStream()))
                for (message in connection.outgoing) writeLanFrame(output, message)
            } catch (_: IOException) {
                connection.close()
            }
        }
        scope.launch(Dispatchers.IO) {
            delay(3000)
            while (isActive && !connection.socket.isClosed) {
                connection.send(JSONObject().put("type", "ping"))
                delay(3000)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                connection.socket.soTimeout = 12000
                connection.socket.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(connection.socket.getInputStream()))
                while (isActive) {
                    val message = JSONObject(readLanFrame(input))
                    withContext(Dispatchers.Main.immediate) { receive(connection, message) }
                }
            } catch (_: Exception) {
                // A malformed frame or a disconnected peer ends this connection.
            } finally {
                connection.close()
                scope.launch { disconnected(connection) }
            }
        }
        scope.launch {
            delay(6000)
            if (hosting && seats.none { it.connection === connection }) connection.close()
            if (!hosting && hostConnection === connection && !connected) connection.close()
        }
    }

    private fun receive(connection: Connection, message: JSONObject) {
        if (closed || message.optString("type") == "ping") return
        if (hosting) receiveOnHost(connection, message) else if (connection === hostConnection) receiveOnClient(message)
    }

    private fun receiveOnHost(connection: Connection, message: JSONObject) {
        val index = seats.indexOfFirst { it.connection === connection }
        when (message.optString("type")) {
            "join" -> {
                if (index >= 0) return
                val guestToken = message.optString("token")
                val old = seats.indexOfFirst { it.token == guestToken }
                val valid = message.optInt("protocol") == PROTOCOL && message.optString("code") == roomCode &&
                    guestToken.length == 36 && old != 0
                if (!valid || old < 0 && (match != null || seats.size >= 4)) {
                    connection.send(JSONObject().put("type", "rejected").put("reason", "Проверь код, версию игры и наличие свободного места."))
                    return
                }
                val slot = if (old >= 1) old else seats.size
                if (old >= 1) {
                    seats[old].connection?.close()
                    seats[old].connection = connection
                } else {
                    seats.add(Seat(cleanName(message.optString("name"), slot), guestToken, connection))
                }
                connection.send(JSONObject().put("type", "welcome").put("seat", slot))
                sendSnapshot(connection)
                broadcastRoom()
            }
            "act" -> {
                if (index < 1) return
                val action = MatchAction.entries.find { it.name == message.optString("action") }
                if (action == null || message.optInt("revision", -1) != revision ||
                    !allowed(index, action, message.optInt("first"), message.optInt("second"))
                ) {
                    connection.send(JSONObject().put("type", "retry"))
                    return
                }
                applyAndBroadcast(action, message.optInt("first"), message.optInt("second"))
            }
        }
    }

    private fun receiveOnClient(message: JSONObject) {
        when (message.optString("type")) {
            "welcome" -> {
                seat = message.getInt("seat")
                connected = true
                busy = false
                waitingForReply = false
            }
            "room" -> {
                val roster = message.getJSONArray("names")
                names = List(roster.length(), roster::getString)
                paused = message.getBoolean("paused")
                status = if (paused) "Партия на паузе. Ждём переподключения." else "Подключено"
            }
            "snapshot" -> {
                val recording = MatchRecording.restore(message.getJSONObject("match"))
                match = recording
                engine = recording.engine
                revision = message.getInt("revision")
                waitingForReply = false
                lastRoll = null
            }
            "action" -> {
                if (message.getInt("revision") != revision + 1) {
                    hostConnection?.close()
                    return
                }
                val recording = match ?: return
                recording.perform(MatchAction.valueOf(message.getString("action")), message.getInt("first"), message.getInt("second"))
                revision++
                waitingForReply = false
                val sides = message.optInt("sides")
                if (sides > 0) lastRoll = LanRoll(revision, recording.engine.lastDiceRoll, sides)
            }
            "retry" -> waitingForReply = false
            "rejected" -> {
                status = message.getString("reason")
                hostConnection?.close()
            }
        }
    }

    private fun cleanName(name: String, slot: Int): String {
        val base = name.trim().take(20).ifBlank { "Игрок ${slot + 1}" }
        return if (seats.none { it.name == base }) base else "$base (${slot + 1})"
    }

    private fun disconnected(connection: Connection) {
        connections.remove(connection)
        if (hosting) {
            val index = seats.indexOfFirst { it.connection === connection }
            if (index < 0) return
            seats[index].connection = null
            broadcastRoom()
        } else if (hostConnection === connection) {
            hostConnection = null
            busy = false
            connected = false
            waitingForReply = false
            paused = engine != null
            if (paused) status = "Связь с ведущим потеряна. Переподключись к комнате."
            else if (status == "Подключаюсь…" || status == "Подключено") status = "Соединение закрыто."
        }
    }

    private fun broadcastRoom() {
        names = seats.map { it.name }
        paused = seats.drop(1).any { it.connection == null }
        status = if (paused) "Ждём переподключения игрока" else "Комната открыта"
        broadcast(JSONObject().put("type", "room").put("names", JSONArray(names)).put("paused", paused))
        driveHost()
    }

    fun start() {
        if (!hosting || match != null || paused || seats.size !in 2..4) return
        val recording = MatchRecording(names, emptySet(), false)
        match = recording
        engine = recording.engine
        seats.mapNotNull { it.connection }.forEach(::sendSnapshot)
        broadcastRoom()
    }

    private fun sendSnapshot(connection: Connection) {
        match?.let {
            connection.send(JSONObject().put("type", "snapshot").put("revision", revision).put("match", it.snapshot()))
        }
    }

    private fun broadcast(message: JSONObject) {
        seats.mapNotNull { it.connection }.forEach { it.send(message) }
    }

    private fun allowed(index: Int, action: MatchAction, first: Int, second: Int): Boolean {
        val game = engine ?: return false
        if (!connected || paused || game.gameOver) return false
        if (action == MatchAction.D4) return index == 0 && game.d4Rolls.isNotEmpty()
        if (action == MatchAction.BOSS) return index == 0 && game.awaitingBossMove && !game.hasPendingAction
        val current = game.players.getOrNull(index) ?: return false
        if (!game.isCurrentPlayer(current) || game.awaitingBossMove) return false
        return when (action) {
            MatchAction.ROLL_INLINE -> game.pendingSteps == 0 && !game.hasPendingAction && !game.awaitingRollDecision
            MatchAction.REROLL -> game.canReroll
            MatchAction.ACCEPT -> game.awaitingRollDecision
            MatchAction.CELL -> game.cardReveal == null && game.isBoardTarget(Cell(first, second))
            MatchAction.CARD -> first == index && CardId.entries.getOrNull(second)?.let { game.canPlayActiveCard(current, it) } == true
            MatchAction.PLAYER -> game.players.getOrNull(first)?.let { (game.pendingChoice as? PendingChoice.ChoosePlayer)?.allowed?.invoke(it) } == true
            MatchAction.EFFECT -> (game.pendingChoice as? PendingChoice.ChooseEffect)?.from?.effects?.getOrNull(first) != null
            MatchAction.POTION -> game.cardReveal != null && first in 0..1
            else -> false
        }
    }

    fun perform(action: MatchAction, first: Int, second: Int): Int {
        if (hosting) return if (allowed(0, action, first, second)) applyAndBroadcast(action, first, second) else 0
        if (!canAct || action == MatchAction.D4 || action == MatchAction.BOSS) return 0
        val connection = hostConnection ?: return 0
        waitingForReply = true
        connection.send(JSONObject().put("type", "act").put("revision", revision)
            .put("action", action.name).put("first", first).put("second", second))
        return 0
    }

    private fun applyAndBroadcast(action: MatchAction, first: Int, second: Int): Int {
        val recording = checkNotNull(match)
        val game = recording.engine
        val player = game.turnOrder.getOrNull(game.currentTurnIndex)
        val sides = if (action == MatchAction.REROLL || action == MatchAction.ROLL_INLINE &&
            !game.mustSkipCurrentTurn() && player?.effects?.has(CardId.HORSE) != true
        ) game.diceSidesForCurrentPlayer() else 0
        val result = recording.perform(action, first, second)
        revision++
        broadcast(JSONObject().put("type", "action").put("revision", revision)
            .put("action", action.name).put("first", first).put("second", second).put("sides", sides))
        if (sides > 0) lastRoll = LanRoll(revision, game.lastDiceRoll, sides)
        driveHost()
        return result
    }

    private fun driveHost() {
        if (!hosting || paused || !connected || driving?.isActive == true) return
        val game = engine ?: return
        driving = scope.launch {
            while (!paused && !game.gameOver) {
                val action = when {
                    game.d4Rolls.isNotEmpty() -> MatchAction.D4
                    game.awaitingBossMove && !game.hasPendingAction -> MatchAction.BOSS
                    else -> break
                }
                val delayMillis = when {
                    action == MatchAction.D4 -> 1700L
                    lastRoll?.revision == revision -> 1700L
                    else -> 650L
                }
                delay(delayMillis)
                if (!allowed(0, action, 0, 0)) break
                applyAndBroadcast(action, 0, 0)
            }
        }
    }

    fun close() {
        closed = true
        runCatching { server?.close() }
        connections.toList().forEach(Connection::close)
        scope.cancel()
    }

    private fun findLocalAddress(): InetAddress? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return interfaces.filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { !it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it.address.size == 4 && it.isSiteLocalAddress }
    }

    private fun parseLocalAddress(host: String): InetAddress? {
        val parts = host.split('.').map { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it == null || it !in 0..255 }) return null
        return InetAddress.getByAddress(parts.map { checkNotNull(it).toByte() }.toByteArray())
            .takeIf { it.isSiteLocalAddress }
    }
}
