package com.example.potionsanddungeons

interface EffectHandler {
    val id: CardId
    val category: EffectCategory
    fun onPickup(engine: GameEngine, holder: Player) {}
    fun checkPendingTrigger(engine: GameEngine, holder: Player): Boolean = false
}

object EffectRegistry {
    private val handlers = mutableMapOf<CardId, EffectHandler>()

    init { registerAllEffects() }

    fun register(h: EffectHandler) { handlers[h.id] = h }
    fun get(id: CardId): EffectHandler? = handlers[id]
}

// Простой шаблон для TEMPORARY-эффектов — особая логика читается в движке
// через player.effects.has(CardId.XXX) (так работают Зловоние, Оборотень и др.)
private class SimpleTemporaryEffect(override val id: CardId) : EffectHandler {
    override val category = EffectCategory.TEMPORARY
    override fun onPickup(engine: GameEngine, holder: Player) {
        holder.effects.add(ActiveEffect(id, category, turnsLeft = 3, justAcquired = engine.isCurrentPlayer(holder)))
    }
}

private class SimplePermanentEffect(override val id: CardId) : EffectHandler {
    override val category = EffectCategory.PERMANENT
    override fun onPickup(engine: GameEngine, holder: Player) {
        holder.effects.add(ActiveEffect(id, category))
    }
}

private fun registerAllEffects() {
    EffectRegistry.register(SimplePermanentEffect(CardId.SPEED))
    EffectRegistry.register(SimplePermanentEffect(CardId.SLOWDOWN))
    EffectRegistry.register(SimpleTemporaryEffect(CardId.INVISIBILITY))
    // Зловоние: держатель становится приоритетной целью босса — читается в GameEngine.nearestPlayerTo
    EffectRegistry.register(SimpleTemporaryEffect(CardId.STENCH))
    // Оборотень: если носитель в свой ход заходит на клетку другого игрока — тот погибает.
    // Проверка — в GameEngine.movePlayerStep/handleKnightMove через checkWerewolfKill.
    EffectRegistry.register(SimpleTemporaryEffect(CardId.WEREWOLF))

    EffectRegistry.register(SimplePermanentEffect(CardId.SHIELD))
    EffectRegistry.register(SimplePermanentEffect(CardId.IMMUNITY))

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BOSS_SPEED_UP
        override val category = EffectCategory.PERMANENT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.bossSpeedBonus++
            engine.addLog("Босс стал быстрее (+1 доп. шаг в конце раунда)")
        }
    })
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BOSS_ATTACK_UP
        override val category = EffectCategory.PERMANENT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.increaseBossAttackRadius()
            engine.addLog("Атака босса усилена (+1 клетка радиуса по прямой)")
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BOSS_DUPLICATION
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.spawnSecondBoss()
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BOSS_BLINDNESS
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.bosses.forEach { it.stunnedTurns = maxOf(it.stunnedTurns, 1) }
            engine.addLog("Босс ослеплён и не сдвинется в конце этого раунда")
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.STUN
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            holder.skipNextTurn = true
            engine.addLog("${holder.name} пропустит свой следующий ход")
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.TIME_REWIND
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.rewindCurrentTurn(holder)
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.REPEL
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.repelBossFrom(holder)
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.HYPNOSIS
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.pullPlayerTowardsBoss(holder)
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BODY_SWAP
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.requestChoosePlayer(
                prompt = "Выбери, с кем поменяться местами",
                allowed = { it != holder && it.alive }
            ) { target ->
                val tmp = holder.position
                if (holder.position != target.position) {
                    holder.position = target.position
                    target.position = tmp
                }
                engine.addLog("${holder.name} поменялся местами с ${target.name}")
            }
        }
    })

    // Свап эффекта — держатель выбирает другого игрока, и они полностью меняются
    // местами ВСЕМИ своими текущими эффектами (временными и постоянными).
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.EFFECT_SWAP
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.requestChoosePlayer(
                prompt = "С кем поменяться всеми эффектами?",
                allowed = { it != holder && it.alive }
            ) { target ->
                engine.swapAllEffects(holder, target)
            }
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BROKEN_SHIELD
        override val category = EffectCategory.PENDING_CONDITIONAL
        override fun onPickup(engine: GameEngine, holder: Player) {
            val shield = holder.effects.get(CardId.SHIELD)
            if (shield != null) {
                holder.effects.remove(shield)
                engine.addLog("${holder.name}: щит сломан сразу")
            } else {
                holder.effects.add(ActiveEffect(id, category, waitingFor = PendingCondition.UNTIL_SHIELD_APPEARS))
                engine.addLog("${holder.name}: «Сломанный щит» ждёт появления щита")
            }
        }
        override fun checkPendingTrigger(engine: GameEngine, holder: Player): Boolean {
            return holder.effects.has(CardId.SHIELD)
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.TELEPORT
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.requestChooseCell(
                prompt = "Выбери клетку для телепортации",
                allowed = { cell -> cell !in engine.doors && engine.bosses.none { it.position == cell } }
            ) { cell ->
                engine.teleportPlayer(holder, cell)
            }
        }
    })


    EffectRegistry.register(SimplePermanentEffect(CardId.DAUN))
    EffectRegistry.register(SimpleTemporaryEffect(CardId.HORSE))
    EffectRegistry.register(SimplePermanentEffect(CardId.DISABILITY))

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.ATTRACTION
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.pullAllPlayersToRandomWall()
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.BFF
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.requestChoosePlayer(
                prompt = "Кого притянуть к себе?",
                allowed = { it != holder && it.alive }
            ) { target ->
                if (target.position != holder.position) {
                    target.position = holder.position
                }
                engine.addLog("${holder.name} притянул ${target.name} к себе")
            }
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.LET_THERE_BE_LIGHT
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.potions.forEach { it.revealed = true }
            engine.addLog("Все зелья на поле раскрыты")
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.CURSE
        override val category = EffectCategory.PENDING_CONDITIONAL
        override fun onPickup(engine: GameEngine, holder: Player) {
            val positive = holder.effects.find { it.id.isPositive() }
            if (positive != null) {
                engine.turnIntoNegative(holder, positive)
            } else {
                holder.effects.add(ActiveEffect(id, category, waitingFor = PendingCondition.UNTIL_POSITIVE_EFFECT_APPEARS))
                engine.addLog("${holder.name}: «Проклятие» ждёт первого позитивного эффекта")
            }
        }
        override fun checkPendingTrigger(engine: GameEngine, holder: Player): Boolean {
            return holder.effects.any { it.id.isPositive() }
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.POTION_BOX
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.spawnPotionsAround(holder.position)
        }
    })

    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.FREEDOM
        override val category = EffectCategory.PERMANENT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.bossDiagonalMovement = true
            engine.addLog("Босс теперь может ходить и по диагонали")
        }
    })

    // Куколд: игрок берёт управление боссом на N ходов, N = бросок д4.
    // Сами ходы делаются позже, по клику игрока на поле — см. GameEngine.startCuckoldControl.
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.CUCKOLD
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.startCuckoldControl(holder)
        }
    })

    // Кукловод: игрок берёт управление другим игроком на N ходов, N = бросок д4.
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.PUPPETEER
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.requestChoosePlayer(
                prompt = "Кем управлять по броску д4?",
                allowed = { it != holder && it.alive }
            ) { target ->
                engine.startPuppeteerControl(holder, target)
            }
        }
    })

    // Рёв альфы: все игроки притягиваются к боссу на одну клетку, строго не по диагонали.
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.ROAR_OF_ALPHA
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.pullAllPlayersTowardsBoss()
        }
    })

    // Хаос-бросок: количество ходов босса за раунд теперь определяется броском д4,
    // направление движения — обычное, к ближайшему игроку.
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.CHAOS_ROLL
        override val category = EffectCategory.PERMANENT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.bossChaosRoll = true
            engine.addLog("Количество ходов босса за раунд теперь определяется броском д4")
        }
    })

    // Очистка: мгновенно снимает все эффекты со всех игроков. Больше не активная карта.
    EffectRegistry.register(object : EffectHandler {
        override val id = CardId.CLEANSE
        override val category = EffectCategory.INSTANT
        override fun onPickup(engine: GameEngine, holder: Player) {
            engine.players.forEach {
                it.effects.clear()
                it.stunnedTurns = 0
                it.skipNextTurn = false
            }
            engine.addLog("${holder.name} применил «Очистку» — эффекты сняты со всех игроков")
        }
    })
}

fun CardId.isPositive(): Boolean = this in setOf(
    CardId.SPEED, CardId.SHIELD, CardId.INVISIBILITY, CardId.IMMUNITY,
    CardId.TELEPORT
)
// Иммунитет блокирует именно эти карты. Оборотень сюда намеренно не входит —
// он не считается негативным эффектом по решению автора игры.
fun CardId.isNegative(): Boolean = this in setOf(
    CardId.SLOWDOWN, CardId.DAUN, CardId.STENCH, CardId.STUN, CardId.DISABILITY, CardId.CURSE, CardId.BROKEN_SHIELD
)
