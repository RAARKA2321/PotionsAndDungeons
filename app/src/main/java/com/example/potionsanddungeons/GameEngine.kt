package com.example.potionsanddungeons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

sealed class PendingChoice {
    data class ChooseCell(val prompt: String, val allowed: (Cell) -> Boolean, val onChosen: (Cell) -> Unit) : PendingChoice()
    data class ChoosePlayer(val prompt: String, val allowed: (Player) -> Boolean, val onChosen: (Player) -> Unit) : PendingChoice()
    data class ChooseEffect(val from: Player, val onChosen: (ActiveEffect) -> Unit) : PendingChoice()
}

data class CardRevealEvent(
    val playerName: String, val card: CardId, val canCleanse: Boolean = false,
    val bot: Boolean = false, val customCard: CustomCard? = null
)

class GameEngine(private val dlcEnabled: Boolean, private val random: Random = Random, private val animateD4: Boolean = false, private val offerReroll: Boolean = false, private val legacyAttackTurns: Boolean = false, private val legacyBossCrossing: Boolean = false, private val customRules: CustomRules = CustomRules(), private val movementRandom: Random = random, private val legacyPotionRules: Boolean = false) {
    val boardSize = customRules.board.size
    private val boardCenter = boardCenter(boardSize)
    private val boardCorners = boardCorners(boardSize)
    private val boardCells = List(boardSize * boardSize) { Cell(it / boardSize, it % boardSize) }
    var awaitingRollDecision by mutableStateOf(false)
        private set
    private var inlineRerollAvailable by mutableStateOf(false)
    val canReroll: Boolean
        get() = (awaitingRollDecision || inlineRerollAvailable) &&
            !gameOver && !awaitingBossMove && !hasPendingAction
    var tutorial = false
        private set
    var players by mutableStateOf(listOf<Player>())
    var bosses by mutableStateOf(listOf<BossUnit>())
    var potions by mutableStateOf(listOf<Potion>())
    var turnOrder by mutableStateOf(listOf<Player>())
    var currentTurnIndex by mutableStateOf(0)
    var pendingSteps by mutableStateOf(0)
    var lastDiceRoll by mutableStateOf(0)
    val d4Rolls = mutableStateListOf<Int>()
    private var recordingD4 = false
    private var afterD4: (() -> Unit)? = null
    var d4Sequence by mutableStateOf(0)
        private set
    var lastDirection by mutableStateOf<Direction?>(null)
    var gameOver by mutableStateOf(false)
    var statusText by mutableStateOf("Настрой игроков и нажми «Начать игру»")
    val log = mutableStateListOf<String>()

    var pendingChoice by mutableStateOf<PendingChoice?>(null)

    var cardReveal by mutableStateOf<CardRevealEvent?>(null)
    private var revealedPotionAction: ((Boolean) -> Unit)? = null
    var doors by mutableStateOf(emptySet<Cell>())
        private set
    val duelExit: Cell? = boardCorners.first().takeIf { customRules.bossDuel }
    var duelKeys by mutableStateOf(emptySet<Cell>())
        private set
    var tradePartners by mutableStateOf(emptyList<Player>())
        private set
    private var considerTrade = false

    var bossSpeedBonus by mutableStateOf(0)
    var bossAttackRadiusBonus by mutableStateOf(0)
    var bossDiagonalMovement by mutableStateOf(false)
    // Хаос-бросок: вместо случайного направления теперь количество ходов босса
    // за раунд определяется броском д4 (направление — обычное, к игроку).
    var bossChaosRoll by mutableStateOf(false)

    var awaitingBossMove by mutableStateOf(false)
    var bossMoveTrigger by mutableStateOf(0)
    private var bossStepQueue: ArrayDeque<BossUnit>? = null

    val bananaPeels = mutableStateListOf<Cell>()

    // Куколд: игрок управляет боссом вручную кликами по клетке, пока не исчерпаны шаги.
    var pendingCuckoldSteps by mutableStateOf(0)
    var cuckoldController by mutableStateOf<Player?>(null)

    // Кукловод: игрок управляет другим игроком вручную кликами по клетке.
    var pendingPuppetSteps by mutableStateOf(0)
    var puppetTarget by mutableStateOf<Player?>(null)

    private lateinit var deck: PotionDeck
    private var cornerDecks: List<CornerAssignment> = emptyList()
    private var turnStart: Cell? = null
    private var puppetStart: Cell? = null
    private val suspendedPuppets = ArrayDeque<PuppetControl>()
    var attackCardPlayed by mutableStateOf(false)
        private set

    private var turnCompletionPending = false
    private var turnRewound = false
    private var deferredDaun: Player? = null

    private data class PuppetControl(val target: Player, val steps: Int, val start: Cell)

    val hasPendingAction: Boolean
        get() = d4Rolls.isNotEmpty() || cardReveal != null || pendingChoice != null || pendingCuckoldSteps > 0 || pendingPuppetSteps > 0 || tradePartners.isNotEmpty()


    fun addLog(msg: String) { log.add(msg) }

    // Щит: если у цели есть щит — тот поглощает удар и разрушается, а урон/стан не применяется.
    // Если щита нет — выполняется действие по умолчанию (стан или другой эффект удара).
    fun absorbHitOrElse(target: Player, absorbedLog: String, onNoShield: () -> Unit) {
        val shield = target.effects.get(CardId.SHIELD)
        if (shield != null) {
            target.effects.remove(shield)
            addLog(absorbedLog)
        } else {
            onNoShield()
        }
    }

    fun setupGame(names: List<String>, botSlots: Set<Int> = emptySet()) =
        setupGame(names, botSlots, fixedTurnOrder = false)

    private fun setupGame(names: List<String>, botSlots: Set<Int>, fixedTurnOrder: Boolean) {
        awaitingRollDecision = false
        inlineRerollAvailable = false
        recordingD4 = animateD4
        afterD4 = null
        d4Rolls.clear()
        turnOrder = emptyList()
        require(if (customRules.bossDuel) names.size == 1 && botSlots.isEmpty() else names.size in 2..boardCorners.size)
        deck = PotionDeck(dlcEnabled, random, customRules.choicePotions, customRules.bossDuel, customRules.board.customCards)
        duelKeys = if (customRules.bossDuel) boardCorners.drop(1).toSet() else emptySet()
        tradePartners = emptyList()
        considerTrade = false
        doors = if (customRules.breakableDoors) setOf(
            boardCenter.copy(col = boardCenter.col - 1), boardCenter.copy(col = boardCenter.col + 1)
        ) else emptySet()
        cornerDecks = assignCornerDecks(dlcEnabled, random, boardSize)

        players = names.mapIndexed { i, name -> Player(name, boardCorners[i], i in botSlots) }
        bosses = listOf(BossUnit(1, boardCenter))

        potions = if (customRules.board.potionPlacement == PotionPlacement.EVERY_CELL) {
            boardCells.map { cell ->
                val source = potionSourceAt(cell)
                Potion(cell, source, deck.draw(source))
            }
        } else {
            cornerDecks.flatMap { assignment ->
                diagonalCellsFromCorner(assignment.corner, boardSize).map { cell ->
                    Potion(cell, assignment.source, deck.draw(assignment.source))
                }
            }
        }
        bananaPeels.clear()

        bossSpeedBonus = 0
        bossAttackRadiusBonus = 0
        bossDiagonalMovement = false
        bossChaosRoll = false
        awaitingBossMove = false
        bossStepQueue = null
        pendingCuckoldSteps = 0
        cuckoldController = null
        pendingPuppetSteps = 0
        puppetTarget = null
        puppetStart = null
        suspendedPuppets.clear()

        attackCardPlayed = false
        gameOver = false
        turnCompletionPending = false
        turnStart = null
        turnRewound = false
        deferredDaun = null
        log.clear()
        pendingChoice = null
        cardReveal = null
        revealedPotionAction = null
        if (fixedTurnOrder) turnOrder = players else determineTurnOrder()
        recordingD4 = animateD4
        currentTurnIndex = 0
        pendingSteps = 0
        lastDirection = null
        updateStatus()
    }

    fun setupTutorial() {
        setupGame(listOf("Ты", "Напарник"), setOf(1))
        tutorial = true
        turnOrder = players
        bosses.single().position = Cell(2, 1)
        potions = listOf(Potion(Cell(0, 1), DeckSource.DLC, CardId.ATTACK))
        log.clear()
        updateStatus()
    }

    internal fun setupCardSandbox() {
        setupGame(listOf("Испытатель", "Напарник"), emptySet(), fixedTurnOrder = true)
        players[0].position = Cell(2, 1)
        players[1].position = Cell(1, 1)
        log.clear()
        updateStatus()
    }

    internal val canTestCard: Boolean
        get() = !gameOver && !awaitingBossMove && !awaitingRollDecision && !hasPendingAction

    internal fun testCard(card: CardId) {
        if (!canTestCard) return
        inlineRerollAvailable = false
        val player = turnOrder.getOrNull(currentTurnIndex) ?: return
        val wasMoving = pendingSteps > 0
        revealPickedCard(player, card) {
            if (wasMoving && turnRewound) turnCompletionPending = true
        }
    }

    private fun rollD4(): Int = random.nextInt(1, 5).also {
        if (recordingD4) d4Rolls.add(it)
    }
    fun withD4(action: (Int) -> Unit) {
        val result = rollD4()
        afterRolls { action(result) }
    }

    private fun afterRolls(action: () -> Unit) {
        if (d4Rolls.isEmpty()) action() else afterD4 = action
    }

    fun finishD4Animation() {
        if (d4Rolls.isEmpty()) return
        d4Rolls.removeAt(0)
        d4Sequence++
        if (d4Rolls.isNotEmpty()) return
        val action = afterD4
        afterD4 = null
        action?.invoke()
        if (!awaitingBossMove && turnOrder.isNotEmpty()) afterChoiceResolved()
    }

    private fun determineTurnOrder() {
        val order = orderByRoll(players)
        afterRolls {
            turnOrder = order
            addLog("Порядок ходов: " + turnOrder.joinToString(" -> ") { it.name })
        }
    }

    private fun orderByRoll(candidates: List<Player>): List<Player> {
        if (candidates.size <= 1) return candidates
        var groups: Map<Int, List<Player>>
        do {
            groups = candidates.groupBy { rollD4() }
        } while (groups.size == 1)
        return groups.toSortedMap(reverseOrder()).values.flatMap(::orderByRoll)
    }

    fun diceSidesForCurrentPlayer(): Int {
        val current = turnOrder.getOrNull(currentTurnIndex) ?: return 6
        return if (current.effects.has(CardId.DISABILITY)) 4 else 6
    }

    private fun isKnightDestination(player: Player, cell: Cell): Boolean {
        val dr = kotlin.math.abs(cell.row - player.position.row)
        val dc = kotlin.math.abs(cell.col - player.position.col)
        return cell.inBounds(boardSize) && cell !in doors && dr * dc == 2 && bosses.none { it.position == cell }
    }

    fun canMoveAsKnight(): Boolean {
        val player = turnOrder.getOrNull(currentTurnIndex) ?: return false
        return player.effects.has(CardId.HORSE) && (0 until boardSize).any { row ->
            (0 until boardSize).any { col -> isKnightDestination(player, Cell(row, col)) }
        }
    }

    fun mustSkipCurrentTurn(): Boolean = turnOrder.getOrNull(currentTurnIndex)?.let {
        it.stunnedTurns > 0 || it.skipNextTurn
    } == true

    internal fun isCurrentPlayer(player: Player): Boolean = player == turnOrder.getOrNull(currentTurnIndex)

    internal fun directionalMoveTarget(direction: Direction): Cell? {
        if (cardReveal != null || pendingChoice != null || tradePartners.isNotEmpty()) return null
        val position = when {
            pendingCuckoldSteps > 0 -> bosses.firstOrNull()?.position
            pendingPuppetSteps > 0 -> puppetTarget?.position
            pendingSteps > 0 -> turnOrder.getOrNull(currentTurnIndex)?.position
            else -> null
        } ?: return null
        return Cell(position.row + direction.dr, position.col + direction.dc).takeIf(::isSelectableBoardCell)
    }

    internal fun isSelectableBoardCell(cell: Cell): Boolean =
        isBoardTarget(cell) && turnOrder.getOrNull(currentTurnIndex)?.isBot != true &&
            (!tutorial || if (pendingChoice != null) bosses.any { it.position == cell } else attackCardPlayed || cell == Cell(0, 1))

    fun isBoardTarget(cell: Cell): Boolean {
        if (awaitingRollDecision) return false
        if (gameOver || awaitingBossMove || d4Rolls.isNotEmpty() || !cell.inBounds(boardSize)) return false
        when (val choice = pendingChoice) {
            is PendingChoice.ChooseCell -> return choice.allowed(cell)
            is PendingChoice.ChoosePlayer -> return players.any { it.position == cell && choice.allowed(it) }
            is PendingChoice.ChooseEffect -> return false
            null -> Unit
        }
        if (pendingCuckoldSteps > 0) return bosses.firstOrNull()?.position?.neighbors4(boardSize)?.contains(cell) == true
        if (pendingPuppetSteps > 0) return cell !in doors && puppetTarget?.position?.neighbors4(boardSize)?.contains(cell) == true
        val current = turnOrder.getOrNull(currentTurnIndex) ?: return false
        if (pendingSteps > 0) {
            return Direction.entries.any { direction ->
                cell == Cell(current.position.row + direction.dr, current.position.col + direction.dc) &&
                    isWalkingDirectionAllowed(direction, cell)
            }
        }
        return current.effects.has(CardId.HORSE) && !mustSkipCurrentTurn() && isKnightDestination(current, cell)
    }

    private fun isWalkingDirectionAllowed(direction: Direction, cell: Cell): Boolean =
        direction != lastDirection?.opposite() && isWalkableCell(cell)

    private fun isWalkableCell(cell: Cell): Boolean =
        cell.inBounds(boardSize) && cell !in doors && (!dlcEnabled || legacyBossCrossing || bosses.none { it.position == cell })

    fun rollDiceForCurrentPlayer(waitForAcceptance: Boolean = false): Int {
        if (gameOver || pendingSteps > 0 || turnOrder.isEmpty() || awaitingBossMove || hasPendingAction || awaitingRollDecision) return 0
        val current = turnOrder[currentTurnIndex]

        if (current.stunnedTurns > 0) {
            current.stunnedTurns--
            addLog("${current.name} оглушён и пропускает ход")
            nextTurn()
            return 0
        }
        if (current.skipNextTurn) {
            current.skipNextTurn = false
            addLog("${current.name} пропускает ход (эффект «Стан»)")
            nextTurn()
            return 0
        }

        turnStart = current.position

        if (current.effects.has(CardId.HORSE)) {
            if (!canMoveAsKnight()) {
                finishBlockedWalk(current)
                return 0
            }
            pendingSteps = 0
            lastDirection = null
            statusText = "${current.name}: ход конём — коснись клетки буквой «Г»."
            return 0
        }

        val sides = diceSidesForCurrentPlayer()
        lastDiceRoll = if (tutorial) 1 else movementRandom.nextInt(1, sides + 1)
        addLog("${current.name} бросил кубик: $lastDiceRoll")
        val eligible = offerReroll && !current.isBot && !current.rerollUsed &&
            (waitForAcceptance || !attackCardPlayed)
        if (eligible) {
            if (waitForAcceptance || adjustedMovementRoll(current) <= 0 ||
                current.position.neighbors4(boardSize).none(::isWalkableCell)
            ) {
                awaitingRollDecision = true
                return lastDiceRoll
            }
            inlineRerollAvailable = true
        }
        return applyDiceRoll(current)
    }

    fun decideRoll(reroll: Boolean): Int {
        if (reroll && !canReroll || !reroll && !awaitingRollDecision) return 0
        val current = turnOrder[currentTurnIndex]
        awaitingRollDecision = false
        inlineRerollAvailable = false
        if (reroll) {
            current.rerollUsed = true
            lastDiceRoll = movementRandom.nextInt(1, diceSidesForCurrentPlayer() + 1)
            addLog("${current.name} перебросил кубик: $lastDiceRoll")
        }
        return applyDiceRoll(current)
    }

    internal fun movementEffectCount(player: Player, card: CardId): Int {
        val count = player.effects.count { it.id == card }
        return if (legacyPotionRules) count.coerceAtMost(1) else count
    }

    private fun adjustedMovementRoll(current: Player): Int {
        val slow = movementEffectCount(current, CardId.SLOWDOWN)
        if (legacyPotionRules && lastDiceRoll == 1 && slow > 0) return 0
        return lastDiceRoll + movementEffectCount(current, CardId.SPEED) - slow
    }

    private fun applyDiceRoll(current: Player): Int {
        val roll = adjustedMovementRoll(current)
        if (roll <= 0) {
            addLog("${current.name}: «Замедление» — шагов не осталось, ход сгорает")
            nextTurn()
            return 0
        }
        if (roll != lastDiceRoll) {
            addLog("${current.name}: после эффектов движения — $roll шагов")
        }

        pendingSteps = roll
        lastDirection = null
        if (!canContinueWalking(current)) {
            finishBlockedWalk(current)
            return roll
        }
        updateStatus()
        return roll
    }

    fun handleCellClick(target: Cell) {
        if (awaitingRollDecision) return
        if (cardReveal != null || d4Rolls.isNotEmpty()) return
        if (gameOver || awaitingBossMove || !target.inBounds(boardSize)) return
        if (pendingChoice != null) {
            resolvePendingChoiceClick(target)
            return
        }

        // Куколд: пока активно ручное управление боссом, клики идут сюда,
        // а не в обычную логику хода игрока.
        if (pendingCuckoldSteps > 0) {
            playOneBossStepTo(target)
            return
        }

        // Кукловод: пока активно ручное управление другим игроком, клики идут сюда.
        if (pendingPuppetSteps > 0) {
            playOnePlayerStepTo(target)
            return
        }

        if (gameOver || turnOrder.isEmpty() || awaitingBossMove) return
        val current = turnOrder[currentTurnIndex]

        if (current.effects.has(CardId.HORSE) && pendingSteps == 0) {
            if (!mustSkipCurrentTurn()) handleKnightMove(current, target)
            return
        }

        if (pendingSteps <= 0) return

        val dr = target.row - current.position.row
        val dc = target.col - current.position.col
        val direction = when {
            dr == -1 && dc == 0 -> Direction.UP
            dr == 1 && dc == 0 -> Direction.DOWN
            dr == 0 && dc == -1 -> Direction.LEFT
            dr == 0 && dc == 1 -> Direction.RIGHT
            else -> null
        } ?: return

        if (lastDirection != null && direction == lastDirection!!.opposite()) {
            addLog("Нельзя ходить назад в этом же ходу")
            return
        }

        if (!isWalkableCell(target)) return

        inlineRerollAvailable = false
        current.position = target
        lastDirection = direction
        pendingSteps--
        val slipped = checkBananaPeel(current)
        if (checkGameEnd()) return
        if (!current.alive || slipped) {
            finishTurnWhenReady()
        } else if (pendingSteps == 0) {
            resolveFinalLanding(current)
        } else if (!canContinueWalking(current)) {
            finishBlockedWalk(current)
        } else {
            updateStatus()
        }
    }

    private fun handleKnightMove(current: Player, target: Cell) {
        if (!isKnightDestination(current, target)) {
            addLog("Для хода конём выбери клетку буквой «Г» без босса")
            return
        }
        turnStart = current.position
        current.position = target
        val slipped = checkBananaPeel(current)
        if (slipped || !current.alive) finishTurnWhenReady() else resolveFinalLanding(current)
    }


    private fun canContinueWalking(player: Player): Boolean = Direction.entries.any { direction ->
        val cell = Cell(player.position.row + direction.dr, player.position.col + direction.dc)
        isWalkingDirectionAllowed(direction, cell)
    }

    private fun finishBlockedWalk(player: Player) {
        addLog("${player.name}: нет доступных клеток — оставшиеся шаги сгорают")
        finishTurnWhenReady()
    }

    private fun resolveFinalLanding(player: Player) {
        if (checkGameEnd()) return
        if (player.position in duelKeys) {
            duelKeys = duelKeys - player.position
            addLog("${player.name} подобрал ключ (${3 - duelKeys.size}/3)")
        }
        if (duelExit == player.position && duelKeys.isEmpty()) {
            statusText = "Все ключи собраны. Ты выбрался!"
            gameOver = true
            clearPendingActions()
            return
        }
        checkWerewolfKill(player)
        if (checkGameEnd()) return
        deferredDaun = player.takeIf { it.effects.has(CardId.DAUN) }
        considerTrade = customRules.cardTrading && !customRules.bossDuel && !player.isBot
        checkPotionAt(player)
        finishTurnWhenReady()
    }

    private fun checkWerewolfKill(player: Player) {
        if (!player.effects.has(CardId.WEREWOLF)) return
        val victim = players.find { it != player && it.alive && it.position == player.position } ?: return
        absorbHitOrElse(victim, "${victim.name}: щит поглотил атаку оборотня ${player.name} и разрушился") {
            victim.alive = false
            addLog("${player.name}-оборотень растерзал ${victim.name}!")
        }
    }


    private fun applyDaunEffect(player: Player) {
        if (!player.effects.has(CardId.DAUN)) return
        withD4 { roll ->
        val wall = Wall.byNumber(roll)
        val next = Cell(player.position.row + wall.vector.row, player.position.col + wall.vector.col)
        if (next.inBounds(boardSize) && next !in doors) {
            player.position = next
            addLog("${player.name}: эффект «Даун» — дополнительный шаг к стене №${wall.number}")
            val slipped = checkBananaPeel(player)
            if (!slipped && player.alive && !checkGameEnd()) checkPotionAt(player)
        } else {
            addLog("${player.name}: эффект «Даун» — шаг к стене №${wall.number} заблокирован")
        }
    
        }
    }

    // Кожурка банана: моментально прерывает оставшийся ход игрока и оглушает его на 1 ход.
    private fun checkBananaPeel(player: Player): Boolean {
        if (player.position in bananaPeels) {
            player.stunnedTurns = maxOf(player.stunnedTurns, 1)
            bananaPeels.remove(player.position)
            addLog("${player.name} наступил на кожурку от банана — ход прерван, оглушён на 1 ход")
            return true
        }
        return false
    }

    fun checkPotionAt(player: Player) {
        val potion = potions.find { it.position == player.position } ?: return
        resolvePotionCard(player, potion)
    }

    fun resolvePotionCard(player: Player, potion: Potion) {
        if (!player.alive || gameOver) return
        val picked = potion.card
        deck.discard(picked, potion.source)
        potion.card = deck.draw(potion.source)
        potion.usedTrigger++
        potion.revealed = false
        if (customRules.board.potionPlacement == PotionPlacement.RANDOM_REPLACEMENT) {
            val occupied = potions.map { it.position }.toSet() + doors + duelKeys +
                players.filter { it.alive }.map { it.position } + bosses.map { it.position }
            boardCells.filterNot { it in occupied || it == duelExit }.randomOrNull(random)?.let { potion.position = it }
        }
        revealPickedCard(player, picked.effect, picked.custom)
    }

    private fun revealPickedCard(player: Player, card: CardId, customCard: CustomCard? = null, afterPickup: () -> Unit = {}) {
        val reward = card.choiceReward
        val negative = if (reward != null) player.effects.firstOrNull { it.id.isNegative() } else null
        val canCleanse = reward != null && (negative != null || player.stunnedTurns > 0 || player.skipNextTurn)
        cardReveal = CardRevealEvent(player.name, card, canCleanse, player.isBot, customCard)
        val name = customCard?.name ?: card.displayName()
        addLog("${player.name}: подобрал «$name»")
        revealedPotionAction = { cleanse ->
            if (reward != null && cleanse && canCleanse) {
                val removed = when {
                    negative != null -> {
                        player.effects.remove(negative)
                        negative.id.displayName()
                    }
                    player.stunnedTurns > 0 -> {
                        player.stunnedTurns = 0
                        "Оглушение"
                    }
                    else -> {
                        player.skipNextTurn = false
                        CardId.STUN.displayName()
                    }
                }
                addLog("${player.name}: снят эффект «$removed»")
            } else if (card.isActive) {
                player.activeCards.add(card)
                addLog("${player.name}: карта ушла в инвентарь")
            } else if (blockNegativePotion(player, card)) {
                addLog("${player.name}: иммунитет заблокировал «$name»")
            } else {
                EffectRegistry.get(reward ?: card)?.onPickup(this, player)
                checkPendingEffects(player)
            }
            afterPickup()
        }
    }

    private fun blockNegativePotion(player: Player, card: CardId): Boolean {
        if (!card.isNegative()) return false
        val immunity = player.effects.get(CardId.IMMUNITY) ?: return false
        if (!legacyPotionRules) player.effects.remove(immunity)
        return true
    }

    fun continueAfterPotion(cleanse: Boolean = false) {
        val action = revealedPotionAction ?: return
        revealedPotionAction = null
        cardReveal = null
        action(cleanse)
        afterChoiceResolved()
    }

    private fun checkPendingEffects(player: Player) {
        player.effects.filter { it.category == EffectCategory.PENDING_CONDITIONAL }.forEach { pending ->
            if (EffectRegistry.get(pending.id)?.checkPendingTrigger(this, player) != true) return@forEach
            player.effects.remove(pending)
            when (pending.id) {
                CardId.BROKEN_SHIELD -> {
                    player.effects.get(CardId.SHIELD)?.let { player.effects.remove(it) }
                    addLog("${player.name}: щит сломан («Сломанный щит» сработал)")
                }
                CardId.CURSE -> {
                    val positive = player.effects.find { it.id.isPositive() }
                    if (positive != null) turnIntoNegative(player, positive)
                }
                else -> {}
            }
        }
    }

    fun turnIntoNegative(player: Player, effect: ActiveEffect) {
        player.effects.remove(effect)
        addLog("${player.name}: «Проклятие» сняло эффект «${effect.id.displayName()}»")
    }

    fun swapAllEffects(holder: Player, target: Player) {
        val holderEffects = holder.effects.toList()
        val targetEffects = target.effects.toList()

        holderEffects.forEach { holder.effects.remove(it) }
        targetEffects.forEach { target.effects.remove(it) }

        targetEffects.forEach { transferEffect(it, holder) }
        holderEffects.forEach { transferEffect(it, target) }

        addLog("${holder.name} и ${target.name} полностью обменялись эффектами")
        checkPendingEffects(holder)
        checkPendingEffects(target)
    }

    fun canPlayActiveCard(player: Player, card: CardId): Boolean =
        card.isActive && player.alive && isCurrentPlayer(player) && card in player.activeCards &&
            !awaitingRollDecision && !attackCardPlayed && !gameOver && pendingSteps == 0 &&
            !awaitingBossMove && !hasPendingAction && !mustSkipCurrentTurn()

    fun playActiveCard(player: Player, card: CardId): Boolean {
        if (!canPlayActiveCard(player, card)) return false
        if (card == CardId.THIEF && players.none { ActiveCardResolver.canStealFrom(player, it) }) {
            addLog("Вор: у других игроков нет эффектов для кражи")
            return false
        }

        turnStart = player.position
        inlineRerollAvailable = false
        if (card != CardId.THIEF) player.activeCards.remove(card)
        attackCardPlayed = !legacyAttackTurns && ActiveCardResolver.allowsMovementAfterUse(card)
        ActiveCardResolver.resolve(this, player, card)
        if (attackCardPlayed) updateStatus() else finishTurnWhenReady()
        return true
    }

    private fun finishTurnWhenReady() {
        if (checkGameEnd()) return
        pendingSteps = 0
        if (!hasPendingAction) {
            val player = deferredDaun
            deferredDaun = null
            if (player != null && !turnRewound && player.alive && player.effects.has(CardId.DAUN)) {
                applyDaunEffect(player)
                if (checkGameEnd()) return
            }
        }
        turnCompletionPending = hasPendingAction
        if (turnCompletionPending) return
        if (considerTrade) {
            considerTrade = false
            val current = turnOrder[currentTurnIndex]
            if (!turnRewound && current.activeCards.isNotEmpty()) {
                tradePartners = players.filter { target ->
                    target !== current && target.alive && !target.isBot &&
                        target.activeCards.isNotEmpty() && manhattan(current.position, target.position) == 1
                }
            }
            if (tradePartners.isNotEmpty()) return
        }
        nextTurn()
    }

    internal fun finishTrade(partner: Player?, offered: CardId?, requested: CardId?) {
        if (tradePartners.isEmpty()) return
        val current = turnOrder[currentTurnIndex]
        if (partner != null) {
            if (partner !in tradePartners || offered == null || requested == null ||
                offered !in current.activeCards || requested !in partner.activeCards
            ) return
            current.activeCards.remove(offered)
            partner.activeCards.remove(requested)
            current.activeCards.add(requested)
            partner.activeCards.add(offered)
            addLog("${current.name} и ${partner.name} обменялись картами «${offered.displayName()}» и «${requested.displayName()}»")
        }
        tradePartners = emptyList()
        nextTurn()
    }

    fun requestChooseCell(prompt: String, allowed: (Cell) -> Boolean, onChosen: (Cell) -> Unit) {
        pendingChoice = PendingChoice.ChooseCell(prompt, allowed, onChosen)
        statusText = prompt
    }

    fun requestChoosePlayer(prompt: String, allowed: (Player) -> Boolean, onChosen: (Player) -> Unit) {
        pendingChoice = PendingChoice.ChoosePlayer(prompt, allowed, onChosen)
        statusText = prompt
    }

    fun requestChooseEffect(from: Player, onChosen: (ActiveEffect) -> Unit) {
        pendingChoice = PendingChoice.ChooseEffect(from, onChosen)
        statusText = "Выбери, какой эффект украсть у ${from.name}"
    }

    private fun resolvePendingChoiceClick(target: Cell) {
        val choice = pendingChoice ?: return
        when (choice) {
            is PendingChoice.ChooseCell -> {
                if (choice.allowed(target)) {
                    pendingChoice = null
                    choice.onChosen(target)
                    afterChoiceResolved()
                } else {
                    addLog("Эта клетка недопустима для выбора")
                }
            }
            is PendingChoice.ChoosePlayer -> {
                val candidates = players.filter { it.position == target && choice.allowed(it) }
                when (candidates.size) {
                    0 -> addLog("На этой клетке нет подходящего игрока")
                    1 -> resolvePlayerChoice(candidates.single())
                    else -> addLog("На клетке несколько целей. Выбери игрока в списке над полем")
                }
            }
            is PendingChoice.ChooseEffect -> {}
        }
    }

    fun resolvePlayerChoice(player: Player): Boolean {
        val choice = pendingChoice as? PendingChoice.ChoosePlayer ?: return false
        if (player !in players || !choice.allowed(player)) return false
        pendingChoice = null
        choice.onChosen(player)
        afterChoiceResolved()
        return true
    }

    fun resolveEffectChoice(effect: ActiveEffect) {
        val choice = pendingChoice as? PendingChoice.ChooseEffect ?: return
        if (effect !in choice.from.effects) return
        pendingChoice = null
        choice.onChosen(effect)
        afterChoiceResolved()
    }

    private fun afterChoiceResolved() {
        if (checkGameEnd()) return
        if (pendingChoice != null) return
        while (true) {
            val target = puppetTarget ?: break
            if (target.alive && pendingPuppetSteps > 0) {
                break
            }
            pendingPuppetSteps = 0
            restorePuppetControl()
        }
        if (turnCompletionPending && !hasPendingAction) finishTurnWhenReady() else updateStatus()
    }


    fun stealEffect(thief: Player, victim: Player, effect: ActiveEffect) {
        victim.effects.remove(effect)
        transferEffect(effect, thief)
        addLog("${thief.name} украл эффект «${effect.id.displayName()}» у ${victim.name}")
        checkPendingEffects(thief)
    }

    private fun transferEffect(effect: ActiveEffect, receiver: Player) {
        receiver.effects.add(
            ActiveEffect(effect.id, effect.category, effect.turnsLeft, effect.waitingFor, justAcquired = isCurrentPlayer(receiver))
        )
    }

    fun spawnSecondBoss() {
        if (bosses.size >= 2) return
        val openCells = boardCorners + boardCells
        val spawn = openCells.first { cell ->
            players.none { it.alive && it.position == cell } && bosses.none { it.position == cell }
        }
        bosses = bosses + BossUnit(2, spawn)
        addLog("Появился второй босс")
    }

    fun rewindCurrentTurn(holder: Player) {
        if (holder == puppetTarget) {
            puppetStart?.let { holder.position = it }
            pendingPuppetSteps = 0
        } else if (holder == turnOrder.getOrNull(currentTurnIndex)) {
            turnStart?.let { holder.position = it }
            pendingSteps = 0
            lastDirection = null
            turnRewound = true
        }
        addLog("${holder.name}: откат времени — ход завершён без перемещения")
    }

    fun teleportPlayer(player: Player, cell: Cell) {
        val moved = player.position != cell
        player.position = cell
        addLog("${player.name} телепортировался на (${cell.row},${cell.col})")
        if (!moved) return
        val slipped = checkBananaPeel(player)
        if (slipped && player == puppetTarget) pendingPuppetSteps = 0
        if (!slipped && player.alive && !checkGameEnd()) checkPotionAt(player)
    }

    fun repelBossFrom(holder: Player) {
        val boss = bosses.minByOrNull { manhattan(it.position, holder.position) } ?: return
        val dr = (boss.position.row - holder.position.row).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
        val dc = (boss.position.col - holder.position.col).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
        val newCell = Cell(boss.position.row + dr, boss.position.col + dc)
        if (newCell.inBounds(boardSize) && newCell !in doors && newCell != boss.position && bosses.none { it.position == newCell }) {
            moveBossTo(boss, newCell)
            addLog("${holder.name} отпугнул босса на (${newCell.row},${newCell.col})")
        }
    }

    fun pullPlayerTowardsBoss(holder: Player) {
        val boss = bosses.minByOrNull { manhattan(it.position, holder.position) } ?: return
        val dr = (boss.position.row - holder.position.row).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
        val dc = (boss.position.col - holder.position.col).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
        val newCell = Cell(holder.position.row + dr, holder.position.col + dc)
        if (newCell.inBounds(boardSize) && newCell !in doors && newCell != holder.position) {
            holder.position = newCell
            addLog("${holder.name} притянут к боссу гипнозом")
        }
    }

    fun pullAllPlayersToRandomWall() {
        withD4 { roll ->
        val wall = Wall.byNumber(roll)
        players.filter { it.alive }.forEach { p ->
            var pos = p.position
            while (true) {
                val next = Cell(pos.row + wall.vector.row, pos.col + wall.vector.col)
                if (!next.inBounds(boardSize) || next in doors) break
                pos = next
            }
            p.position = pos
        }
        addLog("Притяжение: все игроки притянуты к стене №${wall.number}")
    
        }
    }

    // Рёв альфы: все игроки притягиваются к боссу на одну клетку строго по одной оси —
    // двигаемся либо по строке, либо по столбцу, никогда по обеим сразу (без диагонали).
    fun pullAllPlayersTowardsBoss() {
        val boss = bosses.firstOrNull() ?: return
        players.filter { it.alive }.forEach { p ->
            val dRow = boss.position.row - p.position.row
            val dCol = boss.position.col - p.position.col
            val newCell = when {
                dRow == 0 && dCol == 0 -> p.position
                Math.abs(dRow) >= Math.abs(dCol) && dRow != 0 -> Cell(p.position.row + (if (dRow > 0) 1 else -1), p.position.col)
                dCol != 0 -> Cell(p.position.row, p.position.col + (if (dCol > 0) 1 else -1))
                else -> p.position
            }
            if (newCell.inBounds(boardSize) && newCell !in doors && newCell != p.position) {
                p.position = newCell
            }
        }
        addLog("Рёв альфы: все игроки притянуты к боссу на 1 клетку (без диагонали)")
    }

    fun spawnPotionsAround(center: Cell) {
        val occupied = potions.map { it.position }.toSet()
        val newOnes = mutableListOf<Potion>()
        center.neighbors8(boardSize).forEach { cell ->
            if (cell !in occupied && cell !in doors) {
                val source = potionSourceAt(cell)
                newOnes.add(Potion(cell, source, deck.draw(source)))
            }
        }
        potions = potions + newOnes
        addLog("Ящик зелий: появилось ${newOnes.size} новых зелий вокруг игрока")
    }

    private fun potionSourceAt(cell: Cell): DeckSource =
        cornerDecks.minBy { manhattan(it.corner, cell) }.source

    // Куколд: запускает ручное управление боссом на N ходов (N = бросок д4).
    // Сами ходы делаются по клику игрока на соседнюю клетку — см. playOneBossStepTo.
    fun startCuckoldControl(controller: Player) {
        withD4 { roll ->
        pendingCuckoldSteps = roll
        cuckoldController = controller
        addLog("Куколд: ${controller.name} берёт управление боссом на $roll ходов")
        statusText = "${controller.name} управляет боссом — осталось ходов: $roll. Коснись соседней клетки."
    
        }
    }

    fun playOneBossStepTo(cell: Cell) {
        val boss = bosses.firstOrNull() ?: return
        if (pendingCuckoldSteps <= 0) return
        val dr = cell.row - boss.position.row
        val dc = cell.col - boss.position.col
        val isAdjacentStraight = (Math.abs(dr) == 1 && dc == 0) || (Math.abs(dc) == 1 && dr == 0)
        if (!isAdjacentStraight || !cell.inBounds(boardSize)) {
            addLog("Куколд: можно шагнуть только на соседнюю клетку по прямой")
            return
        }
        if (!breakDoor(boss, cell)) {
            moveBossTo(boss, cell)
            addLog("Куколд: босс сходил на (${cell.row},${cell.col})")
        }
        pendingCuckoldSteps--
        if (checkGameEnd()) return
        if (boss.stunnedTurns > 0) pendingCuckoldSteps = 0
        if (pendingCuckoldSteps == 0) {
            cuckoldController = null
            afterChoiceResolved()
        } else {
            statusText = "${cuckoldController?.name}: управление боссом — осталось ходов: $pendingCuckoldSteps"
        }
    }

    // Кукловод: запускает ручное управление другим игроком на N ходов (N = бросок д4).
    fun startPuppeteerControl(controller: Player, target: Player) {
        if (pendingPuppetSteps > 0) {
            suspendedPuppets.addLast(PuppetControl(checkNotNull(puppetTarget), pendingPuppetSteps, checkNotNull(puppetStart)))
        }
        withD4 { roll ->
        pendingPuppetSteps = roll
        puppetTarget = target
        puppetStart = target.position
        addLog("Кукловод: ${controller.name} управляет ${target.name} на $roll ходов")
        statusText = "Управление ${target.name} — осталось ходов: $roll. Коснись соседней клетки."
    
        }
    }

    private fun restorePuppetControl() {
        puppetTarget = null
        puppetStart = null
        while (suspendedPuppets.isNotEmpty()) {
            val control = suspendedPuppets.removeLast()
            if (!control.target.alive) continue
            puppetTarget = control.target
            puppetStart = control.start
            pendingPuppetSteps = control.steps
            return
        }
    }

    fun playOnePlayerStepTo(cell: Cell) {
        val target = puppetTarget ?: return
        if (pendingPuppetSteps <= 0) return
        val dr = cell.row - target.position.row
        val dc = cell.col - target.position.col
        val isAdjacentStraight = (Math.abs(dr) == 1 && dc == 0) || (Math.abs(dc) == 1 && dr == 0)
        if (!isAdjacentStraight || !cell.inBounds(boardSize) || cell in doors) {
            addLog("Кукловод: можно шагнуть только на соседнюю свободную клетку по прямой")
            return
        }
        target.position = cell
        pendingPuppetSteps--
        addLog("Кукловод: ${target.name} сходил на (${cell.row},${cell.col}), осталось ходов: $pendingPuppetSteps")
        val slipped = checkBananaPeel(target)
        if (checkGameEnd()) return
        if (slipped || !target.alive) pendingPuppetSteps = 0
        else checkPotionAt(target)
        afterChoiceResolved()
    }

    fun traceUntilObstacle(from: Cell, direction: Direction): Pair<BoardActor?, Cell> {
        var pos = from
        while (true) {
            val next = Cell(pos.row + direction.dr, pos.col + direction.dc)
            if (!next.inBounds(boardSize) || next in doors) return null to pos
            val hitPlayer = players.find { it.alive && it.position == next }
            if (hitPlayer != null) return hitPlayer to next
            val hitBoss = bosses.find { it.position == next }
            if (hitBoss != null) return hitBoss to next
            pos = next
        }
    }

    fun taranMove(player: Player, direction: Direction) {
        var pos = player.position
        var hit: BoardActor? = null
        while (true) {
            val next = Cell(pos.row + direction.dr, pos.col + direction.dc)
            if (!next.inBounds(boardSize) || next in doors) break
            val hitPlayer = players.find { it.alive && it.position == next && it != player }
            val hitBoss = bosses.find { it.position == next }
            if (hitPlayer != null) { hit = hitPlayer; break }
            if (hitBoss != null) { hit = hitBoss; break }
            pos = next
        }
        when (hit) {
            is Player -> {
                player.position = pos
                absorbHitOrElse(hit, "${hit.name}: щит поглотил таран ${player.name} и разрушился") {
                    val pushedCell = Cell(hit.position.row + direction.dr, hit.position.col + direction.dc)
                    if (pushedCell.inBounds(boardSize) && pushedCell !in doors) {
                        hit.position = pushedCell
                        checkBananaPeel(hit)
                    }
                    hit.stunnedTurns = maxOf(hit.stunnedTurns, 1)
                    addLog("${player.name} протаранил ${hit.displayName} — тот оттолкнут и оглушён")
                }
            }

            is BossUnit -> {
                val pushedCell = Cell(hit.position.row + direction.dr, hit.position.col + direction.dc)
                player.position = pos
                if (pushedCell.inBounds(boardSize) && pushedCell !in doors) moveBossTo(hit, pushedCell)
                hit.stunnedTurns = maxOf(hit.stunnedTurns, 1)
                addLog("${player.name} протаранил босса — тот оттолкнут и оглушён")
            }
            null -> {
                player.position = pos
                addLog("${player.name} протаранил до стены")
            }
        }
        val slipped = checkBananaPeel(player)
        if (!slipped && player.alive && !checkGameEnd()) checkPotionAt(player)
    }

    private fun manhattan(a: Cell, b: Cell) = Math.abs(a.row - b.row) + Math.abs(a.col - b.col)

    fun bossAttackZoneCells(): Set<Cell> {
        val zone = mutableSetOf<Cell>()
        bosses.filter { it.stunnedTurns == 0 }.forEach { boss ->
            boardCells.forEach { cell ->
                if (manhattan(boss.position, cell) <= bossAttackRadiusBonus) zone.add(cell)
            }
        }
        return zone
    }

    fun bossTargetCandidates(boss: BossUnit): List<Player> {
        val visible = players.filter { it.alive && !it.effects.has(CardId.INVISIBILITY) }
        val stenched = visible.filter { it.effects.has(CardId.STENCH) }
        val pool = stenched.ifEmpty { visible }
        val minDistance = pool.minOfOrNull { manhattan(it.position, boss.position) }
        return pool.filter { manhattan(it.position, boss.position) == minDistance }
    }

    private fun nearestPlayerTo(boss: BossUnit): Player? {
        var closest = bossTargetCandidates(boss)
        while (closest.size > 1) {
            val rolls = closest.map { it to random.nextInt(1, 5) }
            val maxRoll = rolls.maxOf { it.second }
            closest = rolls.filter { it.second == maxRoll }.map { it.first }
        }
        return closest.singleOrNull()
    }

    private fun stepTowards(boss: BossUnit, target: Player): Cell {
        val br = boss.position.row; val bc = boss.position.col
        val tr = target.position.row; val tc = target.position.col
        val stepR = (tr - br).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
        val stepC = (tc - bc).let { if (it > 0) 1 else if (it < 0) -1 else 0 }

        if (bossDiagonalMovement && stepR != 0 && stepC != 0) {
            val cell = Cell(br + stepR, bc + stepC)
            return if (cell.inBounds(boardSize)) cell else boss.position
        }

        return if (Math.abs(tr - br) == Math.abs(tc - bc) && (tr - br) != 0) {
            if (random.nextBoolean()) Cell(br + stepR, bc) else Cell(br, bc + stepC)
        } else if (Math.abs(tr - br) >= Math.abs(tc - bc)) {
            Cell(br + stepR, bc)
        } else {
            Cell(br, bc + stepC)
        }
    }

    private fun breakDoor(boss: BossUnit, cell: Cell): Boolean {
        if (cell !in doors) return false
        doors = doors - cell
        addLog("${boss.displayName} сломал дверь и остался на месте")
        return true
    }

    private fun moveBossTo(boss: BossUnit, cell: Cell) {
        val moved = boss.position != cell
        boss.position = cell
        if (moved && bananaPeels.remove(cell)) {
            boss.stunnedTurns = maxOf(boss.stunnedTurns, 1)
            addLog("${boss.displayName} наступил на кожурку от банана — оглушён на 1 ход")
        }

    }

    private fun checkBossAttack(boss: BossUnit) {
        for (player in players) {
            if (!player.alive || manhattan(boss.position, player.position) > bossAttackRadiusBonus) continue
            absorbHitOrElse(player, "${player.name}: щит поглотил атаку босса и разрушился") {
                player.alive = false
                addLog("${player.name} проиграл — босс настиг его (радиус атаки: $bossAttackRadiusBonus)")
            }
        }
    }

    fun increaseBossAttackRadius() {
        bossAttackRadiusBonus++
    }

    private fun checkGameEnd(): Boolean {
        if (gameOver) return true
        val alive = players.filter { it.alive }
        if (alive.size == 1 && !customRules.bossDuel) {
            statusText = "Победитель: ${alive[0].name}!"
            gameOver = true
            clearPendingActions()
            return true
        }
        if (alive.isEmpty()) {
            statusText = if (customRules.bossDuel) "Босс настиг тебя" else "Ничья — все игроки погибли одновременно"
            gameOver = true
            clearPendingActions()
            return true
        }
        return false
    }

    private fun clearPendingActions() {
        pendingSteps = 0
        pendingChoice = null
        pendingCuckoldSteps = 0
        cuckoldController = null
        pendingPuppetSteps = 0
        puppetTarget = null
        puppetStart = null
        suspendedPuppets.clear()
        tradePartners = emptyList()
        considerTrade = false
        awaitingBossMove = false
        turnCompletionPending = false
        deferredDaun = null
    }

    private fun decrementTemporaryEffects(player: Player) {
        val expired = mutableListOf<ActiveEffect>()
        player.effects.filter { it.category == EffectCategory.TEMPORARY }.forEach { effect ->
            if (effect.justAcquired) {
                effect.justAcquired = false
            } else {
                effect.turnsLeft--
                if (effect.turnsLeft <= 0) expired.add(effect)
            }
        }
        expired.forEach {
            player.effects.remove(it)
            addLog("${player.name}: эффект «${it.id.displayName()}» закончился")
        }
    }

    private fun nextTurn() {
        inlineRerollAvailable = false
        attackCardPlayed = false
        turnStart = null
        turnRewound = false
        turnOrder.getOrNull(currentTurnIndex)?.let { decrementTemporaryEffects(it) }
        currentTurnIndex++
        if (currentTurnIndex >= turnOrder.size) {
            currentTurnIndex = 0
            awaitingBossMove = true
            bossMoveTrigger++
            statusText = "Ход босса..."
            return
        }
        if (!turnOrder[currentTurnIndex].alive) {
            nextTurn()
            return
        }
        updateStatus()
    }

    fun triggerBossMove() {
        while (advanceBossStep()) { }
    }

    fun playBotAction(rollDice: () -> Unit = { rollDiceForCurrentPlayer() }) {
        val player = turnOrder.getOrNull(currentTurnIndex) ?: return
        if (!player.isBot || gameOver || awaitingBossMove || cardReveal != null || d4Rolls.isNotEmpty()) return
        when (val choice = pendingChoice) {
            is PendingChoice.ChoosePlayer -> {
                players.firstOrNull(choice.allowed)?.let(::resolvePlayerChoice)
                return
            }
            is PendingChoice.ChooseEffect -> {
                choice.from.effects.firstOrNull()?.let(::resolveEffectChoice)
                return
            }
            else -> Unit
        }
        val targets = boardCells.filter(::isBoardTarget)
        if (targets.isNotEmpty()) {
            val destination = if (hasPendingAction) {
                targets.maxBy(::distanceFromBoss)
            } else if (player.botStyle == BotStyle.AGGRESSIVE) {
                targets.minBy(::distanceFromBoss)
            } else {
                val potionLandings = mutableMapOf<Triple<Cell, Direction?, Int>, Int>()
                targets.maxWith(compareBy<Cell> { cell ->
                    if (player.botStyle == BotStyle.CAUTIOUS) return@compareBy -1
                    if (player.botStyle == BotStyle.TACTICAL && lastStepRisk(cell)) return@compareBy -1
                    if (pendingSteps > 0) {
                        val direction = Direction.entries.first {
                            it.dr == cell.row - player.position.row && it.dc == cell.col - player.position.col
                        }
                        bestPotionLanding(cell, direction, pendingSteps - 1, potionLandings)
                    } else {
                        bestPotionLanding(cell, null, 0, potionLandings)
                    }
                }.thenBy(::distanceFromBoss))
            }
            handleCellClick(destination)
        } else if (!hasPendingAction) {
            if (player.activeCards.none { card ->
                    (!player.botStyle.considersCardTargets || isUsefulTacticalCard(player, card)) &&
                        playActiveCard(player, card)
                }) rollDice()
        }
    }

    private fun distanceFromBoss(cell: Cell): Int = bosses.minOf { manhattan(it.position, cell) }

    private fun isUsefulTacticalCard(player: Player, card: CardId): Boolean = when (card) {
        CardId.ATTACK -> occupiedAttackTargets(player).isNotEmpty()
        CardId.PIXIE, CardId.TARAN -> Direction.entries.any { traceUntilObstacle(player.position, it).first != null }
        else -> true
    }

    internal fun occupiedAttackTargets(player: Player): List<Cell> = player.position.neighbors4(boardSize).filter { cell ->
        bosses.any { it.position == cell } || players.any { it !== player && it.alive && it.position == cell }
    }

    fun lastStepRisk(cell: Cell): Boolean = bosses.any { boss ->
        boss.stunnedTurns == 0 && manhattan(boss.position, cell) <=
            bossAttackRadiusBonus + (if (bossChaosRoll) 4 else 1) * (if (bossDiagonalMovement) 2 else 1) +
            bossSpeedBonus * (if (bossDiagonalMovement) 2 else 1)
    }

    private fun bestPotionLanding(
        cell: Cell, direction: Direction?, stepsLeft: Int,
        results: MutableMap<Triple<Cell, Direction?, Int>, Int>
    ): Int {
        if (cell in bananaPeels) return -1
        if (stepsLeft == 0) {
            return if (potions.any { it.position == cell }) distanceFromBoss(cell) else -1
        }
        return results.getOrPut(Triple(cell, direction, stepsLeft)) {
            Direction.entries.maxOf { next ->
                val destination = Cell(cell.row + next.dr, cell.col + next.dc)
                if (next == direction?.opposite() || !isWalkableCell(destination)) -1
                else bestPotionLanding(destination, next, stepsLeft - 1, results)
            }
        }
    }

    fun advanceBossStep(): Boolean {
        if (!awaitingBossMove || hasPendingAction || cardReveal != null || gameOver) return false
        if (bossStepQueue == null) {
            val steps = (if (bossChaosRoll) rollD4() else 1) + bossSpeedBonus
            afterRolls { prepareBossSteps(steps) }
            if (d4Rolls.isNotEmpty()) return false
        }
        val queue = checkNotNull(bossStepQueue)
        while (queue.isNotEmpty()) {
            val boss = queue.removeFirst()
            if (boss.stunnedTurns > 0) continue
            val target = nearestPlayerTo(boss)
            if (target == null) {
                queue.removeAll { it === boss }
                addLog("${boss.displayName} не видит никого из игроков и остаётся на месте")
                checkBossAttack(boss)
                if (checkGameEnd()) {
                    bossStepQueue = null
                    return false
                }
                continue
            }
            afterRolls {
                val cell = stepTowards(boss, target)
                if (!breakDoor(boss, cell)) {
                    addLog("${boss.displayName} переместился на (${cell.row},${cell.col})")
                    moveBossTo(boss, cell)
                }
                checkBossAttack(boss)
                if (checkGameEnd()) bossStepQueue = null
            }
            return !gameOver && d4Rolls.isEmpty()
        }
        bossStepQueue = null
        awaitingBossMove = false
        while (!turnOrder[currentTurnIndex].alive) {
            currentTurnIndex++
            if (currentTurnIndex >= turnOrder.size) currentTurnIndex = 0
        }
        updateStatus()
        return false
    }

    private fun prepareBossSteps(steps: Int) {
            bossStepQueue = ArrayDeque<BossUnit>().also { queue ->
                bosses.forEach { boss ->
                    if (boss.stunnedTurns > 0) {
                        boss.stunnedTurns--
                        addLog("${boss.displayName} оглушён и пропускает ход")
                    } else {
                        repeat(steps) { queue.addLast(boss) }
                    }
                }
            }
    }

    private fun updateStatus() {
        if (d4Rolls.isNotEmpty()) {
            statusText = "Бросок д4…"
            return
        }
        if (checkGameEnd()) return
        if (pendingChoice != null) return
        if (awaitingBossMove) return
        if (pendingCuckoldSteps > 0) {
            statusText = "${cuckoldController?.name}: управление боссом — осталось ходов: $pendingCuckoldSteps"
            return
        }
        if (pendingPuppetSteps > 0) {
            statusText = "Управление ${puppetTarget?.name} — осталось ходов: $pendingPuppetSteps"
            return
        }
        val current = turnOrder[currentTurnIndex]
        statusText = when {
            mustSkipCurrentTurn() -> "${current.name}: пропуск хода."
            canMoveAsKnight() && pendingSteps == 0 -> "${current.name}: ход конём — коснись клетки буквой «Г»."
            current.effects.has(CardId.HORSE) && pendingSteps == 0 -> "${current.name}: нет доступных ходов конём."
            pendingSteps > 0 -> "${current.name}: осталось шагов $pendingSteps. Коснись соседней клетки."
            attackCardPlayed -> "Карта разыграна. Теперь можно походить."
            current.activeCards.isNotEmpty() -> "Ход игрока: ${current.name}. Брось кубик или разыграй карту из инвентаря."
            else -> "Ход игрока: ${current.name}. Нажми «Бросить кубик»."
        }
    }
}
