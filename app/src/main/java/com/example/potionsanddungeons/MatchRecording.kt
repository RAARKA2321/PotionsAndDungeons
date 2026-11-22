package com.example.potionsanddungeons

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class BotStyle {
    POTION_HUNTER,
    CAUTIOUS,
    TACTICAL,
    AGGRESSIVE;

    val considersCardTargets: Boolean
        get() = this == TACTICAL || this == AGGRESSIVE
}

internal enum class MatchAction {
    ROLL, ACCEPT, REROLL, CELL, CARD, PLAYER, EFFECT, POTION, D4, BOSS, BOT, ROLL_INLINE
}

internal class MatchRecording(
    val names: List<String>,
    val bots: Set<Int>,
    val dlc: Boolean,
    private val seed: Int = Random.nextInt(),
    private val version: Int = 6,
    private val legacyStyle: BotStyle? = null,
    val customRules: CustomRules = CustomRules()
) {
    private val actions = JSONArray()
    val engine = GameEngine(dlc, Random(seed), animateD4 = true, offerReroll = true, legacyAttackTurns = version == 1, legacyBossCrossing = version < 4, customRules = customRules).apply {
        setupGame(names, bots)
        val tacticRandom = Random(seed.inv())
        players.filter { it.isBot }.forEach { bot ->
            bot.botStyle = legacyStyle ?: BotStyle.entries.random(tacticRandom)
        }
    }

    fun perform(action: MatchAction, first: Int = 0, second: Int = 0): Int {
        val result = engine.applyAction(action, first, second)
        actions.put(JSONArray().put(action.name).put(first).put(second))
        return result
    }

    fun save(preferences: SharedPreferences) {
        val json = JSONObject()
            .put("version", version)
            .put("names", JSONArray(names))
            .put("bots", JSONArray(bots.sorted()))
            .put("dlc", dlc)
            .put("choicePotions", customRules.choicePotions)
            .put("breakableDoors", customRules.breakableDoors)
            .put("style", legacyStyle?.name)
            .put("seed", seed)
            .put("actions", actions)
        preferences.edit().putString("match", json.toString()).apply()
    }

    companion object {
        fun read(preferences: SharedPreferences): MatchRecording? = runCatching {
            val json = JSONObject(preferences.getString("match", null) ?: return null)
            val version = json.getInt("version")
            require(version in 1..6)
            val names = json.getJSONArray("names")
            val bots = json.getJSONArray("bots")
            val recording = MatchRecording(
                List(names.length(), names::getString),
                List(bots.length(), bots::getInt).toSet(),
                json.getBoolean("dlc"),
                json.getInt("seed"),
                version,
                if (version < 3) BotStyle.valueOf(json.getString("style")) else null,
                CustomRules(json.optBoolean("choicePotions"), json.optBoolean("breakableDoors"))
            )
            val actions = json.getJSONArray("actions")
            repeat(actions.length()) { index ->
                val action = actions.getJSONArray(index)
                recording.perform(MatchAction.valueOf(action.getString(0)), action.getInt(1), action.getInt(2))
            }
            recording
        }.getOrNull()
    }
}

internal fun GameEngine.applyAction(action: MatchAction, first: Int, second: Int): Int {
    when (action) {
        MatchAction.ROLL -> return rollDiceForCurrentPlayer(waitForAcceptance = true)
        MatchAction.ROLL_INLINE -> return rollDiceForCurrentPlayer()
        MatchAction.ACCEPT -> return decideRoll(false)
        MatchAction.REROLL -> return decideRoll(true)
        MatchAction.CELL -> handleCellClick(Cell(first, second))
        MatchAction.CARD -> playActiveCard(players[first], CardId.entries[second])
        MatchAction.PLAYER -> resolvePlayerChoice(players[first])
        MatchAction.EFFECT -> {
            val choice = pendingChoice as? PendingChoice.ChooseEffect
            choice?.from?.effects?.getOrNull(first)?.let(::resolveEffectChoice)
        }
        MatchAction.POTION -> continueAfterPotion(cleanse = first == 1)
        MatchAction.D4 -> finishD4Animation()
        MatchAction.BOSS -> return if (advanceBossStep()) 1 else 0
        MatchAction.BOT -> {
            var needsRoll = false
            playBotAction { needsRoll = true }
            return if (needsRoll) 1 else 0
        }
    }
    return 0
}
