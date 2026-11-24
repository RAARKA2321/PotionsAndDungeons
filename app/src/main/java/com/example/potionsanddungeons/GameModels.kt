package com.example.potionsanddungeons

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import kotlin.random.Random

data class Cell(val row: Int, val col: Int)

enum class Direction(val dr: Int, val dc: Int) {
    UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1)
}

fun Direction.opposite(): Direction = when (this) {
    Direction.UP -> Direction.DOWN
    Direction.DOWN -> Direction.UP
    Direction.LEFT -> Direction.RIGHT
    Direction.RIGHT -> Direction.LEFT
}

enum class Wall(val number: Int, val vector: Cell) {
    TOP(1, Cell(-1, 0)), RIGHT(2, Cell(0, 1)), BOTTOM(3, Cell(1, 0)), LEFT(4, Cell(0, -1));
    companion object {
        fun byNumber(n: Int) = entries.first { it.number == n }
        fun directionByNumber(n: Int): Direction {
            val vector = byNumber(n).vector
            return Direction.entries.single { it.dr == vector.row && it.dc == vector.col }
        }
    }
}

const val BOARD_SIZE = 5
val CENTER = boardCenter(BOARD_SIZE)
val CORNERS = boardCorners(BOARD_SIZE)

fun boardCenter(size: Int) = Cell(size / 2, size / 2)

fun boardCorners(size: Int): List<Cell> {
    val edge = size - 1
    return listOf(Cell(0, 0), Cell(edge, edge), Cell(0, edge), Cell(edge, 0))
}

fun diagonalCellsFromCorner(corner: Cell, boardSize: Int = BOARD_SIZE): List<Cell> {
    val dr = if (corner.row == 0) 1 else -1
    val dc = if (corner.col == 0) 1 else -1
    val cells = mutableListOf<Cell>()
    var r = corner.row + dr
    var c = corner.col + dc
    val center = boardCenter(boardSize)
    while (Cell(r, c).inBounds(boardSize) && Cell(r, c) != center) {
        cells.add(Cell(r, c))
        r += dr
        c += dc
    }
    return cells
}

fun Cell.inBounds(boardSize: Int = BOARD_SIZE) = row in 0 until boardSize && col in 0 until boardSize

fun Cell.neighbors8(boardSize: Int = BOARD_SIZE): List<Cell> {
    val result = mutableListOf<Cell>()
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val n = Cell(row + dr, col + dc)
        if (n.inBounds(boardSize)) result.add(n)
    }
    return result
}

fun Cell.neighbors4(boardSize: Int = BOARD_SIZE) =
    Direction.entries.map { Cell(row + it.dr, col + it.dc) }.filter { it.inBounds(boardSize) }

interface BoardActor {
    var position: Cell
    val effects: MutableList<ActiveEffect>
    val displayName: String
    var stunnedTurns: Int
}

// ---------- Карты ----------

enum class DeckSource { BASE, DLC }

enum class CardId(val isDlc: Boolean, val isActive: Boolean = false, val display: String) {
    SPEED(false, display = "Скорость"),
    SLOWDOWN(false, display = "Замедление"),
    SHIELD(false, display = "Щит"),
    BROKEN_SHIELD(false, display = "Сломанный щит"),
    INVISIBILITY(false, display = "Невидимость"),
    STENCH(false, display = "Зловоние"),
    STUN(false, display = "Стан"),
    WEREWOLF(false, display = "Оборотень"),
    TIME_REWIND(false, display = "Откат времени"),
    BOSS_SPEED_UP(false, display = "Ускорение босса"),
    BOSS_ATTACK_UP(false, display = "Усиленная атака босса"),
    BODY_SWAP(false, display = "Смена телами"),
    EFFECT_SWAP(false, display = "Свап эффекта"),
    REPEL(false, display = "Отпугивание"),
    HYPNOSIS(false, display = "Гипноз"),
    IMMUNITY(false, display = "Иммунитет"),
    BOSS_DUPLICATION(false, display = "Удвоение босса"),
    BOSS_BLINDNESS(false, display = "Слепота босса"),

    TELEPORT(true, display = "Телепорт"),
    DAUN(true, display = "Даун"),
    ATTRACTION(true, display = "Притяжение"),
    HORSE(true, display = "Конь"),
    BFF(true, display = "БФФ"),
    DISABILITY(true, display = "Инвалидность"),
    LET_THERE_BE_LIGHT(true, display = "Да будет свет"),
    CURSE(true, display = "Проклятие"),
    POTION_BOX(true, display = "Ящик зелий"),
    FREEDOM(true, display = "Свобода"),
    CUCKOLD(true, display = "Куколд"),
    PUPPETEER(true, display = "Кукловод"),
    ROAR_OF_ALPHA(true, display = "Рев альфы"),
    CHAOS_ROLL(true, display = "Хаос-бросок"),

    ATTACK(true, isActive = true, display = "Атака"),
    MAGNET(true, isActive = true, display = "Магнит"),
    PIXIE(true, isActive = true, display = "Пикси"),
    BANANA_PEEL(true, isActive = true, display = "Кожурка от банана"),
    TARAN(true, isActive = true, display = "Таран"),
    THIEF(true, isActive = true, display = "Вор"),
    CLEANSE(true, display = "Очистка"),
    CHOICE_SHIELD(false, display = "Зелье защиты"),
    CHOICE_SPEED(false, display = "Зелье быстроты");

    val choiceReward: CardId?
        get() = when (this) {
            CHOICE_SHIELD -> SHIELD
            CHOICE_SPEED -> SPEED
            else -> null
        }
}

data class CustomRules(
    val choicePotions: Boolean = false,
    val breakableDoors: Boolean = false,
    val cardTrading: Boolean = false,
    val bossDuel: Boolean = false,
    val daily: Boolean = false,
    val board: BoardOptions = BoardOptions()
)

enum class PotionPlacement(val label: String) {
    STANDARD("Обычное поле"),
    RANDOM_REPLACEMENT("Случайное появление"),
    EVERY_CELL("Зелье на каждой клетке")
}

data class BoardOptions(
    val size: Int = BOARD_SIZE,
    val potionPlacement: PotionPlacement = PotionPlacement.STANDARD,
    val customCards: List<CustomCard> = emptyList(),
    val layout: BoardLayout? = null,
    val deckRecipe: DeckRecipe? = null,
    val bossUpgrades: Boolean = false
) {
    init {
        require(size == BOARD_SIZE || size == 7)
        require(customCards.size <= CustomCard.MAX_CARDS)
        require(layout == null || layout.size == size)
    }
}

internal enum class BossUpgrade(val completedRounds: Int, val label: String) {
    SPEED(8, "Скорость"),
    ATTACK(12, "Усиленная атака")
}


data class CustomCard(val name: String, val effects: List<CardId>, val flavor: String = "") {
    constructor(name: String, effect: CardId, flavor: String = "") : this(name, listOf(effect), flavor)

    val effect: CardId get() = effects.first()

    init {
        require(name.isNotBlank() && name.length <= MAX_NAME && flavor.length <= MAX_FLAVOR)
        require(effects.size in 1..MAX_EFFECTS && effects.none { it.isActive })
    }

    companion object {
        const val MAX_CARDS = 12
        const val MAX_NAME = 40
        const val MAX_FLAVOR = 160
        const val MAX_EFFECTS = 5
    }
}

data class PotionCard(val effect: CardId, val custom: CustomCard? = null) {
    val name: String get() = custom?.name ?: effect.displayName()
}

fun CardId.displayName() = display

val CARD_QUANTITIES: Map<CardId, Int> = mapOf(
    CardId.SPEED to 4, CardId.SLOWDOWN to 4, CardId.SHIELD to 4, CardId.BROKEN_SHIELD to 2,
    CardId.INVISIBILITY to 4, CardId.STENCH to 4, CardId.STUN to 4, CardId.WEREWOLF to 4,
    CardId.TIME_REWIND to 4, CardId.BOSS_SPEED_UP to 3, CardId.BOSS_ATTACK_UP to 3,
    CardId.BODY_SWAP to 2, CardId.EFFECT_SWAP to 2, CardId.REPEL to 2, CardId.HYPNOSIS to 2,
    CardId.IMMUNITY to 1, CardId.BOSS_DUPLICATION to 1, CardId.BOSS_BLINDNESS to 1,

    CardId.TELEPORT to 3, CardId.DAUN to 4, CardId.ATTRACTION to 4, CardId.HORSE to 4,
    CardId.BFF to 2, CardId.DISABILITY to 2, CardId.LET_THERE_BE_LIGHT to 1, CardId.CURSE to 1,
    CardId.POTION_BOX to 1, CardId.FREEDOM to 1, CardId.CUCKOLD to 1, CardId.PUPPETEER to 1,
    CardId.ROAR_OF_ALPHA to 1, CardId.CHAOS_ROLL to 1,

    CardId.ATTACK to 4, CardId.MAGNET to 2, CardId.PIXIE to 3, CardId.BANANA_PEEL to 4,
    CardId.TARAN to 3, CardId.THIEF to 4, CardId.CLEANSE to 1
)

enum class EffectCategory { INSTANT, TEMPORARY, PERMANENT, PENDING_CONDITIONAL }
enum class PendingCondition { UNTIL_SHIELD_APPEARS, UNTIL_POSITIVE_EFFECT_APPEARS }

class ActiveEffect(
    val id: CardId,
    val category: EffectCategory,
    turnsLeft: Int = 0,
    var waitingFor: PendingCondition? = null,
    var justAcquired: Boolean = true
) {
    var turnsLeft by mutableIntStateOf(turnsLeft)
}

fun MutableList<ActiveEffect>.has(id: CardId) = any { it.id == id }
fun MutableList<ActiveEffect>.get(id: CardId) = find { it.id == id }

// ---------- Колода ----------

class PotionDeck(
    val dlcEnabled: Boolean, private val random: Random = Random, choicePotions: Boolean = false,
    duel: Boolean = false, customCards: List<CustomCard> = emptyList(), private val recipe: DeckRecipe? = null
) {
    private val baseDraw = mutableListOf<PotionCard>()
    private val baseDiscard = mutableListOf<PotionCard>()
    private val dlcDraw = mutableListOf<PotionCard>()
    private val dlcDiscard = mutableListOf<PotionCard>()
    private val basePool: List<PotionCard>
    private val dlcPool: List<PotionCard>

    init {
        val contents = deckContents(dlcEnabled, choicePotions, duel, customCards, recipe)
        contents.stock.forEach { (card, count) ->
            if (card.choiceReward == null) {
                val pile = if (card.isDlc) dlcDraw else baseDraw
                repeat(count) { pile.add(PotionCard(card)) }
            }
        }
        repeat(choiceDeckCards.maxOf { contents.stock[it] ?: 0 }) { index ->
            choiceDeckCards.forEach { card ->
                if (index < (contents.stock[card] ?: 0)) baseDraw.add(PotionCard(card))
            }
        }
        contents.custom.forEach { (card, count) ->
            repeat(count) { baseDraw.add(PotionCard(card.effect, card)) }
        }
        basePool = baseDraw.filter { it.custom == null }
        dlcPool = dlcDraw.toList()
        baseDraw.shuffle(random)
        dlcDraw.shuffle(random)
    }

    fun draw(source: DeckSource): PotionCard = checkNotNull(drawAvailable(source))

    fun drawAvailable(source: DeckSource): PotionCard? {
        val pile = if (source == DeckSource.BASE) baseDraw else dlcDraw
        val discard = if (source == DeckSource.BASE) baseDiscard else dlcDiscard
        if (pile.isEmpty()) {
            pile.addAll(discard)
            discard.clear()
            pile.shuffle(random)
        }
        if (pile.isNotEmpty()) return pile.removeAt(pile.lastIndex)
        if (recipe != null) return null
        // The entire draw pile can be on the board when every tile has a potion.
        return (if (source == DeckSource.BASE) basePool else dlcPool).random(random)
    }

    fun discard(card: PotionCard, source: DeckSource) {
        if (source == DeckSource.DLC) dlcDiscard.add(card) else baseDiscard.add(card)
    }
}

data class CornerAssignment(val corner: Cell, val source: DeckSource)

fun assignCornerDecks(dlcEnabled: Boolean, random: Random = Random, boardSize: Int = BOARD_SIZE): List<CornerAssignment> {
    val corners = boardCorners(boardSize)
    if (!dlcEnabled) return corners.map { CornerAssignment(it, DeckSource.BASE) }
    val diagonalA = corners.take(2)
    val diagonalB = corners.drop(2)
    val (baseDiag, dlcDiag) = if (random.nextBoolean()) diagonalA to diagonalB else diagonalB to diagonalA
    return baseDiag.map { CornerAssignment(it, DeckSource.BASE) } + dlcDiag.map { CornerAssignment(it, DeckSource.DLC) }
}

// ---------- Акторы ----------

class Player(val name: String, initialPosition: Cell, val isBot: Boolean = false) : BoardActor {
    var rerollUsed = false
    var botStyle = BotStyle.POTION_HUNTER
    override var position: Cell by mutableStateOf(initialPosition)
    override val effects: MutableList<ActiveEffect> = mutableStateListOf()
    override val displayName: String get() = name
    override var stunnedTurns: Int by mutableStateOf(0)
    var alive: Boolean by mutableStateOf(true)
    val activeCards: MutableList<CardId> = mutableStateListOf()
    var skipNextTurn: Boolean by mutableStateOf(false)
}

class BossUnit(val slot: Int, initialPosition: Cell) : BoardActor {
    override var position: Cell by mutableStateOf(initialPosition)
    override val effects: MutableList<ActiveEffect> = mutableStateListOf()
    override val displayName: String get() = if (slot == 1) "Босс" else "Босс $slot"
    override var stunnedTurns: Int by mutableStateOf(0)
}

class Potion(initialPosition: Cell, val source: DeckSource, initialCard: PotionCard) {
    constructor(initialPosition: Cell, source: DeckSource, initialCard: CardId) :
        this(initialPosition, source, PotionCard(initialCard))

    var position: Cell by mutableStateOf(initialPosition)
    var card by mutableStateOf(initialCard)
    val cardId: CardId get() = card.effect
    var revealed: Boolean by mutableStateOf(false)
    var usedTrigger: Int by mutableStateOf(0)
}
