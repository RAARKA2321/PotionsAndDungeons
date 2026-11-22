package com.example.potionsanddungeons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppColors {
    val screenBg = Color(0xFF101B26)
    val boardBg = Color(0xFF152735)
    val cellBg = Color(0xFF223A49)
    val cellHighlight = Color(0xFFF2B84B)
    val moveHighlight = Color(0xFF76D6B0)
    val bossAttackZone = Color(0xFF743D46)
    val panelBg = Color(0xFF1C3040)
    val textPrimary = Color(0xFFF0F4F3)
    val textMuted = Color(0xFFA8BECA)
    val accent = Color(0xFFF2B84B)
    val potionBase = Color(0xFFD9A94E)
    val potionDlc = Color(0xFF8E5BD9)
    val wallNumber = Color(0xFFB7A9D9)
    val darkTextOnAccent = Color(0xFF1B1B2B)
    val effectTemporary = Color(0xFF5CA8E8)
    val effectOther = Color(0xFFB36FD9)
}

object AppFonts {
    val title = 24.sp
    val status = 17.sp
    val sectionLabel = 17.sp
    val hint = 14.sp
    val legend = 13.sp
    val buttonText = 17.sp
    val chip = 14.sp
    val chipCounter = 12.sp
    val inventoryCard = 14.sp
    val dialogTitle = 19.sp
    val dialogBody = 16.sp
    val potionMark = 17.sp
}

val playerPalette = listOf(
    Color(0xFFE8605C), Color(0xFF5CA8E8), Color(0xFF6FE39B), Color(0xFFF2D34B)
)

val CARD_DESCRIPTIONS: Map<CardId, String> = mapOf(
    CardId.SPEED to "Постоянно добавляет 1 шаг к результату броска.",
    CardId.SLOWDOWN to "Постоянно отнимает 1 шаг от броска. Если выпала единица, ход пропускается.",
    CardId.SHIELD to "Защищает от одной атаки другого игрока или босса (оглушение, таран, пикси, укус оборотня, настигание боссом и тп.). Срабатывает автоматически и расходуется.",
    CardId.BROKEN_SHIELD to "Сразу ломает один щит. Если щита нет, ждёт его появления.",
    CardId.INVISIBILITY to "На 3 хода босс перестаёт выбирать тебя целью. В ход босса ты всё равно можешь получить удар в его зоне атаки.",
    CardId.STENCH to "Окружает неприятным запахом на 3 хода — держатель становится приоритетной целью босса.",
    CardId.STUN to "Оглушает: держатель пропускает следующий ход.",
    CardId.WEREWOLF to "На 3 хода ты атакуешь другого игрока, только если заканчиваешь движение на его клетке последним шагом или ходом конём. Без щита игрок выбывает.",
    CardId.TIME_REWIND to "Возвращает держателя на клетку, с которой он начал движение, и завершает его перемещение.",
    CardId.BOSS_SPEED_UP to "Навсегда добавляет боссу один шаг в конце раунда.",
    CardId.BOSS_ATTACK_UP to "Навсегда увеличивает радиус атаки всех боссов на 1 шаг по сетке. Босс атакует в этой зоне в свой ход.",
    CardId.BODY_SWAP to "Меняет держателя местами с выбранным игроком.",
    CardId.EFFECT_SWAP to "Обменивает все эффекты зелий с выбранным игроком. Позиции, оглушение и карты в инвентаре остаются у своих владельцев.",
    CardId.REPEL to "Сдвигает ближайшего босса на клетку от тебя, в том числе по диагонали, если клетка свободна от других боссов и находится на поле.",
    CardId.HYPNOSIS to "Сдвигает тебя на клетку к ближайшему боссу. Возможен шаг по диагонали.",
    CardId.IMMUNITY to "Защищает от зелий «Замедление», «Зловоние», «Стан», «Сломанный щит», «Даун», «Инвалидность» и «Проклятие».",
    CardId.BOSS_DUPLICATION to "Навсегда добавляет на поле второго босса.",
    CardId.BOSS_BLINDNESS to "Все боссы пропускают следующий ход в конце раунда.",
    CardId.TELEPORT to "Выбери любую клетку без босса. На новой клетке можно попасть в зону атаки, наступить на кожурку или подобрать зелье.",
    CardId.DAUN to "Постоянно после завершения движения добавляет шаг к стене, выбранной кубиком. Если шаг заблокирован, он пропускается.",
    CardId.ATTRACTION to "Притягивает всех игроков к случайной стене.",
    CardId.HORSE to "Следующие 3 хода держатель перемещается конём, буквой «Г».",
    CardId.BFF to "Притягивает выбранного игрока прямо на клетку держателя.",
    CardId.DISABILITY to "Заменяет шестигранный кубик четырёхгранным. Эффект постоянный.",
    CardId.LET_THERE_BE_LIGHT to "Раскрывает все закрытые зелья на поле.",
    CardId.CURSE to "Снимает первый положительный эффект. Если его пока нет, ждёт его появления.",
    CardId.POTION_BOX to "Кладёт зелья на соседние клетки, включая диагонали, где ещё нет зелий.",
    CardId.FREEDOM to "Навсегда разрешает боссу ходить по диагонали.",
    CardId.CUCKOLD to "Управляй первым боссом. Кубик определяет от 1 до 4 шагов по соседним клеткам, без диагоналей. Управление заканчивается раньше, если кто-то выбыл или босс оглушён.",
    CardId.PUPPETEER to "Выбери другого игрока и сделай за него от 1 до 4 шагов по броску кубика. На пройденных клетках могут сработать зелья и ловушки.",
    CardId.ROAR_OF_ALPHA to "Подтягивает всех живых игроков на один шаг к первому боссу по горизонтали или вертикали.",
    CardId.CHAOS_ROLL to "Заменяет обычный шаг босса броском д4. Дополнительные шаги от ускорения сохраняются.",
    CardId.ATTACK to "Активная карта: оглушает игрока или босса на соседней клетке по прямой. После карты можно походить.",
    CardId.MAGNET to "Активная карта: подбирает ближайшее зелье без перемещения.",
    CardId.PIXIE to "Летит в случайном направлении по прямой и оглушает первого встречного игрока или босса. У края поля исчезает. После карты можно походить.",
    CardId.BANANA_PEEL to "Кладёт кожурку на соседнюю клетку в случайном направлении. Если клетка за краем, кожурка не появляется. Наступивший прерывает движение и оглушается.",
    CardId.TARAN to "Двигает тебя в случайном направлении до стены или цели. При столкновении оглушает цель и отталкивает её, если за ней есть клетка. Щит игрока поглощает удар. После карты можно походить.",
    CardId.THIEF to "Выбери другого игрока и укради один его эффект. Если подходящих целей нет, карта остаётся в инвентаре, а ход продолжается.",
    CardId.CLEANSE to "Снимает эффекты зелий, оглушение и пропуск хода со всех игроков. Карты в инвентаре сохраняются.",
    CardId.CHOICE_SHIELD to "Получи щит или сними один отрицательный эффект. Встречается в кастомной партии с зельями на выбор.",
    CardId.CHOICE_SPEED to "Получи постоянную скорость или сними один отрицательный эффект. Встречается в кастомной партии с зельями на выбор."
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = ViewModelProvider(this)[GameSession::class.java]
        session.attach(getSharedPreferences("saved_match", MODE_PRIVATE))
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AppColors.accent,
                    onPrimary = AppColors.darkTextOnAccent,
                    background = AppColors.screenBg,
                    onBackground = AppColors.textPrimary,
                    surface = AppColors.panelBg,
                    onSurface = AppColors.textPrimary,
                    surfaceVariant = AppColors.cellBg,
                    onSurfaceVariant = AppColors.textMuted,
                    outline = AppColors.textMuted
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.screenBg) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.screenBg, Color(0xFF19333D), AppColors.screenBg)) ).safeDrawingPadding()) {
                        RootScreen(session)
                    }
                }
            }
        }
    }
}

class GameSession : ViewModel() {
    var gameStarted by mutableStateOf(false)
    var dlcEnabled by mutableStateOf(false)
    var lastNames by mutableStateOf<List<String>>(emptyList())
    var engine by mutableStateOf<GameEngine?>(null)
    var activeDlc = false
    var activeCustomRules = CustomRules()
        private set
    var botSlots = emptySet<Int>()
    var tutorialEngine by mutableStateOf<GameEngine?>(null)
    var sandboxEngine by mutableStateOf<GameEngine?>(null)
        private set
    internal var lanRoom by mutableStateOf<LanRoom?>(null)
        private set
    private lateinit var saves: android.content.SharedPreferences
    private var recording: MatchRecording? = null

    fun openCardSandbox() {
        sandboxEngine = GameEngine(true, animateD4 = true).apply { setupCardSandbox() }
    }

    fun closeCardSandbox() {
        sandboxEngine = null
    }

    fun openLanRoom() {
        lanRoom = LanRoom()
    }

    fun closeLanRoom() {
        lanRoom?.close()
        lanRoom = null
    }

    override fun onCleared() {
        closeLanRoom()
        super.onCleared()
    }

    fun attach(preferences: android.content.SharedPreferences) {
        if (::saves.isInitialized) return
        saves = preferences
        MatchRecording.read(saves)?.let {
            recording = it
            engine = it.engine
            lastNames = it.names
            botSlots = it.bots
            activeDlc = it.dlc
            activeCustomRules = it.customRules
            dlcEnabled = it.dlc
        }
    }

    fun startMatch(
        names: List<String>, bots: Set<Int>, dlc: Boolean, customRules: CustomRules, repeatDaily: Boolean = false
    ) {
        val date = if (customRules.daily) {
            (if (repeatDaily) recording?.dailyDate else null)
                ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        } else null
        val match = MatchRecording(
            names, bots, dlc, seed = date?.hashCode() ?: kotlin.random.Random.nextInt(),
            customRules = customRules, dailyDate = date
        )
        recording = match
        lastNames = names
        botSlots = bots
        activeDlc = dlc
        activeCustomRules = customRules
        engine = match.engine
        gameStarted = true
        match.save(saves)
    }

    internal fun perform(engine: GameEngine, action: MatchAction, first: Int, second: Int): Int {
        lanRoom?.takeIf { it.engine === engine }?.let { return it.perform(action, first, second) }
        val match = recording?.takeIf { it.engine === engine }
        if (match == null) return engine.applyAction(action, first, second)
        val result = match.perform(action, first, second)
        match.save(saves)
        return result
    }
}

private val LocalGameSession = staticCompositionLocalOf<GameSession?> { null }
@Composable
fun RootScreen(session: GameSession) = with(session) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("avatars", android.content.Context.MODE_PRIVATE) }
    var avatars by remember { mutableStateOf(preferences.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()) }
    var showAvatars by rememberSaveable { mutableStateOf(false) }
    val dicePreferences = remember { context.getSharedPreferences("dice_colors", android.content.Context.MODE_PRIVATE) }
    var diceColors by remember { mutableStateOf(DiceColors(
        Color(dicePreferences.getInt("d4", 0xFFF0F1FA.toInt())),
        Color(dicePreferences.getInt("d6", 0xFFF0F1FA.toInt())),
        dicePreferences.getBoolean("rainbowD4",false),
        dicePreferences.getBoolean("rainbowD6",false)
    )) }
    CompositionLocalProvider(LocalAvatars provides avatars, LocalDiceColors provides diceColors, LocalGameSession provides session) {
    if (!showAvatars) {
    Box {
    val lesson = tutorialEngine
    val sandbox = sandboxEngine
    val room = lanRoom
    if (room != null) {
        val onlineGame = room.engine
        if (onlineGame == null) {
            BackHandler { closeLanRoom() }
            LanLobby(room, onBack = ::closeLanRoom)
        } else {
            BackHandler { closeLanRoom() }
            key(onlineGame) {
                GameScreen(onlineGame, onRestart = ::closeLanRoom, onOpenSettings = ::closeLanRoom, lan = room)
            }
        }
    } else if (sandbox != null) {
        BackHandler { closeCardSandbox() }
        key(sandbox) {
            GameScreen(sandbox, onRestart = ::openCardSandbox, onOpenSettings = ::closeCardSandbox, cardSandbox = true)
        }
    } else if (lesson != null) {
        key(lesson) { GameScreen(lesson, onRestart = { tutorialEngine = GameEngine(true).apply { setupTutorial() } }, onOpenSettings = { tutorialEngine = null }) }
    } else if (!gameStarted) {
        SetupScreen(
            dlcEnabled = dlcEnabled,
            onDlcToggle = { dlcEnabled = it },
            initialNames = lastNames,
            onResume = if (engine != null) ({ gameStarted = true }) else null,
            initialBotCount = botSlots.size,
            onTutorial = { tutorialEngine = GameEngine(true).apply { setupTutorial() } },
            onOpenCardSandbox = ::openCardSandbox,
            onOpenLan = ::openLanRoom,
            onStart = { names, bots, rules -> startMatch(names, bots, dlcEnabled, rules) }
        )
    } else {
        engine?.let { currentEngine ->
            key(currentEngine) {
                GameScreen(
                    currentEngine,
                    onRestart = {
                        startMatch(lastNames, botSlots, activeDlc, activeCustomRules, repeatDaily = true)
                    },
                    onOpenSettings = { gameStarted = false }
                )
            }
        }
    }
    if (sandbox == null && room == null) {
        TextButton(onClick = { showAvatars = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Text("Оформление") }
    }
    }
    }
    if (showAvatars) AvatarSettings(onDismiss = { showAvatars = false }, colors = diceColors, onColorsChanged = {
        diceColors = it
        dicePreferences.edit().putInt("d4", it.d4.toArgb()).putInt("d6", it.d6.toArgb())
            .putBoolean("rainbowD4",it.rainbowD4).putBoolean("rainbowD6",it.rainbowD6).apply()
    }) { key, uri ->
        avatars = avatars.toMutableMap().apply { if (uri == null) remove(key) else put(key, uri) }
        preferences.edit().apply { if (uri == null) remove(key) else putString(key, uri) }.apply()
    }
    }
}


@Composable
fun SetupScreen(
    dlcEnabled: Boolean,
    onDlcToggle: (Boolean) -> Unit,
    initialNames: List<String>,
    onResume: (() -> Unit)?,
    initialBotCount: Int,
    onTutorial: () -> Unit,
    onOpenCardSandbox: () -> Unit,
    onOpenLan: () -> Unit,
    onStart: (List<String>, Set<Int>, CustomRules) -> Unit
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(450)) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var setupPage by rememberSaveable { mutableIntStateOf(0) }
    var customParty by rememberSaveable { mutableStateOf(false) }
    var choicePotions by rememberSaveable { mutableStateOf(false) }
    var breakableDoors by rememberSaveable { mutableStateOf(false) }
    var cardTrading by rememberSaveable { mutableStateOf(false) }
    var bossDuel by rememberSaveable { mutableStateOf(false) }
    var daily by rememberSaveable { mutableStateOf(false) }
    val solo = customParty && bossDuel
    var showCatalog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var versionTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(versionTaps) {
        if (versionTaps > 0) {
            delay(2000)
            versionTaps = 0
        }
    }
    BackHandler(enabled = setupPage > 0 || onResume != null) {
        if (setupPage > 0) setupPage-- else onResume?.invoke()
    }
    var playerCount by rememberSaveable { mutableIntStateOf(initialNames.size.coerceAtLeast(2)) }
    var botCount by rememberSaveable { mutableIntStateOf(initialBotCount) }
    var names by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
    ) {
        mutableStateOf(
            List(CORNERS.size) { i -> initialNames.getOrNull(i) ?: "Игрок ${i + 1}" }
        )
    }


    if (showCatalog) {
        CardCatalogScreen { showCatalog = false }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = entrance.value; translationY = (1f - entrance.value) * 32f }.imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (setupPage > 0) {
            TextButton(onClick = { setupPage-- }, modifier = Modifier.align(Alignment.Start)) { Text("Назад") }
        }
        if (setupPage == 0) {
        Text("Potions and Dungeons", color = AppColors.textPrimary, fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
        Text("Переживи остальных игроков, пока босс преследует вас по полю.", color = AppColors.textMuted, textAlign = TextAlign.Center)
        TextButton(onClick = { showRules = true }) { Text("Как играть") }
        TextButton(onClick = onTutorial) { Text("Обучение") }
        Spacer(Modifier.height(24.dp))
        if (onResume != null) {
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Продолжить партию") }
        }
        Button(onClick = { setupPage = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Новая игра") }
        OutlinedButton(onClick = onOpenLan, modifier = Modifier.fillMaxWidth()) { Text("Сетевая игра") }
        TextButton(onClick = { showCatalog = true }) { Text("Справочник зелий") }
        TextButton(onClick = {
            versionTaps++
            if (versionTaps == 7) {
                versionTaps = 0
                onOpenCardSandbox()
            }
        }) { Text("Версия $version", color = AppColors.textMuted, fontSize = AppFonts.legend) }
        }

        if (setupPage == 1) {
        Text("Настройки партии", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !customParty, onClick = { customParty = false }, label = { Text("Обычная") })
            FilterChip(selected = customParty, onClick = { customParty = true }, label = { Text("Кастомная партия") })
        }
        if (customParty) {
            CustomRuleSwitch("Дуэль с боссом", bossDuel) { bossDuel = it }
            CustomRuleSwitch("Зелья с выбором", choicePotions) { choicePotions = it }
            CustomRuleSwitch("Разрушаемые двери", breakableDoors) { breakableDoors = it }
            if (!solo) CustomRuleSwitch("Обмен картами", cardTrading) { cardTrading = it }
            CustomRuleSwitch("Партия дня", daily) { daily = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Дополнительные зелья (DLC)", color = AppColors.textPrimary, fontSize = AppFonts.sectionLabel, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = dlcEnabled,
                onCheckedChange = onDlcToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = AppColors.accent)
            )
        }

        if (!solo) {
        Spacer(Modifier.height(24.dp))
        Text("Сколько игроков?", color = AppColors.textPrimary, fontSize = AppFonts.sectionLabel)
        Spacer(Modifier.height(8.dp))
        Row {
            (2..4).forEach { count ->
                val selected = playerCount == count
                Button(
                    onClick = { playerCount = count; botCount = botCount.coerceAtMost(count - 1) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AppColors.accent else AppColors.panelBg,
                        contentColor = if (selected) AppColors.darkTextOnAccent else AppColors.textPrimary
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) { Text("$count", fontSize = AppFonts.buttonText) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Боты", fontSize = AppFonts.sectionLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until playerCount).forEach { count ->
                FilterChip(selected = botCount == count, onClick = { botCount = count }, label = { Text("$count") })
            }
        }
        }
        Button(onClick = { setupPage = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Далее") }
        }

        if (setupPage == 2) {
        Spacer(Modifier.height(24.dp))
        Text("Имена игроков", color = AppColors.textPrimary, fontSize = AppFonts.sectionLabel)
        Spacer(Modifier.height(4.dp))
        Text("Цвет каждого игрока на доске совпадает с порядком ниже", color = AppColors.textMuted, fontSize = AppFonts.hint)
        Spacer(Modifier.height(8.dp))
        val participants = if (solo) 1 else playerCount
        for (i in 0 until participants) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(16.dp).background(playerPalette[i % playerPalette.size], shape = CircleShape))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = names[i],
                    onValueChange = { newValue -> names = names.mapIndexed { index, name -> if (index == i) newValue else name } },
                    label = { Text(if (!solo && i >= playerCount - botCount) "Имя бота ${i + 1}" else "Имя игрока ${i + 1}", fontSize = AppFonts.hint) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = AppFonts.dialogBody, color = AppColors.textPrimary),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.textPrimary,
                        unfocusedTextColor = AppColors.textPrimary,
                        focusedLabelColor = AppColors.accent,
                        unfocusedLabelColor = AppColors.textMuted,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.textMuted,
                        cursorColor = AppColors.accent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val finalNames = (0 until participants).map { i -> names[i].ifBlank { "Игрок ${i + 1}" } }
                val rules = if (customParty) {
                    CustomRules(choicePotions, breakableDoors, cardTrading && !solo, solo, daily)
                } else CustomRules()
                val bots = if (solo) emptySet() else (playerCount - botCount until playerCount).toSet()
                onStart(finalNames, bots, rules)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.darkTextOnAccent)
        ) { Text("Начать игру", fontSize = AppFonts.buttonText) }
        }
    }
    if (showRules) RulesDialog { showRules = false }

}
@Composable
private fun CustomRuleSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics {
            contentDescription = label
        })
    }
}

@Composable
private fun matchActions(engine: GameEngine): (MatchAction, Int, Int) -> Int {
    val session = LocalGameSession.current
    return { action, first, second ->
        session?.perform(engine, action, first, second) ?: engine.applyAction(action, first, second)
    }
}

@Composable
internal fun GameScreen(
    engine: GameEngine, onRestart: () -> Unit, onOpenSettings: () -> Unit,
    cardSandbox: Boolean = false, lan: LanRoom? = null
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(400)) }

    val scope = rememberCoroutineScope()
    var showInventoryFor by remember { mutableStateOf<Player?>(null) }
    var attackPreview by remember(engine) { mutableStateOf<Pair<Player, CardId>?>(null) }
    var showCardCatalog by remember { mutableStateOf(false) }
    var showSandboxCards by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showGameMenu by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var diceVisible by remember { mutableStateOf(false) }
    var diceResult by remember { mutableIntStateOf(-1) }
    var diceStage by remember { mutableIntStateOf(0) }
    var diceCaption by remember { mutableStateOf<String?>(null) }
    var diceSides by remember { mutableIntStateOf(6) }

    if (showCardCatalog) {
        CardCatalogScreen { showCardCatalog = false }
        return
    }
    if (showSandboxCards) {
        CardCatalogScreen(onSelectCard = { card ->
            engine.testCard(card)
            showSandboxCards = false
        }) { showSandboxCards = false }
        return
    }

    val perform = matchActions(engine)
    val localInput = lan?.canAct ?: true
    val driveGame = lan == null
    val lanRoll = lan?.lastRoll
    LaunchedEffect(lanRoll) {
        if (lanRoll != null) {
            diceResult = -1
            diceSides = lanRoll.sides
            diceCaption = null
            diceVisible = true
            delay(900)
            diceResult = lanRoll.value
            delay(700)
            diceVisible = false
        }
    }
    fun rollWithAnimation(reroll: Boolean = false) {
        if (lan != null) {
            perform(if (reroll) MatchAction.REROLL else MatchAction.ROLL_INLINE, 0, 0)
            return
        }
        val player = engine.turnOrder.getOrNull(engine.currentTurnIndex)
        if (engine.mustSkipCurrentTurn() || player?.effects?.has(CardId.HORSE) == true) {
            perform(MatchAction.ROLL_INLINE, 0, 0)
        } else {
            diceResult = -1
            diceSides = engine.diceSidesForCurrentPlayer()
            diceStage = 0
            diceCaption = null
            diceVisible = true
            scope.launch {
                val slow = player?.effects?.has(CardId.SLOWDOWN) == true
                val fast = player?.effects?.has(CardId.SPEED) == true
                delay(900)
                val finalSteps = perform(if (reroll) MatchAction.REROLL else MatchAction.ROLL_INLINE, 0, 0)
                diceResult = engine.lastDiceRoll
                delay(700)
                if (!engine.awaitingRollDecision && (slow || fast)) {
                    diceCaption = if (slow && diceResult == 1) "Замедление: ход пропущен" else
                        listOfNotNull(if (slow) "−1 Замедление" else null, if (fast) "+1 Скорость" else null).joinToString(" · ")
                    diceStage++
                    diceResult = -1
                    delay(900)
                    diceResult = finalSteps
                    diceCaption += " → $finalSteps шагов"
                    delay(900)
                }
                diceVisible = false
            }
        }
    }

    val tutorialFinished = engine.tutorial && engine.bossMoveTrigger >= 2 && !engine.awaitingBossMove
    val botTurn = engine.turnOrder.getOrNull(engine.currentTurnIndex)?.isBot == true
    LaunchedEffect(engine, botTurn, engine.awaitingBossMove, engine.gameOver, engine.cardReveal, engine.d4Rolls.size, diceVisible, showGameMenu, showLog, showRules, confirmRestart) {
        if (botTurn && !diceVisible && !showGameMenu && !showLog && !showRules && !confirmRestart) {
            while (!engine.gameOver && !engine.awaitingBossMove && engine.cardReveal == null &&
                engine.d4Rolls.isEmpty() && engine.turnOrder.getOrNull(engine.currentTurnIndex)?.isBot == true
            ) {
                delay(450)
                if (perform(MatchAction.BOT, 0, 0) == 1) rollWithAnimation()
                if (diceVisible) break
            }
        }
    }

    LaunchedEffect(engine, engine.bossMoveTrigger, engine.cardReveal, diceVisible, engine.d4Rolls.size, driveGame) {
        if (driveGame && engine.awaitingBossMove && engine.cardReveal == null && !diceVisible && engine.d4Rolls.isEmpty()) {
            delay(1000)
            while (perform(MatchAction.BOSS, 0, 0) == 1) {
                if (engine.d4Rolls.isNotEmpty()) break
                delay(550)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = entrance.value }.padding(16.dp)) {
        val boardSize = minOf(maxWidth, (maxHeight * 0.48f).coerceAtLeast(240.dp))
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTextButton(text = "Меню", enabled = !diceVisible, onClick = { showGameMenu = true })
                IconTextButton(text = "Зелья", enabled = !diceVisible, onClick = { showCardCatalog = true })
                IconTextButton(text = "Журнал", enabled = !diceVisible, onClick = { showLog = true })
            }
            if (cardSandbox) {
                Text("Полигон карт", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !diceVisible && attackPreview == null && engine.canTestCard,
                        onClick = { showSandboxCards = true }
                    ) { Text("Выдать карту") }
                    OutlinedButton(onClick = onRestart) { Text("Сбросить") }
                }
            }
            Spacer(Modifier.height(8.dp))
            TurnGuide(engine)
            if (lan != null) {
                val player = engine.players.getOrNull(lan.seat)?.name.orEmpty()
                Text(if (lan.paused) lan.status else "Ты играешь за $player", color = AppColors.textMuted)
                if (!lan.hosting && !lan.connected) {
                    OutlinedButton(onClick = lan::reconnect, enabled = !lan.busy) { Text("Переподключиться") }
                }
            }
            if (engine.duelExit != null && !engine.gameOver) {
                Text(
                    if (engine.duelKeys.isEmpty()) "Все ключи собраны. Закончи движение на выходе."
                    else "Ключи: ${3 - engine.duelKeys.size}/3 · закончи движение на клетке с ключом.",
                    fontSize = AppFonts.hint, color = AppColors.textMuted
                )
            }
            if (tutorialFinished) {
                Button(onClick = onOpenSettings) { Text("Завершить обучение") }
            }
            Spacer(Modifier.height(12.dp))
            PlayersRow(engine, onPlayerClick = { player ->
                if (!localInput || botTurn || diceVisible || attackPreview != null) return@PlayersRow
                if (engine.pendingChoice is PendingChoice.ChoosePlayer) {
                    perform(MatchAction.PLAYER, engine.players.indexOf(player), 0)
                } else if (player.activeCards.isNotEmpty() && (lan == null || engine.isCurrentPlayer(player))) {
                    showInventoryFor = player
                }
            })

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.height(boardSize).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoardView(engine, attackPreview, inputEnabled = !diceVisible && localInput)
                if (diceVisible) key(diceStage) { DiceRollOverlay(diceResult, diceCaption, diceSides) }
                if (!diceVisible && lan?.paused != true && engine.d4Rolls.isNotEmpty()) {
                    key(engine.d4Sequence) {
                        D4RollOverlay(engine.d4Rolls.first()) {
                            if (driveGame) perform(MatchAction.D4, 0, 0)
                        }
                    }
                }
            }



            Text("Бирюзовый — ход · Золотой — цель или зелье · ! — риск атаки", color = AppColors.textMuted, fontSize = AppFonts.legend)
            Spacer(Modifier.height(8.dp))
            val preview = attackPreview
            if (preview != null) {
                val (player, card) = preview
                Text(card.displayName(), fontWeight = FontWeight.Bold)
                Text(
                    if (card == CardId.ATTACK) "Золотым отмечены соседние клетки с целями."
                    else "Направление определит д4 по номерам стен: 1 ↑  2 →  3 ↓  4 ←.",
                    color = AppColors.textMuted,
                    fontSize = AppFonts.hint
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = localInput && engine.canPlayActiveCard(player, card), onClick = {
                        attackPreview = null
                        perform(MatchAction.CARD, engine.players.indexOf(player), card.ordinal)
                    }) { Text("Сыграть карту") }
                    OutlinedButton(onClick = { attackPreview = null }) { Text("Отмена") }
                }
            } else {
                key(engine.d4Sequence) {
                    DiceButton(engine, scope, !localInput || diceVisible || engine.d4Rolls.isNotEmpty() || botTurn || engine.awaitingRollDecision || tutorialFinished || engine.tutorial && engine.bossMoveTrigger == 1 && !engine.attackCardPlayed) {
                        rollWithAnimation()
                    }
                }
                if (localInput && !diceVisible && engine.canReroll && !botTurn) {
                    OutlinedButton(onClick = { rollWithAnimation(reroll = true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Перебросить кубик")
                    }
                }
                if (localInput && !diceVisible && engine.awaitingRollDecision) {
                    TextButton(
                        onClick = { perform(MatchAction.ACCEPT, 0, 0) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Оставить бросок (${engine.lastDiceRoll})") }
                }
            }
            if (engine.gameOver) {
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (lan == null) "Новая игра" else "Покинуть комнату")
                }
            }
            val current = engine.turnOrder.getOrNull(engine.currentTurnIndex)
            if (localInput && preview == null && !diceVisible && !engine.attackCardPlayed && !engine.awaitingRollDecision && current != null && current.activeCards.isNotEmpty() && engine.pendingSteps == 0 &&
                !engine.awaitingBossMove && !engine.hasPendingAction && !engine.mustSkipCurrentTurn() && !engine.gameOver
            ) {
                OutlinedButton(onClick = { showInventoryFor = current }, modifier = Modifier.fillMaxWidth()) {
                    Text("Карты в руке (${current.activeCards.size})")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    showInventoryFor?.takeIf { localInput }?.let { player ->
        InventoryDialog(engine, player, onDismiss = { showInventoryFor = null }, onPreview = { card ->
            showInventoryFor = null
            attackPreview = player to card
        })
    }
    if (attackPreview != null) {
        BackHandler { attackPreview = null }
    }
    if (showRules) RulesDialog { showRules = false }
    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Журнал партии") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                engine.log.takeLast(50).forEach { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
            } },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Закрыть") } }
        )
    }
    if (showGameMenu) {
        AlertDialog(
            onDismissRequest = { showGameMenu = false },
            title = { Text("Пауза") },
            text = { Column {
                TextButton(onClick = { showGameMenu = false; showRules = true }) { Text("Как играть") }
                TextButton(onClick = { showGameMenu = false; onOpenSettings() }) { Text("Главное меню") }
                if (lan == null) {
                    TextButton(onClick = { showGameMenu = false; confirmRestart = true }) { Text("Начать заново") }
                }
            } },
            confirmButton = { TextButton(onClick = { showGameMenu = false }) { Text("Продолжить партию") } }
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Начать новую игру?") },
            text = { Text("Текущая партия завершится.") },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Продолжить партию") } },
            confirmButton = { TextButton(onClick = onRestart) { Text("Начать заново") } }
        )
    }
    (engine.pendingChoice as? PendingChoice.ChooseEffect)?.takeIf { localInput && !botTurn }?.let { choice ->
        EffectChoiceDialog(choice, onEffectSelected = { effect -> perform(MatchAction.EFFECT, choice.from.effects.indexOf(effect), 0) })
    }
    if (engine.tradePartners.isNotEmpty()) {
        val trader = engine.turnOrder[engine.currentTurnIndex]
        TradeDialog(trader, engine.tradePartners, onCancel = { perform(MatchAction.TRADE, -1, 0) }) { partner, offered, requested ->
            perform(MatchAction.TRADE, engine.players.indexOf(partner), (offered.ordinal shl 16) or requested.ordinal)
        }
    }
    engine.cardReveal?.takeIf { localInput }?.let { event ->
        if (event.bot && event.card.choiceReward != null) {
            LaunchedEffect(event) {
                delay(500)
                if (engine.cardReveal === event) perform(MatchAction.POTION, if (event.canCleanse) 1 else 0, 0)
            }
        } else {
            CardRevealDialog(event) { cleanse ->
                if (engine.cardReveal === event) perform(MatchAction.POTION, if (cleanse) 1 else 0, 0)
            }
        }
    }
}

@Composable
private fun TradeDialog(
    trader: Player,
    partners: List<Player>,
    onCancel: () -> Unit,
    onTrade: (Player, CardId, CardId) -> Unit
) {
    var partner by remember(partners) { mutableStateOf(partners.singleOrNull()) }
    var offered by remember(partners) { mutableStateOf<CardId?>(null) }
    var requested by remember(partners) { mutableStateOf<CardId?>(null) }
    var offeredToPartner by remember(partners) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (offeredToPartner) "${partner?.name}, принять обмен?" else "Обмен картами") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (offeredToPartner) {
                    Text("Получишь «${offered?.displayName()}», отдашь «${requested?.displayName()}».")
                } else {
                    Text("С кем обменяться?")
                    partners.forEach { candidate ->
                        FilterChip(selected = partner === candidate, onClick = {
                            if (partner !== candidate) requested = null
                            partner = candidate
                        }, label = { Text(candidate.name) })
                    }
                    Text("Что отдать?")
                    trader.activeCards.distinct().forEach { card ->
                        FilterChip(selected = offered == card, onClick = { offered = card }, label = { Text(card.displayName()) })
                    }
                    partner?.let { target ->
                        Text("Что попросить?")
                        target.activeCards.distinct().forEach { card ->
                            FilterChip(selected = requested == card, onClick = { requested = card }, label = { Text(card.displayName()) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = partner != null && offered != null && requested != null, onClick = {
                if (offeredToPartner) onTrade(checkNotNull(partner), checkNotNull(offered), checkNotNull(requested))
                else offeredToPartner = true
            }) { Text(if (offeredToPartner) "Принять" else "Предложить") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(if (offeredToPartner) "Отказаться" else "Пропустить") } }
    )
}

@Composable
private fun TurnGuide(engine: GameEngine) {
    val current = engine.turnOrder.getOrNull(engine.currentTurnIndex)
    val heading = when {
        engine.gameOver -> "Итог партии"
        engine.awaitingBossMove -> "Ход босса"
        else -> "Ход: ${current?.name.orEmpty()}"
    }
    val instruction = when {
        engine.tutorial && engine.bossMoveTrigger >= 2 && !engine.awaitingBossMove -> "Обучение пройдено."
        engine.tutorial && engine.pendingChoice != null -> "Выбери клетку босса, чтобы оглушить его."
        engine.tutorial && engine.attackCardPlayed && engine.pendingSteps > 0 -> "Отойди от босса на подсвеченную клетку."
        engine.tutorial && engine.pendingSteps > 0 && current?.isBot == false -> "Нажми на клетку с зельем. Оно попадёт в руку."
        engine.tutorial && engine.bossMoveTrigger == 1 && !engine.attackCardPlayed && current?.isBot == false && !engine.awaitingBossMove -> "Открой «Карты в руке» и разыграй «Атаку»."
        engine.gameOver -> engine.statusText
        engine.awaitingBossMove -> "Босс движется к ближайшему видимому игроку."
        current?.isBot == true -> "Бот выполняет свой ход."
        engine.hasPendingAction -> engine.statusText
        engine.mustSkipCurrentTurn() -> "Нажми «Пропустить ход», чтобы передать очередь."
        engine.pendingSteps > 0 -> "Осталось шагов: ${engine.pendingSteps}. Нажми на клетку с бирюзовым контуром."
        engine.canMoveAsKnight() -> "Выбери выделенную клетку буквой «Г»."
        current?.effects?.has(CardId.HORSE) == true -> "Доступных клеток для коня нет. Пропусти ход."
        engine.attackCardPlayed -> "Карта разыграна. Брось кубик, чтобы продолжить ход."
        current?.activeCards?.isNotEmpty() == true -> "Брось кубик или разыграй карту из руки."
        else -> "Брось кубик, чтобы узнать, сколько шагов можно сделать."
    }
    Column(modifier = Modifier.fillMaxWidth().animateContentSize().shadow(6.dp, RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(AppColors.panelBg, Color(0xFF294754))), RoundedCornerShape(18.dp)).border(1.dp, AppColors.moveHighlight.copy(alpha = 0.2f), RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(heading, color = AppColors.accent, fontSize = AppFonts.sectionLabel, fontWeight = FontWeight.Bold)
        Text(instruction, color = AppColors.textPrimary, fontSize = AppFonts.hint)
    }
}

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Как играть") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Цель — остаться последним игроком на поле.")
                Text("Брось кубик и двигайся по выделенным клеткам. Ходи по горизонтали или вертикали. Сразу вернуться на предыдущую клетку нельзя.")
                Text("Если закончишь движение на клетке с флаконом, подберёшь зелье. Округлые флаконы — базовая колода, угловатые — DLC. В партии с DLC клетку босса нужно обходить. Некоторые карты попадают в руку. Их можно разыграть в начале своего хода. После «Атаки», «Пикси» или «Тарана» можно походить.")
                Text("Босс атакует только в свой ход. Красные клетки показывают зону его атаки.")
                Text("Если игра просит выбрать цель, нажми на клетку с золотым контуром или на выделенного игрока над полем.")
            }
        }
    )
}

@Composable
fun IconTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.panelBg, contentColor = AppColors.textPrimary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, fontSize = AppFonts.chip) }
}

@Composable
fun PlayersRow(engine: GameEngine, onPlayerClick: (Player) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(100.dp).horizontalScroll(rememberScrollState())) {
        engine.players.forEachIndexed { index, p ->
            val color = playerPalette[index % playerPalette.size]
            val isCurrent = engine.turnOrder.getOrNull(engine.currentTurnIndex) == p
            val playerBackground by animateColorAsState(if (isCurrent) AppColors.panelBg else AppColors.boardBg, tween(300))
            val isChoice = (engine.pendingChoice as? PendingChoice.ChoosePlayer)?.allowed?.invoke(p) == true
            Column(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .fillMaxHeight()
                    .background(
                        playerBackground,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = if (isCurrent || isChoice) 2.dp else 0.dp,
                        color = if (isCurrent || isChoice) AppColors.accent else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onPlayerClick(p) }
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(color, shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (p.alive) (if (p.isBot) "${p.name} · бот" else p.name) else "${p.name} (выбыл)",
                        color = if (p.alive) AppColors.textPrimary else AppColors.textMuted,
                        fontSize = AppFonts.chip
                    )
                }
                if (p.effects.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        p.effects.forEach { eff ->
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .background(
                                        if (eff.category == EffectCategory.TEMPORARY) AppColors.effectTemporary else AppColors.effectOther,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(eff.id.displayName(), color = Color.White, fontSize = AppFonts.chipCounter)
                                    if (eff.category == EffectCategory.TEMPORARY) {
                                        Text(
                                            text = eff.turnsLeft.toString(),
                                            color = Color.White,
                                            fontSize = AppFonts.chipCounter,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(AppColors.boardBg, CircleShape)
                                                .padding(horizontal = 5.dp)
                                                .semantics { contentDescription = "Осталось ходов: ${eff.turnsLeft}" }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (p.skipNextTurn || p.stunnedTurns > 0) {
                    Text("Стан · пропуск хода", color = AppColors.accent, fontSize = AppFonts.chipCounter)
                }
                if (p.activeCards.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Инвентарь: ${p.activeCards.size}", color = AppColors.accent, fontSize = AppFonts.chipCounter)
                }
            }
        }
    }
}

@Composable
fun DiceButton(engine: GameEngine, scope: kotlinx.coroutines.CoroutineScope, isRolling: Boolean, onRoll: () -> Unit) {

    val current = engine.turnOrder.getOrNull(engine.currentTurnIndex)
    val mustSkip = engine.mustSkipCurrentTurn()
    val isKnightTurn = current?.effects?.has(CardId.HORSE) == true && engine.pendingSteps == 0 && !mustSkip
    val isKnightMove = isKnightTurn && engine.canMoveAsKnight()
    val canRoll = !engine.gameOver && engine.pendingSteps == 0 && engine.turnOrder.isNotEmpty() &&
            !isRolling && !isKnightMove && !engine.awaitingBossMove && !engine.hasPendingAction

    Column {
        Button(
            onClick = {
                if (!canRoll) return@Button
                onRoll()
            },
            enabled = canRoll,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.accent,
                contentColor = AppColors.darkTextOnAccent,
                disabledContainerColor = AppColors.panelBg,
                disabledContentColor = AppColors.textMuted
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    engine.gameOver -> "Игра окончена"
                    engine.awaitingBossMove -> "Ход босса..."
                    engine.pendingCuckoldSteps > 0 -> "Управление боссом"
                    engine.pendingPuppetSteps > 0 -> "Управление игроком"
                    engine.pendingChoice != null -> "Выбери цель"
                    isKnightMove -> "Ход конём"
                    mustSkip || isKnightTurn -> "Пропустить ход"
                    isRolling -> "Бросаю..."
                    engine.pendingSteps > 0 -> "Осталось шагов: ${engine.pendingSteps}"
                    else -> "Бросить кубик"
                },
                fontSize = AppFonts.buttonText
            )
        }
    }
}

@Composable
private fun DiceRollOverlay(result: Int, caption: String? = null, sides: Int = 6) {
    val spin = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { spin.animateTo(1f, tween(900)) }
        launch {
            lift.animateTo(1f, tween(280))
            lift.animateTo(0f, spring(dampingRatio = 0.4f))
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (sides == 4) RollingTetrahedron(result, spin.value) else RollingCube(result, spin.value, lift.value)
            Spacer(Modifier.height(20.dp))
            Text(if (result >= 0) "Выпало: $result" else "Бросаем кубик…", color = Color.White, fontWeight = FontWeight.Bold)
            caption?.let { Text(it, color = AppColors.accent, textAlign = TextAlign.Center) }
        }
    }
}

private val cellGap = 4.dp
private val boardMargin = 4.dp

@Composable
fun BoardView(engine: GameEngine, attackPreview: Pair<Player, CardId>? = null, inputEnabled: Boolean = true) {
    val perform = matchActions(engine)
    val attackZone = engine.bossAttackZoneCells()
    val previewCells = attackPreview?.let { (player, card) ->
        if (card == CardId.ATTACK) {
            engine.occupiedAttackTargets(player).toSet()
        } else {
            Wall.entries.flatMap { wall ->
                val direction = Wall.directionByNumber(wall.number)
                val (_, end) = engine.traceUntilObstacle(player.position, direction)
                generateSequence(player.position) { cell ->
                    if (cell == end) null else Cell(cell.row + direction.dr, cell.col + direction.dc)
                }.drop(1).toList()
            }.toSet()
        }
    }.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val available = (minOf(maxWidth, maxHeight) - boardMargin * 2).coerceAtLeast(0.dp)
        val step = available / BOARD_SIZE
        val cellSize = (step - cellGap).coerceAtLeast(0.dp)
        val boardSize = step * BOARD_SIZE
        val mid = BOARD_SIZE / 2

        Box(
            modifier = Modifier
                .size(boardSize)
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .background(AppColors.boardBg, shape = RoundedCornerShape(14.dp))
        ) {
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    val cell = Cell(r, c)
                    val selectable = inputEnabled && attackPreview == null && engine.isBoardTarget(cell) &&
                        engine.turnOrder.getOrNull(engine.currentTurnIndex)?.isBot != true &&
                        (!engine.tutorial || if (engine.pendingChoice != null) engine.bosses.any { it.position == cell } else engine.attackCardPlayed || cell == Cell(0, 1))
                    val dangerous = cell in attackZone
                    val door = cell in engine.doors
                    val duelKey = cell in engine.duelKeys
                    val duelExit = cell == engine.duelExit
                    val potion = engine.potions.firstOrNull { it.position == cell }
                    val shape = RoundedCornerShape(6.dp)
                    val lastStep = selectable && !engine.hasPendingAction &&
                        (engine.pendingSteps == 1 || engine.pendingSteps == 0 && engine.canMoveAsKnight())
                    val risky = lastStep && engine.lastStepRisk(cell)
                    val outline = if (engine.pendingChoice != null || lastStep && potion != null) AppColors.cellHighlight else AppColors.moveHighlight
                    val tileColor by animateColorAsState(
                        if (door) Color(0xFF62452E) else if (dangerous) AppColors.bossAttackZone else if (selectable) Color(0xFF30584F) else AppColors.cellBg,
                        tween(240)
                    )
                    val tileScale by animateFloatAsState(if (selectable) 0.96f else 1f, spring())
                    Box(
                        modifier = Modifier
                            .offset(x = step * c, y = step * r)
                            .size(cellSize)
                            .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
                            .background(tileColor, shape)
                            .then(when {
                                cell in previewCells -> Modifier.border(2.dp, AppColors.cellHighlight, shape)
                                selectable -> Modifier.border(2.dp, outline, shape)
                                else -> Modifier
                            })
                            .semantics {
                                contentDescription = buildString {
                                    append("Ряд ${r + 1}, столбец ${c + 1}")
                                    if (door) append(", закрытая дверь")
                                    if (duelKey) append(", ключ")
                                    if (duelExit) append(", выход")
                                    if (dangerous) append(", зона атаки")
                                    if (risky) append(", босс может достать в конце раунда")
                                    if (cell in engine.bananaPeels) append(", кожурка")
                                    if (potion != null) append(if (potion.revealed) ", ${potion.cardId.displayName()}" else ", закрытое зелье")
                                    engine.players.filter { it.alive && it.position == cell }.forEach { append(", ${it.name}") }
                                }
                            }
                            .clickable(enabled = selectable) { perform(MatchAction.CELL, cell.row, cell.col) }
                    ) {
                        if (door) Text("🚪", modifier = Modifier.align(Alignment.Center))
                        if (duelKey || duelExit) {
                            Text(if (duelKey) "🔑" else "🚩", modifier = Modifier.align(Alignment.BottomStart))
                        }
                        if (risky) Text("!", color = Color(0xFFFFA5A5), fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp))
                    }
                }
            }

            Box(modifier = Modifier.offset(x = step * mid, y = 0.dp).size(cellSize), contentAlignment = Alignment.TopCenter) {
                Text("1", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            Box(modifier = Modifier.offset(x = step * mid, y = step * (BOARD_SIZE - 1)).size(cellSize), contentAlignment = Alignment.BottomCenter) {
                Text("3", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
            }
            Box(modifier = Modifier.offset(x = 0.dp, y = step * mid).size(cellSize), contentAlignment = Alignment.CenterStart) {
                Text("4", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
            }
            Box(modifier = Modifier.offset(x = step * (BOARD_SIZE - 1), y = step * mid).size(cellSize), contentAlignment = Alignment.CenterEnd) {
                Text("2", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 2.dp))
            }

            engine.bananaPeels.forEach { cell ->
                key("peel_$cell") {
                    Box(
                        modifier = Modifier.offset(x = step * cell.col, y = step * cell.row).size(cellSize),
                        contentAlignment = Alignment.Center
                    ) { Text("🍌", fontSize = 18.sp) }
                }
            }

            engine.potions.forEach { potion -> key(potion) { PotionView(potion, step, cellSize) } }

            engine.players.filter { it.alive }.forEach { player ->
                val idx = engine.players.indexOf(player)
                val color = playerPalette[idx % playerPalette.size]
                val isCurrent = engine.turnOrder.getOrNull(engine.currentTurnIndex) == player && !engine.gameOver
                key(player) { PlayerView(player, color, isCurrent, step, cellSize, "player$idx") }
            }

            engine.bosses.forEach { boss -> key(boss.slot) { BossView(boss, step, cellSize) } }
        }
    }
}




@Composable
fun PlayerView(player: Player, color: Color, isCurrent: Boolean, step: androidx.compose.ui.unit.Dp, cellSize: androidx.compose.ui.unit.Dp, avatarKey: String) {
    val animX by animateDpAsState(targetValue = step * player.position.col, animationSpec = tween(300))
    val animY by animateDpAsState(targetValue = step * player.position.row, animationSpec = tween(300))
    Box(modifier = Modifier.offset { IntOffset(animX.roundToPx(), animY.roundToPx()) }.size(cellSize), contentAlignment = Alignment.Center) {

        Box(
            modifier = Modifier
                    .size(minOf(52.dp, cellSize * 0.9f))
                .background(color, shape = CircleShape)
                .then(
                    if (isCurrent) Modifier.border(2.dp, AppColors.textPrimary, shape = CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(player.name.first().toString(), color = AppColors.darkTextOnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            AvatarImage(avatarKey, Modifier.fillMaxSize())
        }
        if (player.stunnedTurns > 0) {
            Text("💫", fontSize = 13.sp, modifier = Modifier.offset(y = (-20).dp))
        }
    }
}

@Composable
fun BossView(boss: BossUnit, step: androidx.compose.ui.unit.Dp, cellSize: androidx.compose.ui.unit.Dp) {
    val animX by animateDpAsState(targetValue = step * boss.position.col, animationSpec = tween(400))
    val animY by animateDpAsState(targetValue = step * boss.position.row, animationSpec = tween(400))
    Box(modifier = Modifier.offset { IntOffset(animX.roundToPx(), animY.roundToPx()) }.size(cellSize), contentAlignment = Alignment.Center) {

        Box(
            modifier = Modifier.size(minOf(56.dp, cellSize * 0.95f)).background(Color(0xFF4A1620), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (boss.stunnedTurns > 0) "💤" else "👹", fontSize = 19.sp)
            AvatarImage("boss", Modifier.fillMaxSize())
        }
    }
}

@Composable
fun PotionView(potion: Potion, step: androidx.compose.ui.unit.Dp, cellSize: androidx.compose.ui.unit.Dp) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(potion.usedTrigger) {
        if (potion.usedTrigger > 0) {
            scale.snapTo(1.5f)
            scale.animateTo(1f, animationSpec = tween(350))
        }
    }
    Box(
        modifier = Modifier.offset(x = step * potion.position.col, y = step * potion.position.row)
            .size(cellSize).graphicsLayer(scaleX = scale.value, scaleY = scale.value),

        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(minOf(36.dp, cellSize * 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            if (potion.revealed) {
                Text(potion.cardId.displayName(), color = Color.White, fontSize = 7.sp)
            } else {
                PotionIcon(potion.source, (potion.position.row * BOARD_SIZE + potion.position.col) % 4, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun LogPanel(engine: GameEngine) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(AppColors.panelBg, shape = RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text("Журнал событий", color = AppColors.textMuted, fontSize = AppFonts.hint)
        Spacer(Modifier.height(4.dp))
        val scrollState = rememberScrollState()
        LaunchedEffect(engine.log.size) { scrollState.animateScrollTo(scrollState.maxValue) }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)) {
            engine.log.takeLast(50).forEach { line ->
                Text(line, color = AppColors.textPrimary, fontSize = AppFonts.hint)
            }
        }
    }
}

@Composable
fun InventoryDialog(engine: GameEngine, player: Player, onDismiss: () -> Unit, onPreview: (CardId) -> Unit) {
    val perform = matchActions(engine)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", fontSize = AppFonts.buttonText) } },
        title = { Text("Инвентарь ${player.name}", fontSize = AppFonts.dialogTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                player.activeCards.forEach { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = engine.canPlayActiveCard(player, card)) {
                                if (ActiveCardResolver.allowsMovementAfterUse(card)) {
                                    onPreview(card)
                                } else {
                                    perform(MatchAction.CARD, engine.players.indexOf(player), card.ordinal)
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Column {
                            Text(card.displayName(), fontSize = AppFonts.inventoryCard, fontWeight = FontWeight.Bold)
                            Text(CARD_DESCRIPTIONS[card] ?: "", fontSize = AppFonts.hint, color = AppColors.textMuted)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CardCatalogScreen(onSelectCard: ((CardId) -> Unit)? = null, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var deckFilter by rememberSaveable { mutableStateOf<DeckSource?>(null) }
    val matchingCards = CardId.entries.filter { card ->
        (deckFilter == null || card.isDlc == (deckFilter == DeckSource.DLC)) &&
            card.displayName().contains(query.trim(), ignoreCase = true)
    }
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().imePadding().padding(24.dp)) {
        TextButton(onClick = onDismiss) { Text("Назад") }
        Text(if (onSelectCard == null) "Справочник зелий" else "Выбери карту", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(query, { query = it }, label = { Text("Поиск по названию") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = deckFilter == null, onClick = { deckFilter = null }, label = { Text("Все") })
            FilterChip(selected = deckFilter == DeckSource.BASE, onClick = { deckFilter = DeckSource.BASE }, label = { Text("Базовые") })
            FilterChip(selected = deckFilter == DeckSource.DLC, onClick = { deckFilter = DeckSource.DLC }, label = { Text("DLC") })
        }
        if (matchingCards.isEmpty()) Text("Ничего не найдено. Попробуй другое название.")
        key(deckFilter, query) {
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                matchingCards.forEach { card ->
                    Column(modifier = Modifier.fillMaxWidth()
                        .then(if (onSelectCard == null) Modifier else Modifier.clickable { onSelectCard(card) })
                        .padding(vertical = 8.dp)) {
                        val source = if (card.isDlc) "DLC" else "Базовое"
                        val usage = when {
                            ActiveCardResolver.allowsMovementAfterUse(card) -> "В руку · перед движением"
                            card.isActive -> "В руку · вместо движения"
                            else -> "Сразу при подборе"
                        }
                        Text(card.displayName(), fontSize = AppFonts.inventoryCard, fontWeight = FontWeight.Bold)
                        Text("$source · $usage", fontSize = AppFonts.legend, color = AppColors.accent)
                        Text(CARD_DESCRIPTIONS.getValue(card), fontSize = AppFonts.hint, color = AppColors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun EffectChoiceDialog(choice: PendingChoice.ChooseEffect, onEffectSelected: (ActiveEffect) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Выбери эффект для кражи у ${choice.from.name}", fontSize = AppFonts.dialogTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choice.from.effects.forEach { effect ->
                    TextButton(onClick = { onEffectSelected(effect) }) {
                        Text(effect.id.displayName(), fontSize = AppFonts.dialogBody)
                    }
                }
            }
        }
    )
}

@Composable
private fun CardRevealDialog(event: CardRevealEvent, onChoice: (Boolean) -> Unit) {
    val reward = event.card.choiceReward
    AlertDialog(
        onDismissRequest = {},
        title = { Text(event.card.displayName()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Подобрал: ${event.playerName}", color = AppColors.accent)
                Text(CARD_DESCRIPTIONS.getValue(event.card))
                if (event.card.isActive) Text("Карта в руке. Её можно разыграть в начале своего хода.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text(when (reward) {
                    CardId.SHIELD -> "Получить щит"
                    CardId.SPEED -> "Получить скорость"
                    else -> "Продолжить"
                })
            }
        },
        dismissButton = {
            if (event.canCleanse) TextButton(onClick = { onChoice(true) }) { Text("Снять эффект") }
        }
    )
}
