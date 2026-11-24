package com.example.potionsanddungeons

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    @Test
    fun recapRecordsPickupsPlayedCardsAndWhereTheBossAttackStarted() {
        val engine = GameEngine(true, Random(1))
        engine.setupCardSandbox()
        val (survivor, victim) = engine.players
        val boss = engine.bosses.single()
        engine.currentTurnIndex = 1
        survivor.effects.add(ActiveEffect(CardId.INVISIBILITY, EffectCategory.PERMANENT))
        boss.position = Cell(1, 1)
        victim.position = Cell(0, 2)
        engine.potions = listOf(Potion(victim.position, DeckSource.BASE, CardId.SPEED))
        engine.checkPotionAt(victim)
        engine.continueAfterPotion()

        victim.activeCards.add(CardId.ATTACK)
        assertTrue(engine.playActiveCard(victim, CardId.ATTACK))
        engine.handleCellClick(Cell(0, 3))
        engine.pendingSteps = 1
        engine.handleCellClick(Cell(0, 1))
        engine.triggerBossMove()

        assertTrue(engine.gameOver)
        assertEquals(mapOf("Скорость" to 1), engine.recapFor(victim).potions)
        assertEquals(mapOf(CardId.ATTACK to 1), engine.recapFor(victim).playedCards)
        val elimination = engine.eliminations.single()
        assertSame(victim, elimination.player)
        assertEquals("Скорость", elimination.lastPotion)
        assertEquals(Cell(0, 1), elimination.lastTurnCell)
        assertEquals(Cell(0, 1), elimination.cell)
        assertEquals(EliminationCause.Boss("Босс", Cell(1, 1), Cell(0, 1), 0), elimination.cause)
    }

    @Test
    fun bossUpgradesApplyOnlyAfterRoundsEightAndTwelveAndAddToPotionBonuses() {
        val engine = GameEngine(false, Random(2), customRules = CustomRules(
            board = BoardOptions(size = 7, bossUpgrades = true)
        ))
        engine.setupGame(listOf("А", "Б"))
        val (target, other) = engine.players
        val boss = engine.bosses.single()
        fun hide(player: Player) {
            player.effects.add(ActiveEffect(CardId.INVISIBILITY, EffectCategory.PERMANENT))
        }
        fun completeRound() {
            engine.awaitingBossMove = true
            engine.triggerBossMove()
        }
        hide(target)
        hide(other)
        repeat(7) { completeRound() }
        assertEquals(0, engine.bossSpeedBonus)
        assertEquals(BossUpgrade.SPEED, engine.nextBossUpgrade)
        completeRound()
        assertEquals(8, engine.completedBossRounds)
        assertEquals(1, engine.bossSpeedBonus)

        target.effects.clear()
        target.position = Cell(3, 0)
        engine.awaitingBossMove = true
        assertTrue(engine.advanceBossStep())
        assertEquals(Cell(3, 2), boss.position)
        assertTrue(engine.advanceBossStep())
        assertEquals(Cell(3, 1), boss.position)
        assertEquals(8, engine.completedBossRounds)
        assertFalse(engine.advanceBossStep())
        assertEquals(9, engine.completedBossRounds)

        hide(target)
        boss.position = Cell(3, 3)
        repeat(2) { completeRound() }
        engine.increaseBossAttackRadius()
        assertEquals(1, engine.bossAttackRadiusBonus)
        assertEquals(BossUpgrade.ATTACK, engine.nextBossUpgrade)
        completeRound()
        assertEquals(12, engine.completedBossRounds)
        assertEquals(2, engine.bossAttackRadiusBonus)
        assertTrue(Cell(3, 5) in engine.bossAttackZoneCells())
        assertNull(engine.nextBossUpgrade)
        completeRound()
        assertEquals(2, engine.bossAttackRadiusBonus)
    }

    @Test
    fun curatedDeckKeepsExactCopiesEvenWhenTheBoardOrCrateCouldHoldMore() {
        val custom = CustomCard("Двойная скорость", listOf(CardId.SPEED, CardId.SPEED))
        val overrides = stockDeckCards(false, false, false).mapValues { (card, _) ->
            if (card == CardId.SHIELD) 1 else 0
        }
        val recipe = DeckRecipe(overrides, mapOf(custom.name to 2))
        val board = BoardOptions(customCards = listOf(custom), deckRecipe = recipe)
        val engine = GameEngine(false, Random(1), customRules = CustomRules(board = board))
        engine.setupGame(listOf("А", "Б"))
        assertEquals(3, engine.potions.size)
        assertEquals(1, engine.potions.count { it.cardId == CardId.SHIELD })
        assertEquals(2, engine.potions.count { it.card.custom == custom })
        engine.spawnPotionsAround(CENTER)
        assertEquals(3, engine.potions.size)
        val potion = engine.potions.first()
        engine.resolvePotionCard(engine.turnOrder.first(), potion)
        assertEquals(3, engine.potions.size)
        assertEquals(2, engine.potions.count { it.card.custom == custom })
    }

    @Test
    fun everyCellUsesTheOtherPileWhenACuratedDlcDeckRunsOutDuringSetup() {
        val overrides = stockDeckCards(true, false, false).mapValues { (card, _) ->
            when (card) {
                CardId.SHIELD -> 20
                CardId.SPEED -> 4
                CardId.TELEPORT -> 1
                else -> 0
            }
        }
        val board = BoardOptions(potionPlacement = PotionPlacement.EVERY_CELL, deckRecipe = DeckRecipe(overrides))
        assertNull(board.deckProblem(dlc = true))
        val engine = GameEngine(true, Random(1), customRules = CustomRules(board = board))
        engine.setupGame(listOf("А", "Б"))
        assertEquals(25, engine.potions.map { it.position }.distinct().size)
        assertEquals(1, engine.potions.count { it.source == DeckSource.DLC })
        val tooFew = board.copy(size = 7)
        assertNotNull(tooFew.deckProblem(dlc = true))
    }

    @Test
    fun customPotionWaitsForTargetDiceAndRewardBeforeApplyingLaterEffects() {
        val engine = GameEngine(true, Random(1), animateD4 = true)
        engine.setupCardSandbox()
        val player = engine.turnOrder.first()
        engine.potions = emptyList()
        val effects = listOf(CardId.SPEED, CardId.TELEPORT, CardId.ATTRACTION, CardId.CHOICE_SHIELD, CardId.SLOWDOWN)
        val potion = CustomCard("Цепочка", effects)
        engine.testCard(potion.effect, potion)
        assertEquals(true, engine.cardReveal?.showsAllEffects)
        assertTrue(player.effects.isEmpty())

        engine.continueAfterPotion()
        assertTrue(player.effects.has(CardId.SPEED))
        assertTrue(engine.pendingChoice is PendingChoice.ChooseCell)
        assertTrue(engine.d4Rolls.isEmpty())
        engine.handleCellClick(Cell(0, 0))
        assertTrue(engine.d4Rolls.isNotEmpty())
        assertNull(engine.cardReveal)
        assertFalse(player.effects.has(CardId.SLOWDOWN))
        while (engine.d4Rolls.isNotEmpty()) engine.finishD4Animation()

        assertEquals(3, engine.cardReveal?.effectIndex)
        assertEquals(CardId.SHIELD, engine.cardReveal?.choiceReward)
        assertFalse(player.effects.has(CardId.SLOWDOWN))
        engine.continueAfterPotion()
        assertTrue(player.effects.has(CardId.SHIELD))
        assertTrue(player.effects.has(CardId.SLOWDOWN))
        assertFalse(engine.hasPendingAction)
    }

    @Test
    fun nestedCustomPotionCompletesBeforeManualControlResumes() {
        val engine = GameEngine(true, Random(1), animateD4 = true)
        engine.setupCardSandbox()
        val (controller, controlled) = engine.players
        val first = CustomCard("Нити", listOf(CardId.PUPPETEER, CardId.SHIELD))
        val nested = CustomCard("Двойная скорость", listOf(CardId.SPEED, CardId.SPEED))
        engine.potions = listOf(Potion(Cell(0, 1), DeckSource.BASE, PotionCard(nested.effect, nested)))
        engine.testCard(first.effect, first)
        engine.continueAfterPotion()
        assertTrue(engine.resolvePlayerChoice(controlled))
        while (engine.d4Rolls.isNotEmpty()) engine.finishD4Animation()
        engine.pendingPuppetSteps = 2

        engine.handleCellClick(Cell(0, 1))
        assertEquals(nested, engine.cardReveal?.customCard)
        engine.continueAfterPotion()
        assertEquals(2, engine.movementEffectCount(controlled, CardId.SPEED))
        assertFalse(controller.effects.has(CardId.SHIELD))
        assertEquals(1, engine.pendingPuppetSteps)
        engine.handleCellClick(Cell(0, 0))
        assertTrue(controller.effects.has(CardId.SHIELD))
        assertFalse(engine.hasPendingAction)
    }

    @Test
    fun stepPreviewDistinguishesWalkingPastPotionsFromLandingAndSlipping() {
        val engine = GameEngine(false, Random(1))
        engine.setupCardSandbox()
        val player = engine.turnOrder.first()
        player.position = Cell(0, 0)
        val next = Cell(0, 1)
        engine.potions = listOf(Potion(next, DeckSource.BASE, CardId.SPEED))
        engine.pendingSteps = 2
        assertEquals(false, engine.previewStep(next)?.endsMovement)
        assertEquals(false, engine.previewStep(next)?.picksPotion)
        engine.pendingSteps = 1
        assertEquals(true, engine.previewStep(next)?.endsMovement)
        assertEquals(true, engine.previewStep(next)?.picksPotion)
        engine.bananaPeels.add(next)
        assertEquals(true, engine.previewStep(next)?.slips)
        assertEquals(false, engine.previewStep(next)?.picksPotion)
        assertNull(engine.previewStep(Cell(0, 2)))
        assertEquals(Cell(0, 0), player.position)
    }

    @Test
    fun customBoardRoutesAroundMissingCellsAndFillsOnlyItsOwnTiles() {
        val tiles = setOf(Cell(1, 1), Cell(2, 1), Cell(3, 1), Cell(3, 2), Cell(3, 3), Cell(2, 3), Cell(1, 3))
        val layout = BoardLayout("Подкова", 5, tiles,
            listOf(Cell(1, 3), Cell(3, 3), Cell(2, 3), Cell(3, 2)), bossStart = Cell(1, 1))
        val engine = GameEngine(false, Random(1), customRules = CustomRules(
            board = BoardOptions(5, PotionPlacement.EVERY_CELL, layout = layout)
        ))
        engine.setupGame(listOf("А", "Б"))
        val gap = Cell(1, 2)
        assertEquals(tiles, engine.potions.map { it.position }.toSet())
        assertEquals(layout.starts.take(2), engine.players.map { it.position })
        assertFalse(engine.isBoardTarget(gap))
        assertEquals(layout.bossStart, engine.traceUntilObstacle(layout.bossStart, Direction.RIGHT).second)

        engine.bossAttackRadiusBonus = 2
        assertFalse(layout.starts.first() in engine.bossAttackZoneCells())
        engine.awaitingBossMove = true
        engine.advanceBossStep()
        assertEquals(Cell(2, 1), engine.bosses.single().position)
        assertTrue(engine.players.all { it.alive })
    }

    @Test
    fun largeBoardSupportsMovementAndEffectsBeyondTheOriginalEdge() {
        val engine = GameEngine(true, Random(1), customRules = CustomRules(
            breakableDoors = true, board = BoardOptions(size = 7)
        ))
        engine.setupGame(listOf("А", "Б"))
        assertEquals(listOf(Cell(0, 0), Cell(6, 6)), engine.players.map { it.position })
        assertEquals(Cell(3, 3), engine.bosses.single().position)
        assertEquals(setOf(Cell(3, 2), Cell(3, 4)), engine.doors)
        assertEquals(8, engine.potions.size)
        val player = engine.turnOrder.first()
        player.position = Cell(5, 5)
        engine.pendingSteps = 2
        assertEquals(Cell(5, 6), engine.directionalMoveTarget(Direction.RIGHT))
        engine.handleCellClick(Cell(5, 6))
        assertEquals(Cell(5, 6), player.position)
        assertNull(engine.directionalMoveTarget(Direction.RIGHT))
        engine.bosses.single().position = Cell(6, 6)
        assertTrue(engine.occupiedAttackTargets(player).contains(Cell(6, 6)))
    }

    @Test
    fun collectedPotionMovesToAFreeCellWhenRandomReplacementIsEnabled() {
        val engine = GameEngine(false, Random(2), customRules = CustomRules(
            board = BoardOptions(size = 7, potionPlacement = PotionPlacement.RANDOM_REPLACEMENT)
        ))
        engine.setupGame(listOf("А", "Б"))
        val player = engine.turnOrder.first()
        val potion = engine.potions.first()
        val oldPosition = potion.position
        val occupied = engine.potions.map { it.position } + engine.players.map { it.position } +
            engine.bosses.map { it.position }
        player.position = oldPosition
        val card = potion.card
        engine.checkPotionAt(player)
        assertNotEquals(oldPosition, potion.position)
        assertFalse(potion.position in occupied)
        assertTrue(potion.position.inBounds(7))
        assertEquals(8, engine.potions.size)
        assertEquals(card.effect, engine.cardReveal?.card)
    }

    @Test
    fun fullLargeBoardRefillsEveryTileAndAppliesTheCustomCardEffect() {
        val custom = CustomCard("Флакон ветра", CardId.SPEED, "Порыв ветра")
        val engine = GameEngine(false, Random(3), customRules = CustomRules(
            bossDuel = true, board = BoardOptions(7, PotionPlacement.EVERY_CELL, listOf(custom))
        ))
        engine.setupGame(listOf("А"))
        val player = engine.players.single()
        val potion = engine.potions.single { it.card.custom == custom }
        player.position = potion.position
        val allCells = List(49) { Cell(it / 7, it % 7) }.toSet()
        assertEquals(allCells, engine.potions.map { it.position }.toSet())
        engine.checkPotionAt(player)
        assertEquals(custom, engine.cardReveal?.customCard)
        engine.continueAfterPotion()
        assertTrue(player.effects.has(CardId.SPEED))
        assertEquals(allCells, engine.potions.map { it.position }.toSet())
    }

    @Test
    fun directionalMoveRequiresAnAvailableStepAndNeverChoosesACardTarget() {
        val engine = GameEngine(false, Random(1))
        engine.setupCardSandbox()
        val player = engine.turnOrder.first()
        player.position = Cell(0, 1)
        assertNull(engine.directionalMoveTarget(Direction.RIGHT))

        engine.pendingSteps = 2
        assertNull(engine.directionalMoveTarget(Direction.UP))
        val destination = checkNotNull(engine.directionalMoveTarget(Direction.RIGHT))
        assertEquals(Cell(0, 2), destination)
        engine.handleCellClick(destination)
        assertEquals(1, engine.pendingSteps)
        assertNull(engine.directionalMoveTarget(Direction.LEFT))

        engine.requestChooseCell("Телепорт", allowed = { true }, onChosen = {})
        assertTrue(engine.isBoardTarget(Cell(0, 3)))
        assertNull(engine.directionalMoveTarget(Direction.RIGHT))
    }

    @Test
    fun duelEndsOnlyAfterCollectingAllKeysAndReturningToTheExit() {
        val engine = GameEngine(false, Random(1), customRules = CustomRules(bossDuel = true))
        engine.setupGame(listOf("А"))
        engine.potions = emptyList()
        val player = engine.players.single()
        assertFalse(engine.gameOver)
        assertEquals(3, engine.duelKeys.size)
        fun landOn(cell: Cell) {
            player.position = Cell(cell.row, if (cell.col == 0) 1 else cell.col - 1)
            engine.pendingSteps = 1
            engine.lastDirection = null
            engine.handleCellClick(cell)
        }
        landOn(checkNotNull(engine.duelExit))
        assertFalse(engine.gameOver)
        engine.duelKeys.toList().forEach { key ->
            engine.bosses.single().stunnedTurns = 1
            engine.triggerBossMove()
            landOn(key)
        }
        assertFalse(engine.gameOver)
        engine.bosses.single().stunnedTurns = 1
        engine.triggerBossMove()
        landOn(checkNotNull(engine.duelExit))
        assertTrue(engine.gameOver)
    }

    @Test
    fun separateMovementDiceKeepsRollsStableWhenCardRollsDiffer() {
        fun newAttempt() = GameEngine(false, Random(28), movementRandom = Random(41)).apply {
            setupGame(listOf("А", "Б"))
        }
        val first = newAttempt()
        val second = newAttempt()
        assertEquals(first.potions.map { it.cardId }, second.potions.map { it.cardId })
        second.withD4 { }
        second.withD4 { }
        assertEquals(first.rollDiceForCurrentPlayer(), second.rollDiceForCurrentPlayer())
        assertEquals(first.lastDiceRoll, second.lastDiceRoll)
    }

    @Test
    fun neighboringPlayersCanTradeOneCardEachAndThenFinishTheTurn() {
        val engine = GameEngine(false, Random(1), customRules = CustomRules(cardTrading = true))
        engine.setupCardSandbox()
        engine.potions = emptyList()
        val (trader, partner) = engine.players
        trader.position = Cell(0, 1)
        trader.activeCards.add(CardId.ATTACK)
        partner.activeCards.add(CardId.TARAN)
        engine.pendingSteps = 1
        engine.handleCellClick(Cell(0, 0))
        assertTrue(engine.tradePartners.isEmpty())
        assertEquals(partner, engine.turnOrder[engine.currentTurnIndex])

        partner.position = Cell(0, 2)
        engine.pendingSteps = 1
        engine.handleCellClick(Cell(0, 1))
        assertEquals(listOf(trader), engine.tradePartners)
        engine.finishTrade(trader, CardId.TARAN, CardId.ATTACK)
        assertEquals(listOf(CardId.TARAN), trader.activeCards)
        assertEquals(listOf(CardId.ATTACK), partner.activeCards)
        assertTrue(engine.tradePartners.isEmpty())
        assertTrue(engine.awaitingBossMove)
    }

    @Test
    fun choicePotionCanClearAnEffectOrGrantItsReward() {
        val engine = GameEngine(false, Random(1))
        engine.setupCardSandbox()
        val player = engine.turnOrder.first()
        engine.testCard(CardId.SLOWDOWN)
        engine.continueAfterPotion()

        engine.testCard(CardId.CHOICE_SHIELD)
        assertTrue(engine.cardReveal!!.canCleanse)
        engine.continueAfterPotion(cleanse = true)
        assertFalse(player.effects.has(CardId.SLOWDOWN))
        assertFalse(player.effects.has(CardId.SHIELD))

        engine.testCard(CardId.CHOICE_SPEED)
        assertFalse(engine.cardReveal!!.canCleanse)
        engine.continueAfterPotion()
        assertTrue(player.effects.has(CardId.SPEED))
    }

    @Test
    fun bossSpendsAStepOpeningADoor() {
        val engine = GameEngine(false, Random(1), customRules = CustomRules(breakableDoors = true))
        engine.setupGame(listOf("А", "Б"))
        val door = Cell(2, 1)
        val boss = engine.bosses.single()
        val start = boss.position
        engine.turnOrder.first().position = Cell(2, 0)
        engine.pendingSteps = 1
        assertFalse(engine.isBoardTarget(door))
        engine.pendingSteps = 0
        engine.awaitingBossMove = true
        engine.advanceBossStep()
        assertEquals(start, boss.position)
        assertFalse(door in engine.doors)
        assertTrue(Cell(2, 3) in engine.doors)
    }

    @Test
    fun sandboxUsesNormalPickupAndCardRules() {
        val engine = GameEngine(true, Random(1), animateD4 = true)
        engine.setupCardSandbox()
        val player = engine.turnOrder.first()
        val boss = engine.bosses.single()
        assertTrue(engine.canTestCard)

        engine.testCard(CardId.SHIELD)
        assertEquals(CardId.SHIELD, engine.cardReveal?.card)
        assertFalse(player.effects.has(CardId.SHIELD))
        engine.testCard(CardId.ATTACK)
        assertEquals(CardId.SHIELD, engine.cardReveal?.card)
        engine.continueAfterPotion()
        assertTrue(player.effects.has(CardId.SHIELD))

        engine.testCard(CardId.ATTACK)
        engine.continueAfterPotion()
        assertTrue(engine.playActiveCard(player, CardId.ATTACK))
        engine.handleCellClick(boss.position)
        assertEquals(1, boss.stunnedTurns)
        assertTrue(engine.attackCardPlayed)
    }

    @Test
    fun rerollCommitsNewResultAndIsAvailableOnlyOncePerPlayer() {
        val engine = GameEngine(false, ScriptedRolls(4, 1, 2, 4, 3), offerReroll = true)
        engine.setupGame(listOf("A", "B"))
        val player = engine.turnOrder.first()
        val target = player.position.neighbors4().first()
        assertEquals(2, engine.rollDiceForCurrentPlayer())
        assertFalse(engine.awaitingRollDecision)
        assertTrue(engine.isBoardTarget(target))
        assertTrue(engine.canReroll)
        assertEquals(4, engine.decideRoll(true))
        assertTrue(player.rerollUsed)
        assertEquals(4, engine.pendingSteps)

        engine.pendingSteps = 0
        assertEquals(3, engine.rollDiceForCurrentPlayer())
        assertFalse(engine.awaitingRollDecision)
        assertFalse(engine.canReroll)
        assertEquals(3, engine.pendingSteps)
    }

    @Test
    fun firstStepOrAttackCardClosesTheRerollWindow() {
        val engine = GameEngine(true, ScriptedRolls(4, 1, 3, 3), offerReroll = true)
        engine.setupCardSandbox()
        val player = engine.turnOrder.first()
        engine.rollDiceForCurrentPlayer()
        assertTrue(engine.canReroll)
        engine.handleCellClick(Cell(2, 0))
        assertFalse(engine.canReroll)
        assertEquals(0, engine.decideRoll(true))
        assertFalse(player.rerollUsed)

        val cardEngine = GameEngine(true, Random(1), offerReroll = true)
        cardEngine.setupCardSandbox()
        val cardPlayer = cardEngine.turnOrder.first()
        cardPlayer.activeCards.add(CardId.ATTACK)
        assertTrue(cardEngine.playActiveCard(cardPlayer, CardId.ATTACK))
        cardEngine.handleCellClick(cardEngine.bosses.single().position)
        cardEngine.rollDiceForCurrentPlayer()
        assertFalse(cardEngine.canReroll)
        assertFalse(cardPlayer.rerollUsed)
    }

    @Test
    fun tutorialTeachesPotionPickupAndAttackBeforeFinishing() {
        val engine = GameEngine(true, kotlin.random.Random(1))
        engine.setupTutorial()
        val player = engine.turnOrder.first()
        val boss = engine.bosses.single()
        assertEquals(1, engine.rollDiceForCurrentPlayer())
        engine.clickAndContinue(Cell(0, 1))
        assertTrue(CardId.ATTACK in player.activeCards)
        repeat(2) { engine.playBotAction() }
        engine.triggerBossMove()
        assertEquals(Cell(1, 1), boss.position)

        assertTrue(engine.playActiveCard(player, CardId.ATTACK))
        assertTrue(engine.isBoardTarget(boss.position))
        engine.handleCellClick(boss.position)
        assertEquals(1, boss.stunnedTurns)
        assertEquals(player, engine.turnOrder[engine.currentTurnIndex])
        assertFalse(engine.awaitingBossMove)
        player.activeCards.add(CardId.ATTACK)
        assertFalse(engine.playActiveCard(player, CardId.ATTACK))
        assertEquals(1, engine.rollDiceForCurrentPlayer())
        assertTrue(engine.isBoardTarget(Cell(0, 2)))
        engine.clickAndContinue(Cell(0, 2))
        assertEquals(Cell(0, 2), player.position)
        repeat(2) { engine.playBotAction() }
        engine.triggerBossMove()
        assertEquals(2, engine.bossMoveTrigger)
        assertFalse(engine.awaitingBossMove)
        assertEquals(Cell(1, 1), boss.position)
    }
    @Test
    fun botPlansTwoStepsToAPotionEvenWhenTheFirstStepLeadsTowardTheBoss() {
        val engine = GameEngine(false, ScriptedRolls(4, 1, 2))
        engine.setupGame(listOf("Бот", "Игрок"), botSlots = setOf(0))
        val bot = engine.turnOrder.first()
        bot.position = Cell(0, 1)
        val potionCell = Cell(1, 2)
        engine.potions = listOf(Potion(potionCell, DeckSource.BASE, CardId.SHIELD))

        repeat(3) { engine.playBotAction() }

        assertEquals(potionCell, bot.position)
        assertEquals(CardId.SHIELD, engine.cardReveal?.card)
    }
    @Test
    fun botRollsAndCompletesItsMove() {
        val engine = GameEngine(false, ScriptedRolls(4, 1, 1))
        engine.setupGame(listOf("Бот", "Игрок"), botSlots = setOf(0))
        val bot = engine.turnOrder.first()
        val start = bot.position

        engine.playBotAction()
        assertEquals(1, engine.pendingSteps)
        engine.playBotAction()

        assertTrue(bot.position in start.neighbors4())
        assertEquals(1, engine.currentTurnIndex)
    }

    @Test
    fun fourMovementEffectsArePermanentAndBrokenShieldHasTwoCopies() {
        val engine = game()
        val player = engine.turnOrder.first()
        val cards = listOf(CardId.SPEED, CardId.SLOWDOWN, CardId.DISABILITY, CardId.DAUN)
        cards.forEach { card ->
            val source = if (card.isDlc) DeckSource.DLC else DeckSource.BASE
            engine.pickupAndContinue(player, Potion(player.position, source, card))
        }
        assertEquals(cards, player.effects.map { it.id })
        assertTrue(player.effects.all { it.category == EffectCategory.PERMANENT })
        assertEquals(2, CARD_QUANTITIES[CardId.BROKEN_SHIELD])
    }
    @Test
    fun stunOnlySchedulesSkippedTurnAndDoesNotGrantInvisibility() {
        val engine = game()
        val player = engine.turnOrder.first()
        val potion = Potion(player.position, DeckSource.BASE, CardId.STUN)
        engine.resolvePotionCard(player, potion)
        assertEquals(CardId.STUN, engine.cardReveal?.card)
        assertFalse(player.skipNextTurn)
        engine.continueAfterPotion()
        assertTrue(player.skipNextTurn)
        assertFalse(player.effects.has(CardId.INVISIBILITY))
        engine.rollDiceForCurrentPlayer()
        assertFalse(player.skipNextTurn)
        assertFalse(player.effects.has(CardId.INVISIBILITY))
        assertEquals(1, engine.currentTurnIndex)
    }
    @Test
    fun animatedInitiativePublishesOrderAfterAllRolls() {
        val engine = GameEngine(true, ScriptedRolls(4, 1), animateD4 = true)
        engine.setupGame(listOf("A", "B"))
        assertTrue(engine.turnOrder.isEmpty())
        assertEquals(listOf(4, 1), engine.d4Rolls.toList())
        engine.finishD4Animation()
        assertTrue(engine.turnOrder.isEmpty())
        engine.finishD4Animation()
        assertEquals(listOf("A", "B"), engine.turnOrder.map { it.name })
    }

    @Test
    fun animatedAttractionWaitsForItsRoll() {
        val engine = GameEngine(true, ScriptedRolls(4, 1, 2), animateD4 = true)
        engine.setupGame(listOf("A", "B"))
        while (engine.d4Rolls.isNotEmpty()) engine.finishD4Animation()
        val player = engine.turnOrder.first()
        val start = player.position
        engine.pullAllPlayersToRandomWall()
        assertEquals(start, player.position)
        assertEquals(listOf(2), engine.d4Rolls.toList())
        engine.finishD4Animation()
        assertEquals(Cell(0, 4), player.position)
        assertTrue(engine.d4Rolls.isEmpty())
    }

    @Test
    fun animatedChaosRollWaitsBeforeBossMoves() {
        val engine = GameEngine(true, ScriptedRolls(4, 1, 2), animateD4 = true)
        engine.setupGame(listOf("A", "B"))
        while (engine.d4Rolls.isNotEmpty()) engine.finishD4Animation()
        engine.turnOrder.first().position = Cell(2, 0)
        engine.bossChaosRoll = true
        engine.awaitingBossMove = true
        assertFalse(engine.advanceBossStep())
        assertEquals(CENTER, engine.bosses.single().position)
        engine.finishD4Animation()
        assertEquals(CENTER, engine.bosses.single().position)
        assertTrue(engine.advanceBossStep())
        assertEquals(Cell(2, 1), engine.bosses.single().position)
    }
    @Test
    fun bossWaitsForPotionDescriptionAndMovesOneCellPerStep() {
        val engine = game()
        engine.turnOrder.first().position = Cell(2, 0)
        engine.bossSpeedBonus = 1
        engine.awaitingBossMove = true
        engine.resolvePotionCard(engine.turnOrder.first(), Potion(Cell(2, 0), DeckSource.BASE, CardId.SHIELD))

        assertFalse(engine.advanceBossStep())
        assertEquals(CENTER, engine.bosses.single().position)
        assertTrue(engine.awaitingBossMove)

        while (engine.cardReveal != null) engine.continueAfterPotion()
        assertTrue(engine.advanceBossStep())
        assertEquals(Cell(2, 1), engine.bosses.single().position)
        assertTrue(engine.awaitingBossMove)
    }
    @Test
    fun hypnosisWaitsForContinue() {
        val engine = game()
        val player = engine.turnOrder.first()
        val start = player.position
        engine.resolvePotionCard(player, Potion(start, DeckSource.BASE, CardId.HYPNOSIS))
        assertEquals(start, player.position)
        assertNotNull(engine.cardReveal)
        engine.continueAfterPotion()
        assertEquals(Cell(1, 1), player.position)
        assertNull(engine.cardReveal)
    }
    @Test
    fun catalogDescribesEveryPotion() {
        assertEquals(CardId.entries.toSet(), CARD_DESCRIPTIONS.keys)
        assertTrue(CARD_DESCRIPTIONS.values.all { it.isNotBlank() })
    }

    @Test
    fun boardTargetsFollowWalkingAndPendingChoiceRules() {
        val engine = game()
        assertFalse(engine.isBoardTarget(Cell(0, 1)))
        engine.pendingSteps = 2
        engine.clickAndContinue(Cell(0, 1))
        assertFalse(engine.isBoardTarget(Cell(0, 0)))
        assertTrue(engine.isBoardTarget(Cell(1, 1)))
        assertFalse(engine.isBoardTarget(CENTER))

        engine.requestChooseCell("Телепорт", allowed = { it != CENTER }, onChosen = {})
        assertTrue(engine.isBoardTarget(Cell(0, 0)))
        assertFalse(engine.isBoardTarget(CENTER))
    }

    @Test
    fun initiativeTieKeepsPlayersWhoDidNotTie() {
        val engine = GameEngine(false, ScriptedRolls(4, 4, 2, 3, 1))
        engine.setupGame(listOf("A", "B", "C"))
        assertEquals(listOf("A", "B", "C"), engine.turnOrder.map { it.name })
    }

    @Test
    fun initiativeKeepsDistinctInitialRollsInDescendingOrder() {
        val engine = GameEngine(false, ScriptedRolls(2, 4, 1, 3))
        engine.setupGame(listOf("A", "B", "C", "D"))

        assertEquals(listOf("B", "D", "A", "C"), engine.turnOrder.map { it.name })
    }

    @Test
    fun immunityIsConsumedOnlyByTheFirstNegativePotion() {
        val engine = game()
        val player = engine.turnOrder.first()
        engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.IMMUNITY))

        engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.SPEED))
        assertEquals(listOf(CardId.IMMUNITY, CardId.SPEED), player.effects.map { it.id })
        engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.STUN))
        assertFalse(player.skipNextTurn)
        assertFalse(player.effects.has(CardId.IMMUNITY))
        engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.STUN))
        assertTrue(player.skipNextTurn)
    }

    @Test
    fun movementEffectsStackAndOffsetEachOtherBeforeSkipping() {
        val engine = GameEngine(false, ScriptedRolls(4, 1, 1))
        engine.setupGame(listOf("А", "Б"))
        val player = engine.turnOrder.first()
        repeat(3) { engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.SPEED)) }
        repeat(2) { engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.SLOWDOWN)) }
        assertEquals(2, engine.rollDiceForCurrentPlayer())
    }

    @Test
    fun stackedSlowdownStillAllowsARerollBeforeTheTurnIsSkipped() {
        val engine = GameEngine(false, ScriptedRolls(4, 1, 2, 4), offerReroll = true)
        engine.setupGame(listOf("А", "Б"))
        val player = engine.turnOrder.first()
        repeat(2) { engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.SLOWDOWN)) }
        engine.rollDiceForCurrentPlayer()
        assertEquals(0, engine.pendingSteps)
        assertTrue(engine.canReroll)
        assertEquals(2, engine.decideRoll(true))
    }

    @Test
    fun oneShieldOnlyTriggersOneWaitingBrokenShield() {
        val engine = game()
        val player = engine.turnOrder.first()
        repeat(2) {
            engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.BROKEN_SHIELD))
        }
        engine.pickupAndContinue(player, Potion(player.position, DeckSource.BASE, CardId.SHIELD))

        assertEquals(listOf(CardId.BROKEN_SHIELD), player.effects.map { it.id })
    }

    @Test
    fun playerCanCrossBossDuringPlayerTurn() {
        val engine = GameEngine(false, ScriptedRolls(4, 3, 2)).also {
            it.setupGame(listOf("A", "B", "C"))
        }
        val player = engine.turnOrder.first()
        player.position = Cell(2, 1)
        engine.pendingSteps = 3

        engine.clickAndContinue(CENTER)

        assertTrue(player.alive)
        assertEquals(2, engine.pendingSteps)
        engine.clickAndContinue(Cell(2, 3))
        assertEquals(Cell(2, 3), player.position)
    }

    @Test
    fun bossCellRemainsAvailableInBaseGameWhenReverseIsForbidden() {
        val engine = game(dlc = false)
        val player = engine.turnOrder.first()
        player.position = Cell(1, 0)
        engine.bosses.single().position = Cell(0, 1)
        engine.pendingSteps = 2

        engine.clickAndContinue(Cell(0, 0))

        assertEquals(Cell(0, 0), player.position)
        assertEquals(1, engine.pendingSteps)
        assertTrue(engine.isBoardTarget(Cell(0, 1)))
    }

    @Test
    fun dlcBlocksBossCellButAllowsWalkingThroughItsAttackRadius() {
        val engine = game()
        val player = engine.turnOrder.first()
        player.position = Cell(2, 1)
        engine.bosses.single().position = CENTER
        engine.increaseBossAttackRadius()
        engine.pendingSteps = 3

        assertFalse(engine.isBoardTarget(CENTER))
        engine.handleCellClick(CENTER)
        assertEquals(Cell(2, 1), player.position)
        assertEquals(3, engine.pendingSteps)

        val adjacent = Cell(1, 2)
        engine.handleCellClick(Cell(1, 1))
        assertTrue(engine.isBoardTarget(adjacent))
        engine.handleCellClick(adjacent)
        assertEquals(adjacent, player.position)
        assertEquals(1, engine.pendingSteps)
        assertTrue(player.alive)
    }

    @Test
    fun werewolfDoesNotAttackDuringAnIntermediateStep() {
        val engine = game()
        val (wolf, victim) = engine.turnOrder
        wolf.effects.add(ActiveEffect(CardId.WEREWOLF, EffectCategory.TEMPORARY, turnsLeft = 2))
        victim.position = Cell(0, 1)
        engine.pendingSteps = 2

        engine.clickAndContinue(victim.position)

        assertTrue(victim.alive)
        assertFalse(engine.gameOver)
        assertEquals(1, engine.pendingSteps)
    }

    @Test
    fun werewolfAttacksOnTheLastStep() {
        val engine = game()
        val (wolf, victim) = engine.turnOrder
        wolf.effects.add(ActiveEffect(CardId.WEREWOLF, EffectCategory.TEMPORARY, turnsLeft = 2))
        victim.position = Cell(0, 1)
        engine.pendingSteps = 1
        engine.clickAndContinue(victim.position)
        assertFalse(victim.alive)
        assertTrue(engine.gameOver)
    }

    @Test
    fun shieldAbsorbsWerewolfAttackOnTheLastStep() {
        val engine = game()
        val (wolf, victim) = engine.turnOrder
        wolf.effects.add(ActiveEffect(CardId.WEREWOLF, EffectCategory.TEMPORARY, turnsLeft = 2))
        victim.effects.add(ActiveEffect(CardId.SHIELD, EffectCategory.PERMANENT))
        victim.position = Cell(0, 1)
        engine.pendingSteps = 1
        engine.clickAndContinue(victim.position)
        assertTrue(victim.alive)
        assertFalse(victim.effects.has(CardId.SHIELD))
    }

    @Test
    fun duplicateBossUsesAFreeCellWhenAllCornersAreOccupied() {
        val engine = GameEngine(false, ScriptedRolls(4, 3, 2, 1)).also {
            it.setupGame(listOf("A", "B", "C", "D"))
        }
        engine.spawnSecondBoss()

        assertEquals(2, engine.bosses.map { it.position }.toSet().size)
        assertTrue(engine.players.none { player -> engine.bosses.any { it.position == player.position } })
    }

    @Test
    fun stunnedBossSkipsTheWholePhaseEvenWithExtraSteps() {
        val engine = game()
        val boss = engine.bosses.single()
        boss.stunnedTurns = 1
        engine.bossSpeedBonus = 3
        engine.awaitingBossMove = true

        engine.triggerBossMove()

        assertEquals(CENTER, boss.position)
        assertEquals(0, boss.stunnedTurns)
        assertTrue(engine.players.all { it.alive })
    }

    @Test
    fun bossBananaStopsRemainingStepsAndStunsNextPhase() {
        val engine = game()
        val (target, other) = engine.turnOrder
        target.position = Cell(2, 0)
        other.position = Cell(4, 4)
        val peel = Cell(2, 1)
        engine.bananaPeels.add(peel)
        engine.bossSpeedBonus = 2
        engine.awaitingBossMove = true

        engine.triggerBossMove()

        val boss = engine.bosses.single()
        assertEquals(peel, boss.position)
        assertEquals(1, boss.stunnedTurns)
        assertTrue(target.alive)
        assertTrue(engine.bananaPeels.isEmpty())
    }

    @Test
    fun shieldSurvivesAsOneBossHitWhenAnotherPlayerMoves() {
        val engine = game()
        val (safePlayer, shieldedPlayer) = engine.turnOrder
        shieldedPlayer.position = CENTER
        shieldedPlayer.effects.add(ActiveEffect(CardId.SHIELD, EffectCategory.PERMANENT))

        engine.pendingSteps = 1

        engine.clickAndContinue(Cell(0, 1))

        assertTrue(shieldedPlayer.alive)
        assertTrue(shieldedPlayer.effects.has(CardId.SHIELD))
        engine.awaitingBossMove = true
        engine.triggerBossMove()
        assertTrue(shieldedPlayer.alive)
        assertFalse(shieldedPlayer.effects.has(CardId.SHIELD))
        assertEquals(1, engine.currentTurnIndex)
        assertEquals(Cell(0, 1), safePlayer.position)
    }

    @Test
    fun shieldStopsRamPushAndStun() {
        val engine = game()
        val (rammer, target) = engine.turnOrder
        target.position = Cell(0, 1)
        target.effects.add(ActiveEffect(CardId.SHIELD, EffectCategory.PERMANENT))

        engine.taranMove(rammer, Direction.RIGHT)

        assertEquals(Cell(0, 1), target.position)
        assertEquals(0, target.stunnedTurns)
        assertFalse(target.effects.has(CardId.SHIELD))
    }

    @Test
    fun rewindUsesControlledPlayersOwnStartingCell() {
        val engine = GameEngine(true, ScriptedRolls(4, 1, 2)).also {
            it.setupGame(listOf("A", "B"))
        }
        val (controller, target) = engine.turnOrder
        val start = target.position
        val landing = start.neighbors4().first()
        engine.potions = listOf(Potion(landing, DeckSource.BASE, CardId.TIME_REWIND))
        engine.startPuppeteerControl(controller, target)

        engine.clickAndContinue(landing)

        assertEquals(start, target.position)
        assertFalse(engine.hasPendingAction)
    }

    @Test
    fun nestedPuppeteerResumesPreviousControlAfterTheNewTargetMoves() {
        val engine = GameEngine(true, ScriptedRolls(4, 3, 2, 3, 1)).also {
            it.setupGame(listOf("A", "B", "C"))
        }
        val (controller, first, second) = engine.turnOrder
        val potionCell = first.position.neighbors4().first()
        engine.potions = listOf(Potion(potionCell, DeckSource.DLC, CardId.PUPPETEER))
        engine.startPuppeteerControl(controller, first)

        engine.clickAndContinue(potionCell)
        assertTrue(engine.resolvePlayerChoice(second))
        engine.clickAndContinue(second.position.neighbors4().first())

        assertSame(first, engine.puppetTarget)
        assertEquals(2, engine.pendingPuppetSteps)
        assertTrue(engine.statusText.contains(first.name))
    }

    @Test
    fun stackedPlayerTargetsCanBeSelectedIndividually() {
        val engine = GameEngine(true, ScriptedRolls(4, 3, 2)).also {
            it.setupGame(listOf("A", "B", "C"))
        }
        val (thief, first, second) = engine.turnOrder
        first.position = second.position
        first.effects.add(ActiveEffect(CardId.SHIELD, EffectCategory.PERMANENT))
        second.effects.add(ActiveEffect(CardId.SPEED, EffectCategory.TEMPORARY, turnsLeft = 2))
        thief.activeCards.add(CardId.THIEF)
        assertTrue(engine.playActiveCard(thief, CardId.THIEF))

        engine.clickAndContinue(second.position)
        assertTrue(engine.pendingChoice is PendingChoice.ChoosePlayer)
        assertTrue(engine.resolvePlayerChoice(second))

        assertTrue(thief.effects.has(CardId.SPEED))
        assertTrue(first.effects.has(CardId.SHIELD))
        assertFalse(second.effects.has(CardId.SPEED))
    }

    @Test
    fun stolenEffectWithOneTurnLeftSurvivesTheTurnSpentStealingIt() {
        val engine = game()
        val (thief, victim) = engine.turnOrder
        victim.effects.add(ActiveEffect(CardId.SPEED, EffectCategory.TEMPORARY, turnsLeft = 1))
        thief.activeCards.add(CardId.THIEF)
        assertTrue(engine.playActiveCard(thief, CardId.THIEF))

        engine.clickAndContinue(victim.position)

        assertEquals(1, thief.effects.single().turnsLeft)
        assertEquals(1, engine.currentTurnIndex)
    }

    @Test
    fun cleanseRemovesSkipFlagsAndStunButLeavesInventory() {
        val engine = game()
        val (holder, target) = engine.turnOrder
        target.stunnedTurns = 1
        target.skipNextTurn = true
        target.activeCards.add(CardId.THIEF)
        target.effects.add(ActiveEffect(CardId.SHIELD, EffectCategory.PERMANENT))

        engine.pickupAndContinue(holder, Potion(holder.position, DeckSource.DLC, CardId.CLEANSE))

        assertFalse(target.skipNextTurn)
        assertEquals(0, target.stunnedTurns)
        assertTrue(target.effects.isEmpty())
        assertEquals(listOf(CardId.THIEF), target.activeCards)
    }

    @Test
    fun daunsExtraStepWaitsForTeleportAndResolvesBeforeTurnChanges() {
        val engine = GameEngine(true, ScriptedRolls(4, 1, 2)).also {
            it.setupGame(listOf("A", "B"))
        }
        val player = engine.turnOrder.first()
        player.effects.add(ActiveEffect(CardId.DAUN, EffectCategory.TEMPORARY, turnsLeft = 2))
        engine.pendingSteps = 1
        val potionCell = Cell(0, 1)
        val teleportCell = Cell(1, 0)
        engine.potions = listOf(Potion(potionCell, DeckSource.DLC, CardId.TELEPORT))

        engine.clickAndContinue(potionCell)
        assertEquals(0, engine.currentTurnIndex)
        engine.clickAndContinue(teleportCell)

        assertEquals(Cell(1, 1), player.position)
        assertEquals(1, engine.currentTurnIndex)
    }

    @Test
    fun revealAllIncludesTheReplacementPotion() {
        val engine = game()
        val player = engine.turnOrder.first()
        val potion = Potion(player.position, DeckSource.DLC, CardId.LET_THERE_BE_LIGHT)
        engine.potions = engine.potions + potion

        engine.pickupAndContinue(player, potion)

        assertTrue(engine.potions.all { it.revealed })
    }

    @Test
    fun seededGamesCanAdvanceAcrossAllPlayerCountsAndDecks() {
        for (dlc in listOf(false, true)) {
            for (playerCount in 2..4) {
                repeat(40) { seed ->
                    val engine = GameEngine(dlc, Random(seed))
                    engine.setupGame(List(playerCount) { "P${it + 1}" })
                    val input = Random(seed + 100)
                    repeat(200) {
                        if (!engine.gameOver) playAction(engine, input)
                    }
                    assertTrue(engine.players.all { it.position.inBounds() })
                    assertTrue(engine.bosses.all { it.position.inBounds() })
                    if (engine.gameOver) {
                        assertFalse(engine.hasPendingAction)
                    } else {
                        assertTrue(engine.turnOrder[engine.currentTurnIndex].alive || engine.hasPendingAction || engine.awaitingBossMove)
                    }
                }
            }
        }
    }

    private fun playAction(engine: GameEngine, input: Random) {
        while (engine.cardReveal != null) engine.continueAfterPotion()
        val choice = engine.pendingChoice
        val current = engine.turnOrder[engine.currentTurnIndex]
        when {
            choice is PendingChoice.ChooseEffect -> engine.resolveEffectChoice(choice.from.effects.random(input))
            choice is PendingChoice.ChoosePlayer -> engine.resolvePlayerChoice(engine.players.filter(choice.allowed).random(input))
            choice is PendingChoice.ChooseCell -> engine.clickAndContinue(boardCells.filter(choice.allowed).random(input))
            engine.awaitingBossMove -> engine.triggerBossMove()
            engine.pendingCuckoldSteps > 0 -> engine.clickAndContinue(engine.bosses.first().position.neighbors4().random(input))
            engine.pendingPuppetSteps > 0 -> {
                val choices = checkNotNull(engine.puppetTarget).position.neighbors4().filter { cell ->
                    engine.bosses.none { it.position == cell }
                }
                engine.clickAndContinue(choices.random(input))
            }
            engine.pendingSteps > 0 -> {
                val choices = Direction.entries.filter { direction ->
                    val cell = Cell(current.position.row + direction.dr, current.position.col + direction.dc)
                    cell.inBounds() && direction != engine.lastDirection?.opposite()
                }.shuffled(input)
                val start = current.position
                assertTrue(choices.any { direction ->
                    engine.clickAndContinue(Cell(start.row + direction.dr, start.col + direction.dc))
                    current.position != start || engine.pendingSteps == 0 || engine.hasPendingAction
                })
            }
            current.activeCards.isNotEmpty() && input.nextBoolean() &&
                engine.playActiveCard(current, current.activeCards.random(input)) -> Unit
            engine.canMoveAsKnight() && !engine.mustSkipCurrentTurn() -> {
                val start = current.position
                val cell = boardCells.filter {
                    kotlin.math.abs(it.row - start.row) * kotlin.math.abs(it.col - start.col) == 2 &&
                        engine.bosses.none { boss -> boss.position == it }
                }.random(input)
                engine.clickAndContinue(cell)
            }
            else -> engine.rollDiceForCurrentPlayer()
        }
    }

    private val boardCells = (0 until BOARD_SIZE).flatMap { row ->
        (0 until BOARD_SIZE).map { col -> Cell(row, col) }
    }

    @Test
    fun thiefWithoutTargetsKeepsCardAndTurn() {
        val engine = game()
        val thief = engine.turnOrder.first()
        thief.activeCards.add(CardId.THIEF)

        assertFalse(engine.playActiveCard(thief, CardId.THIEF))
        assertTrue(CardId.THIEF in thief.activeCards)
        assertFalse(engine.hasPendingAction)
        assertEquals(0, engine.currentTurnIndex)
    }

    @Test
    fun thiefConsumesCardAfterSelectingOneOfSeveralEffects() {
        val engine = game()
        val (thief, victim) = engine.turnOrder
        val shield = ActiveEffect(CardId.SHIELD, EffectCategory.PERMANENT)
        val speed = ActiveEffect(CardId.SPEED, EffectCategory.TEMPORARY, turnsLeft = 2)
        victim.effects.addAll(listOf(shield, speed))
        thief.activeCards.add(CardId.THIEF)

        assertTrue(engine.playActiveCard(thief, CardId.THIEF))
        engine.clickAndContinue(victim.position)
        assertTrue(engine.pendingChoice is PendingChoice.ChooseEffect)
        assertTrue(CardId.THIEF in thief.activeCards)
        assertEquals(0, engine.rollDiceForCurrentPlayer())
        engine.resolveEffectChoice(shield)

        assertNull(engine.pendingChoice)
        assertFalse(CardId.THIEF in thief.activeCards)
        assertTrue(thief.effects.has(CardId.SHIELD))
        assertEquals(listOf(speed), victim.effects.toList())
        assertEquals(1, engine.currentTurnIndex)
    }

    @Test
    fun lastPlayerTeleportFinishesBeforeBossPhase() {
        val engine = game()
        engine.currentTurnIndex = 1
        val player = engine.turnOrder[1]
        val potionCell = player.position.neighbors4().first()
        engine.potions = listOf(Potion(potionCell, DeckSource.DLC, CardId.TELEPORT))
        engine.pendingSteps = 1

        engine.clickAndContinue(potionCell)
        assertTrue(engine.pendingChoice is PendingChoice.ChooseCell)
        assertEquals(1, engine.currentTurnIndex)
        assertFalse(engine.awaitingBossMove)

        val destination = Cell(0, 2)
        engine.clickAndContinue(destination)
        assertEquals(destination, player.position)
        assertNull(engine.pendingChoice)
        assertTrue(engine.awaitingBossMove)
        assertEquals(0, engine.currentTurnIndex)
    }

    @Test
    fun stunBlocksKnightMoveAndConsumesExactlyOneTurn() {
        val engine = game()
        val player = engine.turnOrder.first()
        val start = player.position
        val target = Cell(1, 2)
        player.effects.add(ActiveEffect(CardId.HORSE, EffectCategory.TEMPORARY, turnsLeft = 2))
        player.stunnedTurns = 1

        engine.clickAndContinue(target)
        assertEquals(start, player.position)
        assertEquals(0, engine.rollDiceForCurrentPlayer())
        assertEquals(0, player.stunnedTurns)
        assertEquals(1, engine.currentTurnIndex)
    }

    @Test
    fun bananaOnKnightLandingInterruptsTurnBeforePickingUpPotion() {
        val engine = game()
        val player = engine.turnOrder.first()
        val target = Cell(1, 2)
        player.effects.add(ActiveEffect(CardId.HORSE, EffectCategory.TEMPORARY, turnsLeft = 2))
        engine.bananaPeels.add(target)
        engine.potions = listOf(Potion(target, DeckSource.DLC, CardId.TELEPORT))

        engine.clickAndContinue(target)
        assertEquals(target, player.position)
        assertEquals(1, player.stunnedTurns)
        assertFalse(target in engine.bananaPeels)
        assertNull(engine.pendingChoice)
        assertEquals(1, engine.currentTurnIndex)
    }

    private fun GameEngine.clickAndContinue(cell: Cell) {
        handleCellClick(cell)
        while (cardReveal != null) continueAfterPotion()
    }

    private fun GameEngine.pickupAndContinue(player: Player, potion: Potion) {
        resolvePotionCard(player, potion)
        while (cardReveal != null) continueAfterPotion()
    }
    private fun game(dlc: Boolean = true) = GameEngine(dlc, ScriptedRolls(4, 1)).also {
        it.setupGame(listOf("A", "B"))
    }

    private class ScriptedRolls(vararg values: Int) : Random() {
        private val rolls = values.iterator()
        private val layoutRandom = Random(0)

        override fun nextInt(from: Int, until: Int): Int = rolls.nextInt().also {
            require(it in from until until)
        }

        override fun nextInt(until: Int): Int = layoutRandom.nextInt(until)

        override fun nextBits(bitCount: Int): Int = layoutRandom.nextBits(bitCount)
    }
}
