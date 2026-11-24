package com.example.potionsanddungeons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    CardId.SPEED to "Каждая «Скорость» постоянно добавляет 1 шаг к броску. Бонусы складываются с учётом замедления.",
    CardId.SLOWDOWN to "Каждое «Замедление» постоянно отнимает 1 шаг от броска. Если после всех поправок шагов не осталось, ход пропускается.",
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
    CardId.IMMUNITY to "Блокирует одно следующее зелье из списка: «Замедление», «Зловоние», «Стан», «Сломанный щит», «Даун», «Инвалидность», «Проклятие». После этого расходуется.",
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

@Composable
internal fun GameApp(session: GameSession, windowInsets: Modifier = Modifier) {
    val updates = rememberUpdateController()
    LaunchedEffect(updates) { updates.check() }
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
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.screenBg, Color(0xFF19333D), AppColors.screenBg)) ).then(windowInsets)) {
                CompositionLocalProvider(LocalUpdates provides updates) { RootScreen(session) }
            }
        }
    }
}

internal class GameSession(private val saves: GamePreferences) {
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

    init {
        MatchRecording.read(saves.values()["match"])?.let {
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
        saves.put(mapOf("match" to match.snapshot().toString()))
    }

    internal fun perform(engine: GameEngine, action: MatchAction, first: Int, second: Int): Int {
        lanRoom?.takeIf { it.engine === engine }?.let { return it.perform(action, first, second) }
        val match = recording?.takeIf { it.engine === engine }
        if (match == null) return engine.applyAction(action, first, second)
        val result = match.perform(action, first, second)
        saves.put(mapOf("match" to match.snapshot().toString()))
        return result
    }
}

private val LocalGameSession = staticCompositionLocalOf<GameSession?> { null }
@Composable
private fun RootScreen(session: GameSession) = with(session) {
    val preferences = rememberGamePreferences("avatars")
    var avatars by remember { mutableStateOf(preferences.values()) }
    var showAvatars by rememberSaveable { mutableStateOf(false) }
    val layoutPreferences = rememberGamePreferences("board_layouts")
    var layouts by remember(layoutPreferences) {
        val saved = layoutPreferences.values()["layouts"]
        val restored = saved?.let {
            runCatching {
                val array = org.json.JSONArray(it)
                List(minOf(array.length(), BoardLayout.MAX_LAYOUTS)) { index -> readBoardLayout(array.getJSONObject(index)) }
            }.getOrNull()
        }
        mutableStateOf(restored.orEmpty())
    }
    var showLayouts by rememberSaveable { mutableStateOf(false) }
    if (showLayouts) {
        BoardLayoutsScreen(layouts, onChange = { updated ->
            val encoded = org.json.JSONArray()
            updated.forEach { encoded.put(it.toJson()) }
            layoutPreferences.put(mapOf("layouts" to encoded.toString()))
            layouts = updated
        }) { showLayouts = false }
        return@with
    }
    val cardsPreferences = rememberGamePreferences("custom_cards")
    var customCards by remember(cardsPreferences) {
        val saved = cardsPreferences.values()["cards"]
        mutableStateOf(saved?.let { runCatching { readCustomCards(org.json.JSONArray(it)) }.getOrNull() }.orEmpty())
    }
    var showCustomCards by rememberSaveable { mutableStateOf(false) }
    if (showCustomCards) {
        CustomCardsScreen(customCards, onChange = { cards ->
            cardsPreferences.put(mapOf("cards" to cards.toJson().toString()))
            customCards = cards
        }) { showCustomCards = false }
        return@with
    }
    val dicePreferences = rememberGamePreferences("dice_colors")
    val presetPreferences = rememberGamePreferences("party_presets")
    val presetLibrary = remember(presetPreferences) { PartyPresetLibrary(presetPreferences) }
    var diceColors by remember {
        val dice = dicePreferences.values()
        mutableStateOf(DiceColors(
            Color(dice["d4"]?.toIntOrNull() ?: 0xFFF0F1FA.toInt()),
            Color(dice["d6"]?.toIntOrNull() ?: 0xFFF0F1FA.toInt()),
            dice["rainbowD4"].toBoolean(),
            dice["rainbowD6"].toBoolean()
        ))
    }
    CompositionLocalProvider(LocalAvatars provides avatars, LocalDiceColors provides diceColors, LocalGameSession provides session) {
    if (!showAvatars) {
    val lesson = tutorialEngine
    val sandbox = sandboxEngine
    val room = lanRoom
    if (room != null) {
        val onlineGame = room.engine
        if (onlineGame == null) {
            BackHandler { closeLanRoom() }
            LanLobby(room, customCards, layouts, presetLibrary, onBack = ::closeLanRoom)
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
            customCards = customCards,
            layouts = layouts,
            presetLibrary = presetLibrary,
            onOpenLayouts = { showLayouts = true },
            onOpenCustomCards = { showCustomCards = true },
            onOpenAppearance = { showAvatars = true },
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
    }
    if (showAvatars) AvatarSettings(onDismiss = { showAvatars = false }, colors = diceColors, onColorsChanged = {
        diceColors = it
        dicePreferences.put(mapOf("d4" to it.d4.toArgb(), "d6" to it.d6.toArgb(),
            "rainbowD4" to it.rainbowD4, "rainbowD6" to it.rainbowD6))
    }) { key, uri ->
        avatars = avatars.toMutableMap().apply { if (uri == null) remove(key) else put(key, uri) }
        preferences.put(mapOf(key to uri))
    }
    }
}


@Composable
internal fun SetupScreen(
    dlcEnabled: Boolean,
    onDlcToggle: (Boolean) -> Unit,
    initialNames: List<String>,
    onResume: (() -> Unit)?,
    initialBotCount: Int,
    onTutorial: () -> Unit,
    onOpenCardSandbox: () -> Unit,
    onOpenLan: () -> Unit,
    customCards: List<CustomCard>,
    layouts: List<BoardLayout>,
    presetLibrary: PartyPresetLibrary,
    onOpenLayouts: () -> Unit,
    onOpenCustomCards: () -> Unit,
    onOpenAppearance: () -> Unit,
    onStart: (List<String>, Set<Int>, CustomRules) -> Unit
) {
    var showRules by rememberSaveable { mutableStateOf(false) }
    var setupPage by rememberSaveable { mutableIntStateOf(0) }
    var customParty by rememberSaveable { mutableStateOf(false) }
    var choicePotions by rememberSaveable { mutableStateOf(false) }
    var breakableDoors by rememberSaveable { mutableStateOf(false) }
    var cardTrading by rememberSaveable { mutableStateOf(false) }
    var bossDuel by rememberSaveable { mutableStateOf(false) }
    var daily by rememberSaveable { mutableStateOf(false) }
    var boardOptions by rememberSaveable(stateSaver = Saver<BoardOptions, String>(
        save = { it.toJson().toString() }, restore = { readBoardOptions(org.json.JSONObject(it)) }
    )) { mutableStateOf(BoardOptions()) }
    var showDeckEditor by rememberSaveable { mutableStateOf(false) }
    val layout = boardOptions.layout
    val solo = customParty && bossDuel
    var showCatalog by remember { mutableStateOf(false) }
    val version = appVersion()
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
    var playerCount by rememberSaveable { mutableIntStateOf(initialNames.size.coerceIn(2, 4)) }
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
    if (showRules) {
        RulesScreen { showRules = false }
        return
    }
    if (showDeckEditor) {
        DeckEditorScreen(boardOptions, customCards, dlcEnabled, choices = customParty && choicePotions, duel = solo,
            onApply = { boardOptions = it; showDeckEditor = false }, onBack = { showDeckEditor = false })
        return
    }

    if (setupPage == 0) {
        HomeMenu(
            version = version,
            onVersion = {
                versionTaps++
                if (versionTaps == 7) {
                    versionTaps = 0
                    onOpenCardSandbox()
                }
            },
            onResume = onResume,
            onNewGame = { setupPage = 1 },
            onNetwork = onOpenLan,
            onTutorial = onTutorial,
            onRules = { showRules = true },
            onCatalog = { showCatalog = true },
            onLayouts = onOpenLayouts,
            onCards = onOpenCustomCards,
            onAppearance = onOpenAppearance
        )
        return
    }

    val participants = if (solo) 1 else playerCount
    val starting = setupPage == 2
    val rules = if (customParty) {
        CustomRules(choicePotions, breakableDoors && layout == null, cardTrading && !solo, solo, daily, boardOptions)
    } else CustomRules(board = boardOptions)
    val deckProblem = boardOptions.deckProblem(dlcEnabled, rules.choicePotions, solo)
    MenuPage(if (starting) "Игроки" else "Новая игра", onBack = { setupPage-- }, footer = {
        if (starting) {
            BoardRulesSummary(boardOptions, dlcEnabled, choices = rules.choicePotions, duel = solo, extra = buildList {
                if (rules.bossDuel) add("дуэль с боссом")
                if (rules.choicePotions) add("зелья с выбором")
                if (rules.breakableDoors) add("разрушаемые двери")
                if (rules.cardTrading) add("обмен картами")
                if (rules.daily) add("партия дня")
            })
        }
        if (deckProblem != null) Text(deckProblem, color = AppColors.accent, fontSize = AppFonts.hint)
        Button(onClick = {
            if (!starting) {
                setupPage = 2
            } else {
                val finalNames = (0 until participants).map { i -> names[i].ifBlank { "Игрок " + (i + 1) } }
                val bots = if (solo) emptySet() else (playerCount - botCount until playerCount).toSet()
                onStart(finalNames, bots, rules)
            }
        }, enabled = deckProblem == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (starting) "Начать игру" else "Далее · игроки")
        }
    }) {
        if (starting) {
            MenuPanel {
                if (!solo) {
                    Text("Сколько игроков?", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (2..4).forEach { count ->
                            FilterChip(selected = playerCount == count, onClick = {
                                playerCount = count
                                botCount = botCount.coerceAtMost(count - 1)
                            }, label = { Text("$count") })
                        }
                    }
                    Text("Боты", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until playerCount).forEach { count ->
                            FilterChip(selected = botCount == count, onClick = { botCount = count }, label = { Text("$count") })
                        }
                    }
                }
                for (index in 0 until participants) {
                    val isBot = !solo && index >= playerCount - botCount
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(14.dp).background(playerPalette[index], CircleShape))
                        OutlinedTextField(
                            value = names[index],
                            onValueChange = { value -> names = names.mapIndexed { i, name -> if (i == index) value else name } },
                            label = { Text((if (isBot) "Имя бота " else "Имя игрока ") + (index + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            MenuPanel("Поле и колода") {
                BoardOptionsEditor(boardOptions, customCards, layouts) { boardOptions = it }
                HorizontalDivider(color = AppColors.boardBg)
                CustomRuleSwitch("Дополнительные зелья (DLC)", dlcEnabled, onDlcToggle)
                OutlinedButton(onClick = { showDeckEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (boardOptions.deckRecipe == null) "Настроить колоду" else "Изменить колоду")
                }
            }
            MenuPanel("Режим партии") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !customParty, onClick = { customParty = false }, label = { Text("Обычная") })
                    FilterChip(selected = customParty, onClick = { customParty = true }, label = { Text("Кастомная") })
                }
                if (customParty) {
                    CustomRuleSwitch("Дуэль с боссом", bossDuel) { bossDuel = it }
                    CustomRuleSwitch("Зелья с выбором", choicePotions) { choicePotions = it }
                    if (layout == null) CustomRuleSwitch("Разрушаемые двери", breakableDoors) { breakableDoors = it }
                    if (!solo) CustomRuleSwitch("Обмен картами", cardTrading) { cardTrading = it }
                    CustomRuleSwitch("Партия дня", daily) { daily = it }
                }
            }
            PartyPresetControls(presetLibrary, dlcEnabled, rules, onApply = { preset ->
                val saved = preset.rules
                boardOptions = saved.board
                onDlcToggle(preset.dlc)
                customParty = saved != CustomRules(board = saved.board)
                choicePotions = saved.choicePotions
                breakableDoors = saved.breakableDoors
                cardTrading = saved.cardTrading
                bossDuel = saved.bossDuel
                daily = saved.daily
            })
        }
    }

}
@Composable
internal fun CustomRuleSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
    cardSandbox: Boolean = false, lan: LanRoom? = null, playtest: Boolean = false
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(400)) }

    val scope = rememberCoroutineScope()
    var showInventoryFor by remember { mutableStateOf<Player?>(null) }
    var playerDetails by remember(engine) { mutableStateOf<Player?>(null) }
    var hoveredCell by remember(engine) { mutableStateOf<Cell?>(null) }
    var attackPreview by remember(engine) { mutableStateOf<Pair<Player, CardId>?>(null) }
    var showCardCatalog by remember { mutableStateOf(false) }
    var showSandboxCards by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showGameMenu by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showRecap by rememberSaveable { mutableStateOf(false) }
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
    if (showRecap && engine.gameOver) {
        BackHandler { showRecap = false }
        MatchRecapScreen(engine) { showRecap = false }
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
                val slow = player?.let { engine.movementEffectCount(it, CardId.SLOWDOWN) } ?: 0
                val fast = player?.let { engine.movementEffectCount(it, CardId.SPEED) } ?: 0
                delay(900)
                val finalSteps = perform(if (reroll) MatchAction.REROLL else MatchAction.ROLL_INLINE, 0, 0)
                diceResult = engine.lastDiceRoll
                delay(700)
                if (!engine.awaitingRollDecision && (slow > 0 || fast > 0)) {
                    diceCaption = if (finalSteps == 0) "Замедление: ход пропущен" else
                        listOfNotNull(if (slow > 0) "−$slow Замедление" else null, if (fast > 0) "+$fast Скорость" else null).joinToString(" · ")
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
    MovementKeyHandler { direction ->
        if (diceVisible || lan?.canAct == false || attackPreview != null || showInventoryFor != null || playerDetails != null ||
            showGameMenu || showLog || showRules || confirmRestart
        ) {
            false
        } else {
            val target = engine.directionalMoveTarget(direction)
            if (target != null) {
                perform(MatchAction.CELL, target.row, target.col)
                true
            } else false
        }
    }
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

    val current = engine.turnOrder.getOrNull(engine.currentTurnIndex)
    val handOwner = (if (lan == null) current?.takeUnless { it.isBot } else engine.players.getOrNull(lan.seat))
        ?.takeIf { it.activeCards.isNotEmpty() }
    val stepPreview = hoveredCell?.takeIf { !diceVisible && localInput && attackPreview == null }
        ?.let(engine::previewStep)

    MatchTableLayout(
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = entrance.value }.padding(horizontal = 12.dp, vertical = 8.dp),
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TurnGuide(engine, lan, diceVisible, stepPreview, Modifier.weight(1f))
                if (playtest) {
                    OutlinedButton(onClick = onOpenSettings) { Text("В редактор") }
                } else {
                    OutlinedButton(onClick = { showGameMenu = true }, enabled = !diceVisible) { Text("Меню") }
                }
            }
            if (cardSandbox) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !diceVisible && attackPreview == null && engine.canTestCard,
                        onClick = { showSandboxCards = true }) { Text("Выдать карту") }
                    OutlinedButton(onClick = onRestart) { Text("Сбросить полигон") }
                }
            }
        },
        board = {
            BoardView(engine, attackPreview, inputEnabled = !diceVisible && localInput,
                onlineAvatars = lan?.avatars, onHover = { cell, hovered ->
                    if (hovered) hoveredCell = cell else if (hoveredCell == cell) hoveredCell = null
                })
            if (diceVisible) key(diceStage) { DiceRollOverlay(diceResult, diceCaption, diceSides) }
            if (!diceVisible && lan?.paused != true && engine.d4Rolls.isNotEmpty()) {
                key(engine.d4Sequence) {
                    D4RollOverlay(engine.d4Rolls.first()) {
                        if (driveGame) perform(MatchAction.D4, 0, 0)
                    }
                }
            }
        },
        roster = { detailed ->
            MatchRoster(engine, lan?.avatars, detailed) { player ->
                if (!diceVisible && attackPreview == null) {
                    if (engine.pendingChoice is PendingChoice.ChoosePlayer) {
                        if (localInput && !botTurn) perform(MatchAction.PLAYER, engine.players.indexOf(player), 0)
                    } else {
                        playerDetails = player
                    }
                }
            }
        },
        controls = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (lan != null && !lan.hosting && !lan.connected) {
                    OutlinedButton(onClick = lan::reconnect, enabled = !lan.busy) { Text("Переподключиться") }
                }
                val preview = attackPreview
                when {
                    preview != null -> {
                        val (player, card) = preview
                        Text(card.displayName(), fontWeight = FontWeight.Bold)
                        Text(
                            if (card == CardId.ATTACK) "Золотым отмечены соседние клетки с целями."
                            else "Направление определит д4 по номерам стен: 1 ↑  2 →  3 ↓  4 ←.",
                            color = AppColors.textMuted, fontSize = AppFonts.hint
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = localInput && engine.canPlayActiveCard(player, card), onClick = {
                                attackPreview = null
                                perform(MatchAction.CARD, engine.players.indexOf(player), card.ordinal)
                            }) { Text("Сыграть карту") }
                            OutlinedButton(onClick = { attackPreview = null }) { Text("Отмена") }
                        }
                    }
                    tutorialFinished -> Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Завершить обучение")
                    }
                    engine.gameOver -> {
                        Button(onClick = { showRecap = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Разбор партии")
                        }
                        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                            Text(if (lan == null) "Новая игра" else "Покинуть комнату")
                        }
                        if (lan?.hosting == true) {
                            Button(onClick = lan::rematch, enabled = lan.canRematch, modifier = Modifier.fillMaxWidth()) {
                                Text("Играть снова")
                            }
                        }
                    }
                    else -> {
                        if (localInput && !botTurn && engine.pendingSteps == 0 && !engine.hasPendingAction &&
                            !engine.awaitingBossMove && !engine.awaitingRollDecision &&
                            (engine.mustSkipCurrentTurn() || !engine.canMoveAsKnight())) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    DiceButton(engine, diceVisible ||
                                        engine.tutorial && engine.bossMoveTrigger == 1 && !engine.attackCardPlayed) {
                                        rollWithAnimation()
                                    }
                                }
                            }
                        }
                        if (localInput && !diceVisible && engine.canReroll && !botTurn) {
                            OutlinedButton(onClick = { rollWithAnimation(reroll = true) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Перебросить кубик")
                            }
                        }
                        if (localInput && !diceVisible && engine.awaitingRollDecision) {
                            TextButton(onClick = { perform(MatchAction.ACCEPT, 0, 0) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Оставить бросок (" + engine.lastDiceRoll + ")")
                            }
                        }
                        if (handOwner != null && !diceVisible && !engine.hasPendingAction) {
                            OutlinedButton(onClick = { showInventoryFor = handOwner }) {
                                Text("Рука · " + handOwner.activeCards.size)
                            }
                        }
                    }
                }
            }
        }
    )

    val blockingChoice = engine.cardReveal != null || engine.pendingChoice != null ||
        engine.d4Rolls.isNotEmpty() || engine.tradePartners.isNotEmpty()
    LaunchedEffect(blockingChoice) { if (blockingChoice) playerDetails = null }
    playerDetails?.takeUnless { blockingChoice }?.let { player ->
        val maySeeHand = !player.isBot && player.activeCards.isNotEmpty() &&
            (lan == null || engine.players.getOrNull(lan.seat) === player)
        MatchPlayerSheet(player, onDismiss = { playerDetails = null },
            onOpenHand = if (maySeeHand) ({
                playerDetails = null
                showInventoryFor = player
            }) else null)
    }

    LaunchedEffect(blockingChoice) { if (blockingChoice) showInventoryFor = null }
    showInventoryFor?.takeUnless { blockingChoice }?.let { player ->
        InventoryDialog(engine, player, onDismiss = { showInventoryFor = null },
            networkBlock = if (!localInput) (if (lan?.paused == true) lan.status else "Дождись своей очереди.") else null,
            onPreview = { card ->
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
                TextButton(onClick = { showGameMenu = false; showCardCatalog = true }) { Text("Справочник зелий") }
                TextButton(onClick = { showGameMenu = false; showLog = true }) { Text("Журнал партии") }
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
        if (event.bot && event.choiceReward != null) {
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
private fun TurnGuide(engine: GameEngine, lan: LanRoom?, rolling: Boolean, preview: StepPreview?, modifier: Modifier = Modifier) {
    val current = engine.turnOrder.getOrNull(engine.currentTurnIndex)
    val seat = lan?.let { engine.players.getOrNull(it.seat) }
    val heading = when {
        engine.gameOver -> "Итог партии"
        lan?.connected == false || lan?.paused == true -> "Партия на паузе"
        rolling || engine.d4Rolls.isNotEmpty() -> "Бросок кубика"
        engine.cardReveal != null -> "Зелье подобрано"
        engine.awaitingBossMove -> "Ход босса"
        lan != null && seat !== current -> "Ждём ход ${current?.name.orEmpty()}"
        engine.pendingChoice != null -> "Выбери цель"
        engine.tutorial && current === engine.players.firstOrNull() -> "Твой ход"
        seat != null && seat === current -> "Твой ход"
        else -> current?.name?.let { "Ходит $it" }.orEmpty()
    }
    val steps = if (!engine.gameOver && lan?.connected != false && lan?.paused != true &&
        !rolling && !engine.awaitingBossMove && !engine.hasPendingAction && engine.pendingSteps > 0 &&
        (lan == null || seat === current)
    ) " · шагов: ${engine.pendingSteps}" else ""
    val instruction = when {
        engine.gameOver -> engine.statusText
        lan?.connected == false || lan?.paused == true -> lan.status
        preview != null -> buildList {
            if (preview.slips) add("Кожурка прервёт движение")
            else if (preview.picksPotion) add("Подберёшь зелье")
            if (preview.endsMovement && !preview.slips) add("Движение закончится")
            else if (!preview.endsMovement) add("Можно идти дальше")
            if (preview.risksAttack) add("Риск атаки босса")
        }.joinToString(" · ")
        engine.tutorial && engine.bossMoveTrigger >= 2 && !engine.awaitingBossMove -> "Обучение пройдено."
        engine.tutorial && engine.pendingChoice != null -> "Выбери клетку босса, чтобы оглушить его."
        engine.tutorial && engine.attackCardPlayed && engine.pendingSteps > 0 -> "Отойди от босса на подсвеченную клетку."
        engine.tutorial && engine.pendingSteps > 0 && current?.isBot == false -> "Нажми на клетку с зельем. Оно попадёт в руку."
        engine.tutorial && engine.bossMoveTrigger == 1 && !engine.attackCardPlayed && current?.isBot == false && !engine.awaitingBossMove -> "Открой «Рука» и разыграй «Атаку»."
        rolling || engine.awaitingBossMove -> null
        seat != null && seat !== current -> "Ты играешь за ${seat.name}"
        current?.isBot == true -> null
        engine.hasPendingAction -> engine.statusText
        engine.mustSkipCurrentTurn() -> "Нужно пропустить ход"
        engine.canMoveAsKnight() -> "Выбери выделенную клетку буквой «Г»."
        current?.effects?.has(CardId.HORSE) == true -> "Доступных клеток для коня нет. Пропусти ход."
        engine.duelExit != null && engine.duelKeys.isEmpty() -> "Все ключи собраны · закончи движение на выходе"
        engine.duelExit != null -> "Ключи: ${3 - engine.duelKeys.size}/3"
        else -> null
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading + steps, color = AppColors.accent, fontSize = AppFonts.sectionLabel,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(instruction.orEmpty(), color = AppColors.textPrimary, fontSize = AppFonts.hint,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (engine.bossUpgradesEnabled) {
            val next = engine.nextBossUpgrade
            val upgrade = when {
                engine.gameOver -> ""
                next == null -> "Босс получил оба усиления"
                else -> "${next.label} · осталось ходов босса: ${next.completedRounds - engine.completedBossRounds}"
            }
            Text(upgrade, color = AppColors.textMuted, fontSize = AppFonts.legend,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val ruleSections = listOf(
    "Цель партии" to "Останься последним игроком на поле.",
    "Ход" to ("Брось кубик и двигайся по выделенным клеткам по горизонтали или вертикали. Сразу возвращаться на предыдущую клетку нельзя." +
        if (supportsMovementKeys) " На ПК можно ходить клавишами WASD. Для хода конём выбери клетку мышью." else ""),
    "Зелья и карты" to "Закончи движение на клетке с флаконом, чтобы подобрать зелье. Круглые флаконы относятся к базовой колоде, угловатые — к DLC. Карты из руки разыгрываются в начале хода. После «Атаки», «Пикси» и «Тарана» можно походить.",
    "Босс" to "Босс атакует в свой ход. Красные клетки показывают зону атаки. При игре с DLC клетку босса нужно обходить.",
    "Подсказки на поле" to ("Бирюзовый контур показывает доступный ход, золотой — цель или клетку, где подберёшь зелье. " +
        "Значок «+» обозначает подбор зелья, «✓» — конец движения, «!» — риск атаки. Цель также можно выбрать в списке игроков." +
        if (supportsMovementKeys) " Наведи указатель на доступную клетку, чтобы увидеть подсказку над полем." else "")
)

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    MenuPage("Как играть", onBack) {
        ruleSections.forEach { (title, description) -> MenuPanel(title) { Text(description) } }
    }
}

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Как играть") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ruleSections.forEach { (title, description) ->
                    Text(title, color = AppColors.accent, fontWeight = FontWeight.Bold)
                    Text(description)
                }
            }
        })
}

@Composable
private fun DiceButton(engine: GameEngine, isRolling: Boolean, onRoll: () -> Unit) {

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
private val boardFrameInset = 8.dp

@Composable
internal fun BoardView(
    engine: GameEngine, attackPreview: Pair<Player, CardId>? = null, inputEnabled: Boolean = true,
    onlineAvatars: Map<Int, LanAvatar>? = null, onHover: (Cell, Boolean) -> Unit = { _, _ -> }
) {
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
        val size = engine.boardSize
        val density = LocalDensity.current
        val inset = with(density) { boardFrameInset.roundToPx().toDp() }
        val gap = with(density) { cellGap.roundToPx().toDp() }
        val step = with(density) {
            ((available.roundToPx() - 2 * inset.roundToPx() + gap.roundToPx()).coerceAtLeast(0) / size).toDp()
        }
        val cellSize = (step - gap).coerceAtLeast(0.dp)
        val boardSize = (step * (size - 1) + cellSize + inset * 2).coerceAtMost(available)
        val mid = size / 2

        val frame = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(boardSize)
                .shadow(14.dp, frame)
                .background(Brush.radialGradient(listOf(Color(0xFF304653), Color(0xFF101C27))), frame)
                .border(1.dp, AppColors.wallNumber.copy(alpha = 0.22f), frame)
                .padding(inset)
        ) {
            for (r in 0 until size) {
                for (c in 0 until size) {
                    val cell = Cell(r, c)
                    if (!engine.isBoardCell(cell)) continue
                    val selectable = inputEnabled && attackPreview == null && engine.isSelectableBoardCell(cell)
                    val dangerous = cell in attackZone
                    val door = cell in engine.doors
                    val duelKey = cell in engine.duelKeys
                    val duelExit = cell == engine.duelExit
                    val potion = engine.potions.firstOrNull { it.position == cell }
                    val shape = RoundedCornerShape(6.dp)
                    val stepPreview = if (selectable) engine.previewStep(cell) else null
                    val risky = stepPreview?.risksAttack == true
                    val outline = if (engine.pendingChoice != null || stepPreview?.picksPotion == true) AppColors.cellHighlight else AppColors.moveHighlight
                    val hoverModifier = if (supportsMovementKeys && stepPreview != null) {
                        val source = remember(cell) { MutableInteractionSource() }
                        val hovered by source.collectIsHoveredAsState()
                        val updateHover by rememberUpdatedState(onHover)
                        if (hovered) DisposableEffect(cell) {
                            updateHover(cell, true)
                            onDispose { updateHover(cell, false) }
                        }
                        Modifier.hoverable(source)
                    } else Modifier
                    val tileColor by animateColorAsState(
                        when {
                            door -> Color(0xFF674B32)
                            dangerous -> AppColors.bossAttackZone
                            selectable -> Color(0xFF30584F)
                            (r + c) % 2 == 0 -> AppColors.cellBg
                            else -> Color(0xFF263544)
                        },
                        tween(240)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = step * c, y = step * r)
                            .size(cellSize)
                            .then(hoverModifier)
                            .background(Brush.verticalGradient(listOf(
                                androidx.compose.ui.graphics.lerp(tileColor, Color.White, 0.045f),
                                androidx.compose.ui.graphics.lerp(tileColor, Color.Black, 0.14f)
                            )), shape)
                            .then(when {
                                cell in previewCells -> Modifier.border(2.dp, AppColors.cellHighlight, shape)
                                selectable -> Modifier.border(2.dp, outline, shape)
                                else -> Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                            })
                            .semantics {
                                contentDescription = buildString {
                                    append("Ряд ${r + 1}, столбец ${c + 1}")
                                    if (door) append(", закрытая дверь")
                                    if (duelKey) append(", ключ")
                                    if (duelExit) append(", выход")
                                    if (dangerous) append(", зона атаки")
                                    if (risky) append(", босс может достать в конце раунда")
                                    if (stepPreview?.endsMovement == true) append(", движение закончится")
                                    if (stepPreview?.picksPotion == true) append(", подберёшь зелье")
                                    if (cell in engine.bananaPeels) append(", кожурка")
                                    if (potion != null) append(if (potion.revealed) ", ${potion.card.name}" else ", закрытое зелье")
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
                        if (stepPreview?.endsMovement == true) {
                            Text(if (stepPreview.picksPotion) "+" else "✓",
                                color = outline, fontSize = AppFonts.legend, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp))
                        }
                    }
                }
            }

            Box(modifier = Modifier.offset(x = step * mid, y = 0.dp).size(cellSize), contentAlignment = Alignment.TopCenter) {
                Text("1", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            Box(modifier = Modifier.offset(x = step * mid, y = step * (size - 1)).size(cellSize), contentAlignment = Alignment.BottomCenter) {
                Text("3", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
            }
            Box(modifier = Modifier.offset(x = 0.dp, y = step * mid).size(cellSize), contentAlignment = Alignment.CenterStart) {
                Text("4", color = AppColors.wallNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
            }
            Box(modifier = Modifier.offset(x = step * (size - 1), y = step * mid).size(cellSize), contentAlignment = Alignment.CenterEnd) {
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

            engine.potions.forEach { potion -> key(potion) { PotionView(potion, step, cellSize, size) } }

            engine.players.filter { it.alive }.forEach { player ->
                val idx = engine.players.indexOf(player)
                val color = playerPalette[idx % playerPalette.size]
                val isCurrent = engine.turnOrder.getOrNull(engine.currentTurnIndex) == player && !engine.gameOver
                key(player) {
                    PlayerView(player, color, isCurrent, step, cellSize,
                        avatarKey = if (onlineAvatars == null) "player$idx" else null,
                        onlineAvatar = onlineAvatars?.get(idx))
                }
            }

            engine.bosses.forEach { boss -> key(boss.slot) { BossView(boss, step, cellSize) } }
        }
    }
}



@Composable
internal fun PlayerView(
    player: Player, color: Color, isCurrent: Boolean, step: androidx.compose.ui.unit.Dp,
    cellSize: androidx.compose.ui.unit.Dp, avatarKey: String?, onlineAvatar: LanAvatar?
) {
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
            avatarKey?.let { AvatarImage(it, Modifier.fillMaxSize()) }
            onlineAvatar?.let { LanAvatarImage(it, Modifier.fillMaxSize()) }
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
fun PotionView(potion: Potion, step: androidx.compose.ui.unit.Dp, cellSize: androidx.compose.ui.unit.Dp, boardSize: Int = BOARD_SIZE) {
    val scale = remember { Animatable(1f) }
    val x by animateDpAsState(step * potion.position.col, tween(280))
    val y by animateDpAsState(step * potion.position.row, tween(280))
    LaunchedEffect(potion.usedTrigger) {
        if (potion.usedTrigger > 0) {
            scale.snapTo(1.22f)
            scale.animateTo(1f, animationSpec = tween(350))
        }
    }
    Box(
        modifier = Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
            .size(cellSize).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = -(scale.value - 1f) * size.height * 0.6f
            },

        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(minOf(36.dp, cellSize * 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            if (potion.revealed) {
                Text(potion.card.name, color = Color.White, fontSize = 7.sp)
            } else {
                PotionIcon(potion.source, (potion.position.row * boardSize + potion.position.col) % 4, Modifier.fillMaxSize())
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
fun InventoryDialog(
    engine: GameEngine, player: Player, onDismiss: () -> Unit, onPreview: (CardId) -> Unit,
    networkBlock: String? = null
) {
    val perform = matchActions(engine)
    var selectedRestriction by remember(player) { mutableStateOf<CardId?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", fontSize = AppFonts.buttonText) } },
        title = { Text("Инвентарь ${player.name}", fontSize = AppFonts.dialogTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                player.activeCards.groupingBy { it }.eachCount().forEach { (card, count) ->
                    val restriction = engine.activeCardUnavailableReason(player, card) ?: networkBlock
                    val movement = ActiveCardResolver.allowsMovementAfterUse(card)
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        val title = if (count == 1) card.displayName() else "${card.displayName()} ×$count"
                        Text(title, fontSize = AppFonts.inventoryCard, fontWeight = FontWeight.Bold)
                        Text(if (movement) "После карты можно ходить" else "Карта завершает ход",
                            color = AppColors.accent, fontSize = AppFonts.legend)
                        Text(CARD_DESCRIPTIONS.getValue(card), fontSize = AppFonts.hint, color = AppColors.textMuted)
                        TextButton(onClick = {
                            if (restriction != null) {
                                selectedRestriction = if (selectedRestriction == card) null else card
                            } else if (movement) {
                                onPreview(card)
                            } else {
                                perform(MatchAction.CARD, engine.players.indexOf(player), card.ordinal)
                                onDismiss()
                            }
                        }) { Text(if (restriction == null) "Сыграть карту" else "Почему недоступна?") }
                        if (selectedRestriction == card && restriction != null) {
                            Text(restriction, color = AppColors.accent, fontSize = AppFonts.hint)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CardCatalogScreen(onSelectCard: ((CardId) -> Unit)? = null, pickupOnly: Boolean = false, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var deckFilter by rememberSaveable { mutableStateOf<DeckSource?>(null) }
    val matchingCards = CardId.entries.filter { card ->
        (!pickupOnly || !card.isActive) && (deckFilter == null || card.isDlc == (deckFilter == DeckSource.DLC)) &&
            card.displayName().contains(query.trim(), ignoreCase = true)
    }
    BackHandler(onBack = onDismiss)
    MenuPage(if (onSelectCard == null) "Справочник зелий" else "Выбери карту", onDismiss, scroll = false) {
        OutlinedTextField(query, { query = it }, label = { Text("Поиск по названию") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = deckFilter == null, onClick = { deckFilter = null }, label = { Text("Все") })
            FilterChip(selected = deckFilter == DeckSource.BASE, onClick = { deckFilter = DeckSource.BASE }, label = { Text("Базовые") })
            FilterChip(selected = deckFilter == DeckSource.DLC, onClick = { deckFilter = DeckSource.DLC }, label = { Text("DLC") })
        }
        if (matchingCards.isEmpty()) Text("Ничего не найдено. Попробуй другое название.")
        key(deckFilter, query) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matchingCards, key = { it.name }) { card ->
                    val click = if (onSelectCard == null) Modifier else Modifier
                        .clip(RoundedCornerShape(18.dp)).clickable { onSelectCard(card) }
                    MenuPanel(modifier = click) {
                        val source = if (card.isDlc) "DLC" else "Базовое"
                        val usage = when {
                            ActiveCardResolver.allowsMovementAfterUse(card) -> "В руку · перед движением"
                            card.isActive -> "В руку · вместо движения"
                            else -> "Сразу при подборе"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PotionIcon(if (card.isDlc) DeckSource.DLC else DeckSource.BASE,
                                card.ordinal % 4, Modifier.size(32.dp))
                            Column {
                                Text(card.displayName(), fontWeight = FontWeight.Bold)
                                Text("$source · $usage", fontSize = AppFonts.legend, color = AppColors.accent)
                            }
                        }
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
    val reward = event.choiceReward
    val custom = event.customCard
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (event.effectIndex == null) custom?.name ?: event.card.displayName() else event.card.displayName()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.effectIndex != null && custom != null) {
                    Text("${custom.name} · эффект ${event.effectIndex + 1} из ${custom.effects.size}", color = AppColors.accent)
                } else {
                    Text("Подобрал: ${event.playerName}", color = AppColors.accent)
                    custom?.flavor?.takeIf { it.isNotBlank() }?.let { Text(it, color = AppColors.textMuted) }
                }
                if (event.showsAllEffects && custom != null) {
                    custom.effects.forEachIndexed { index, effect ->
                        Text("${index + 1}. ${effect.displayName()}", fontWeight = FontWeight.Bold)
                        Text(CARD_DESCRIPTIONS.getValue(effect))
                    }
                } else Text(CARD_DESCRIPTIONS.getValue(event.card))
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
