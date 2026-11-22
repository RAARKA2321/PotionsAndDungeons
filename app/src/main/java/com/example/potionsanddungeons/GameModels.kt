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
val CENTER = Cell(2, 2)
val CORNERS = listOf(Cell(0, 0), Cell(4, 4), Cell(0, 4), Cell(4, 0))

fun diagonalCellsFromCorner(corner: Cell): List<Cell> {
    val dr = if (corner.row == 0) 1 else -1
    val dc = if (corner.col == 0) 1 else -1
    val cells = mutableListOf<Cell>()
    var r = corner.row + dr
    var c = corner.col + dc
    while (r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE && Cell(r, c) != CENTER) {
        cells.add(Cell(r, c))
        r += dr
        c += dc
    }
    return cells
}

fun Cell.inBounds() = row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE

fun Cell.neighbors8(): List<Cell> {
    val result = mutableListOf<Cell>()
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val n = Cell(row + dr, col + dc)
        if (n.inBounds()) result.add(n)
    }
    return result
}

fun Cell.neighbors4() = Direction.entries.map { Cell(row + it.dr, col + it.dc) }.filter { it.inBounds() }

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
    val daily: Boolean = false
)

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

class PotionDeck(val dlcEnabled: Boolean, private val random: Random = Random, choicePotions: Boolean = false, duel: Boolean = false) {
    private val baseDraw = mutableListOf<CardId>()
    private val baseDiscard = mutableListOf<CardId>()
    private val dlcDraw = mutableListOf<CardId>()
    private val dlcDiscard = mutableListOf<CardId>()

    init {
        CARD_QUANTITIES.forEach { (card, qty) ->
            if (!duel || card !in duelExcludedCards) {
                if (!card.isDlc) repeat(qty) { baseDraw.add(card) }
                else if (dlcEnabled) repeat(qty) { dlcDraw.add(card) }
            }
        }
        if (choicePotions) repeat(2) {
            baseDraw.add(CardId.CHOICE_SHIELD)
            baseDraw.add(CardId.CHOICE_SPEED)
        }
        baseDraw.shuffle(random)
        dlcDraw.shuffle(random)
    }

    fun draw(source: DeckSource): CardId {
        val pile = if (source == DeckSource.BASE) baseDraw else dlcDraw
        val discard = if (source == DeckSource.BASE) baseDiscard else dlcDiscard
        if (pile.isEmpty()) {
            pile.addAll(discard)
            discard.clear()
            pile.shuffle(random)
        }
        return pile.removeAt(pile.lastIndex)
    }

    fun discard(card: CardId) {
        if (card.isDlc) dlcDiscard.add(card) else baseDiscard.add(card)
    }
}

private val duelExcludedCards = setOf(CardId.BODY_SWAP, CardId.EFFECT_SWAP, CardId.BFF, CardId.PUPPETEER, CardId.THIEF, CardId.WEREWOLF)

data class CornerAssignment(val corner: Cell, val source: DeckSource)

fun assignCornerDecks(dlcEnabled: Boolean, random: Random = Random): List<CornerAssignment> {
    if (!dlcEnabled) return CORNERS.map { CornerAssignment(it, DeckSource.BASE) }
    val diagonalA = listOf(Cell(0, 0), Cell(4, 4))
    val diagonalB = listOf(Cell(0, 4), Cell(4, 0))
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

class Potion(initialPosition: Cell, val source: DeckSource, initialCard: CardId) {
    var position: Cell by mutableStateOf(initialPosition)
    var cardId: CardId by mutableStateOf(initialCard)
    var revealed: Boolean by mutableStateOf(false)
    var usedTrigger: Int by mutableStateOf(0)
}
