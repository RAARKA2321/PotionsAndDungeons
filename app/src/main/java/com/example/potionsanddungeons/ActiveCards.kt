package com.example.potionsanddungeons

// Активные карты не живут в EffectRegistry — они хранятся в инвентаре игрока
// и разыгрываются в начале хода.
object ActiveCardResolver {
    fun allowsMovementAfterUse(card: CardId): Boolean = when (card) {
        CardId.ATTACK, CardId.PIXIE, CardId.TARAN -> true
        else -> false
    }

    fun resolve(engine: GameEngine, player: Player, card: CardId) {
        when (card) {
            CardId.ATTACK -> resolveAttack(engine, player)
            CardId.MAGNET -> resolveMagnet(engine, player)
            CardId.PIXIE -> resolvePixie(engine, player)
            CardId.BANANA_PEEL -> resolveBananaPeel(engine, player)
            CardId.TARAN -> resolveTaran(engine, player)
            CardId.THIEF -> resolveThief(engine, player)
            else -> engine.addLog("Карта ${card.displayName()} не является активной")
        }
    }

    private fun resolveAttack(engine: GameEngine, player: Player) {
        val candidates = if (player.isBot && player.botStyle.considersCardTargets) {
            engine.occupiedAttackTargets(player)
        } else {
            player.position.neighbors4(engine.boardSize).filter(engine::isBoardCell)
        }
        engine.requestChooseCell(
            prompt = "Выбери соседнюю клетку для атаки",
            allowed = { it in candidates }
        ) { cell ->
            engine.players.find { it.alive && it.position == cell }?.let { target ->
                engine.absorbHitOrElse(target, "${target.name}: щит поглотил атаку ${player.name} и разрушился") {
                    target.stunnedTurns = maxOf(target.stunnedTurns, 1)
                    engine.addLog("${player.name} атаковал ${target.name} — тот оглушён на 1 ход")
                }
            }
            engine.bosses.find { it.position == cell }?.let {
                it.stunnedTurns = maxOf(it.stunnedTurns, 1)
                engine.addLog("${player.name} атаковал босса — тот оглушён на 1 ход")
            }
        }
    }


    private fun resolveMagnet(engine: GameEngine, player: Player) {
        val nearest = engine.potions.minByOrNull { manhattan(it.position, player.position) }
        if (nearest == null) {
            engine.addLog("${player.name} применил Магнит, но зелий на поле не осталось")
            return
        }
        engine.addLog("${player.name} притянул карту себе с клетки (${nearest.position.row},${nearest.position.col})")
        engine.resolvePotionCard(player, nearest)
    }

    private fun resolvePixie(engine: GameEngine, player: Player) {
        engine.withD4 { roll ->
        val direction = Wall.directionByNumber(roll)
        val (hitActor, _) = engine.traceUntilObstacle(player.position, direction)
        when (hitActor) {
            is Player -> engine.absorbHitOrElse(hitActor, "${hitActor.name}: щит поглотил удар пикси и разрушился") {
                hitActor.stunnedTurns = maxOf(hitActor.stunnedTurns, 1)
                engine.addLog("Пикси врезалась в ${hitActor.name} — тот оглушён на 1 ход")
            }
            is BossUnit -> { hitActor.stunnedTurns = maxOf(hitActor.stunnedTurns, 1); engine.addLog("Пикси врезалась в босса — тот оглушён на 1 ход") }
            null -> engine.addLog("Пикси улетела в стену и ничего не задела")
        }
    
        }
    }


    private fun resolveBananaPeel(engine: GameEngine, player: Player) {
        engine.withD4 { roll ->
        val direction = Wall.directionByNumber(roll)
        val cell = Cell(player.position.row + direction.dr, player.position.col + direction.dc)
        if (engine.isBoardCell(cell)) {
            if (cell !in engine.bananaPeels) engine.bananaPeels.add(cell)
            engine.addLog("${player.name} положил кожурку от банана на (${cell.row},${cell.col})")
        } else {
            engine.addLog("${player.name} попытался положить кожурку, но клетка за краем поля")
        }
    
        }
    }

    private fun resolveTaran(engine: GameEngine, player: Player) {
        engine.withD4 { roll ->
        val direction = Wall.directionByNumber(roll)
        engine.taranMove(player, direction)
    
        }
    }

    fun canStealFrom(player: Player, target: Player): Boolean =
        target != player && target.alive && target.effects.isNotEmpty()

    private fun resolveThief(engine: GameEngine, player: Player) {
        engine.requestChoosePlayer(
            prompt = "У кого украсть эффект?",
            allowed = { canStealFrom(player, it) }
        ) { target ->
            if (target.effects.size == 1) {
                engine.consumeActiveCard(player, CardId.THIEF)
                engine.stealEffect(player, target, target.effects.first())
            } else {
                engine.requestChooseEffect(target) { effect ->
                    engine.consumeActiveCard(player, CardId.THIEF)
                    engine.stealEffect(player, target, effect)
                }
            }
        }
    }

    private fun manhattan(a: Cell, b: Cell) = Math.abs(a.row - b.row) + Math.abs(a.col - b.col)
}
