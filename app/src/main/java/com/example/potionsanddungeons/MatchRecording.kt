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
    ROLL, ACCEPT, REROLL, CELL, CARD, PLAYER, EFFECT, POTION, D4, BOSS, BOT, ROLL_INLINE, TRADE
}

internal class MatchRecording(
    val names: List<String>,
    val bots: Set<Int>,
    val dlc: Boolean,
    private val seed: Int = Random.nextInt(),
    private val version: Int = 7,
    private val legacyStyle: BotStyle? = null,
    val customRules: CustomRules = CustomRules(),
    val dailyDate: String? = null
) {
    private val actions = JSONArray()
    private val matchRandom = Random(seed)
    val engine = GameEngine(dlc, matchRandom, animateD4 = true, offerReroll = true, legacyAttackTurns = version == 1, legacyBossCrossing = version < 4, customRules = customRules,
        movementRandom = if (dailyDate != null) Random(seed xor 0x345217AC) else matchRandom).apply {
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

    fun snapshot(): JSONObject = JSONObject()
            .put("version", version)
            .put("names", JSONArray(names))
            .put("bots", JSONArray(bots.sorted()))
            .put("dlc", dlc)
            .put("choicePotions", customRules.choicePotions)
            .put("breakableDoors", customRules.breakableDoors)
            .put("cardTrading", customRules.cardTrading)
            .put("bossDuel", customRules.bossDuel)
            .put("dailyDate", dailyDate)
            .put("style", legacyStyle?.name)
            .put("seed", seed)
            .put("actions", actions)
    fun save(preferences: SharedPreferences) {
        preferences.edit().putString("match", snapshot().toString()).apply()
    }

    companion object {
        fun read(preferences: SharedPreferences): MatchRecording? =
            preferences.getString("match", null)?.let { runCatching { restore(JSONObject(it)) }.getOrNull() }

        fun restore(json: JSONObject): MatchRecording {
            val version = json.getInt("version")
            require(version in 1..7)
            val names = json.getJSONArray("names")
            val bots = json.getJSONArray("bots")
            val date = json.optString("dailyDate").ifEmpty { null }
            val recording = MatchRecording(
                List(names.length(), names::getString),
                List(bots.length(), bots::getInt).toSet(),
                json.getBoolean("dlc"),
                json.getInt("seed"),
                version,
                if (version < 3) BotStyle.valueOf(json.getString("style")) else null,
                CustomRules(
                    choicePotions = json.optBoolean("choicePotions"),
                    breakableDoors = json.optBoolean("breakableDoors"),
                    cardTrading = json.optBoolean("cardTrading"),
                    bossDuel = json.optBoolean("bossDuel"),
                    daily = date != null
                ),
                date
            )
            val actions = json.getJSONArray("actions")
            repeat(actions.length()) { index ->
                val action = actions.getJSONArray(index)
                recording.perform(MatchAction.valueOf(action.getString(0)), action.getInt(1), action.getInt(2))
            }
            return recording
        }
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
        MatchAction.TRADE -> {
            if (first == -1) {
                finishTrade(null, null, null)
            } else {
                val partner = players.getOrNull(first) ?: return 0
                val cards = CardId.entries
                finishTrade(partner, cards.getOrNull(second ushr 16), cards.getOrNull(second and 0xffff))
            }
        }
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
