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
private const val PROTOCOL = 2
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

internal class LanRoom(private val resolveIpv4: (String) -> InetAddress? = ::parseIpv4) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connections = mutableSetOf<Connection>()
    private data class Seat(val name: String, val token: String, var connection: Connection?, val avatar: LanAvatar?)
    private val seats = mutableListOf<Seat>()
    private var server: ServerSocket? = null
    private var relayControl: Socket? = null
    private var relay: InternetRelay? = null
    private var joiningViaVpn = false
    private var hostConnection: Connection? = null
    private var match: MatchRecording? = null
    private var revision = 0
    private var token = UUID.randomUUID().toString()
    private var address = ""
    private var joinName = ""
    private var joinCode = ""
    private var localAvatar: LanAvatar? = null
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
    var avatars by mutableStateOf(emptyMap<Int, LanAvatar>())
        private set
    var dlcEnabled by mutableStateOf(false)
        private set
    var boardOptions by mutableStateOf(BoardOptions())
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

    fun host(name: String, vpnAddress: String? = null, avatar: LanAvatar? = null) {
        if (busy || connected) return
        localAvatar = avatar
        hosting = true
        busy = true
        status = "Создаю комнату…"
        scope.launch {
            try {
                val vpnIp = vpnAddress?.let {
                    resolveIpv4(it.trim()) ?: throw IOException("Invalid VPN IPv4 address")
                }
                val listener = withContext(Dispatchers.IO) {
                    // Userspace VPNs can forward incoming traffic to loopback.
                    val bindAddress = if (vpnIp == null) {
                        findLocalAddress() ?: throw IOException("Нет адреса в локальной сети")
                    } else null
                    ServerSocket().apply {
                        try {
                            reuseAddress = true
                            bind(InetSocketAddress(bindAddress, PORT))
                        } catch (e: IOException) {
                            close()
                            throw e
                        }
                    }
                }
                server = listener
                hostAddress = (vpnIp ?: listener.inetAddress).hostAddress.orEmpty()
                openRoom(name, String.format(Locale.ROOT, "%06d", SecureRandom().nextInt(1_000_000)))
                while (isActive) {
                    val socket = withContext(Dispatchers.IO) { listener.accept() }
                    val acceptedAddress = vpnIp == null || socket.localAddress == vpnIp ||
                        socket.localAddress.isLoopbackAddress
                    if (!acceptedAddress || connections.size >= 8) socket.close()
                    else attach(Connection(socket))
                }
            } catch (_: IOException) {
                if (!closed) {
                    if (server == null) hosting = false
                    connected = false
                    busy = false
                    paused = engine != null
                    status = if (vpnAddress == null) "Не удалось открыть комнату. Проверь подключение к Wi‑Fi."
                    else "Не удалось открыть комнату. Включи VPN и проверь свой IPv4-адрес."
                }
            }
        }
    }

    fun hostOnline(endpoint: String, name: String, avatar: LanAvatar? = null) {
        if (busy || connected) return
        localAvatar = avatar
        hosting = true
        busy = true
        status = "Создаю онлайн-комнату…"
        val service = InternetRelay(endpoint.trim())
        scope.launch {
            var control: Socket? = null
            try {
                val reply = withContext(Dispatchers.IO) {
                    val socket = service.connect(JSONObject().put("role", "host"))
                    control = socket
                    service.read(socket).also { socket.soTimeout = 25_000 }
                }
                if (reply.optString("type") != "room") throw IOException("Relay rejected room")
                val socket = checkNotNull(control)
                relayControl = socket
                openRoom(name, reply.optString("code"))
                val key = reply.optString("key")
                while (isActive) {
                    val event = withContext(Dispatchers.IO) { service.read(socket) }
                    if (event.optString("type") == "peer" && connections.size < 8) {
                        attachOnlineGuest(service, key, event.optString("ticket"))
                    }
                }
            } catch (_: IOException) {
                if (!closed) {
                    if (engine == null) {
                        hosting = false
                        seats.clear()
                        names = emptyList()
                    }
                    connected = false
                    busy = false
                    paused = engine != null
                    status = "Связь с сервером потеряна. Проверь адрес сервера и подключение к интернету."
                }
            } finally {
                runCatching { control?.close() }
                relayControl = null
                connections.toList().forEach(Connection::close)
            }
        }
    }

    private suspend fun attachOnlineGuest(service: InternetRelay, key: String, ticket: String) {
        var peer: Socket? = null
        try {
            val socket = withContext(Dispatchers.IO) {
                service.connect(JSONObject().put("role", "peer").put("code", roomCode)
                    .put("key", key).put("ticket", ticket)).also { peer = it }
            }
            attach(Connection(socket))
            peer = null
        } catch (_: IOException) {
            // The guest can retry without closing the room.
        } finally {
            runCatching { peer?.close() }
        }
    }

    private fun openRoom(name: String, code: String) {
        roomCode = code
        seat = 0
        seats.add(Seat(cleanName(name, 0), token, null, localAvatar))
        names = seats.map { it.name }
        updateAvatars()
        connected = true
        busy = false
        status = "Ожидаем игроков"
    }

    fun join(host: String, code: String, name: String, viaVpn: Boolean = false, avatar: LanAvatar? = null) {
        if (busy || connected || hosting) return
        localAvatar = avatar
        relay = null
        joiningViaVpn = viaVpn
        address = host.trim()
        joinCode = code.trim()
        joinName = name
        connect()
    }

    fun joinOnline(endpoint: String, code: String, name: String, avatar: LanAvatar? = null) {
        if (busy || connected || hosting) return
        localAvatar = avatar
        address = endpoint.trim()
        relay = InternetRelay(address)
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
            var opened: Socket? = null
            val service = relay
            try {
                val socket = withContext(Dispatchers.IO) {
                    if (service == null) {
                        val ip = resolveIpv4(address)?.takeIf { joiningViaVpn || it.isSiteLocalAddress }
                            ?: throw IOException("Invalid room IPv4 address")
                        Socket().also {
                            opened = it
                            it.connect(InetSocketAddress(ip, PORT), 4500)
                        }
                    } else {
                        val guest = service.connect(JSONObject().put("role", "guest").put("code", joinCode))
                        opened = guest
                        if (service.read(guest).optString("type") != "ready") {
                            throw IOException("Relay rejected room code")
                        }
                        guest
                    }
                }
                val connection = Connection(socket)
                hostConnection = connection
                attach(connection)
                opened = null
                connection.send(JSONObject().put("type", "join").put("protocol", PROTOCOL)
                    .put("code", joinCode).put("token", token).put("name", joinName)
                    .put("avatar", localAvatar?.encoded))
            } catch (_: IOException) {
                busy = false
                status = when {
                    service != null -> "Не удалось подключиться. Проверь код комнаты, адрес сервера и интернет."
                    joiningViaVpn -> "Не удалось подключиться. Проверь адрес ведущего и общую VPN-сеть."
                    else -> "Не удалось подключиться. Проверь адрес и общую Wi‑Fi сеть."
                }
            } finally {
                runCatching { opened?.close() }
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
                    seats.add(Seat(cleanName(message.optString("name"), slot), guestToken, connection,
                        LanAvatar.parse(message.opt("avatar") as? String)))
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
                val images = message.optJSONObject("avatars")
                avatars = buildMap {
                    for (index in 0 until minOf(names.size, 4)) {
                        LanAvatar.parse(images?.opt(index.toString()) as? String)?.let { put(index, it) }
                    }
                }
                dlcEnabled = message.optBoolean("dlc")
                boardOptions = readBoardOptions(message.optJSONObject("board"))
                paused = message.getBoolean("paused")
                status = if (paused) "Партия на паузе. Ждём переподключения." else "Подключено"
            }
            "snapshot" -> {
                val recording = MatchRecording.restore(message.getJSONObject("match"))
                match = recording
                engine = recording.engine
                boardOptions = recording.customRules.board
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
            if (connected) broadcastRoom()
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

    private fun updateAvatars() {
        avatars = buildMap {
            seats.forEachIndexed { index, seat -> seat.avatar?.let { put(index, it) } }
        }
    }

    private fun broadcastRoom() {
        names = seats.map { it.name }
        updateAvatars()
        paused = seats.drop(1).any { it.connection == null }
        status = if (paused) "Ждём переподключения игрока" else "Комната открыта"
        val images = JSONObject()
        avatars.forEach { (index, avatar) -> images.put(index.toString(), avatar.encoded) }
        broadcast(JSONObject().put("type", "room").put("names", JSONArray(names)).put("paused", paused)
            .put("avatars", images).put("dlc", dlcEnabled).put("board", boardOptions.toJson()))
        driveHost()
    }

    fun configureDlc(enabled: Boolean) {
        if (!hosting || !connected || match != null || dlcEnabled == enabled) return
        dlcEnabled = enabled
        broadcastRoom()
    }

    fun configureBoard(options: BoardOptions) {
        if (!hosting || !connected || match != null || boardOptions == options) return
        boardOptions = options
        broadcastRoom()
    }

    fun start() {
        if (!hosting || !connected || match != null || paused || seats.size !in 2..4) return
        startMatch()
    }

    val canRematch: Boolean
        get() = hosting && connected && !paused && engine?.gameOver == true

    fun rematch() {
        if (!canRematch) return
        // Reject commands queued by a guest before the new match's snapshot arrives.
        revision++
        startMatch()
    }

    private fun startMatch() {
        driving?.cancel()
        driving = null
        lastRoll = null
        val recording = MatchRecording(names, emptySet(), dlcEnabled, customRules = CustomRules(board = boardOptions))
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
        runCatching { relayControl?.close() }
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

}

private fun parseIpv4(host: String): InetAddress? {
    val parts = host.split('.').map { it.toIntOrNull() }
    if (parts.size != 4 || parts.any { it == null || it !in 0..255 }) return null
    return InetAddress.getByAddress(parts.map { checkNotNull(it).toByte() }.toByteArray())
        .takeUnless { it.isAnyLocalAddress || it.isMulticastAddress || it.isLoopbackAddress }
}
