package com.example.potionsanddungeons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

internal data class PartyPreset(val name: String, val dlc: Boolean, val rules: CustomRules) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME)
    }

    val canUseInNetwork: Boolean get() = rules == CustomRules(board = rules.board)

    companion object {
        const val MAX_NAME = 40
    }
}

internal class PartyPresetLibrary(private val preferences: GamePreferences) {
    var presets by mutableStateOf(readPartyPresets(preferences.values()["presets"]))
        private set

    fun save(preset: PartyPreset) {
        require(presets.none { it.name.equals(preset.name, ignoreCase = true) })
        val updated = presets + preset
        preferences.put(mapOf("presets" to encodePartyPresets(updated)))
        presets = updated
    }
}

internal fun encodePartyPresets(presets: List<PartyPreset>): String = JSONArray().also { array ->
    presets.forEach { preset ->
        val rules = preset.rules
        array.put(JSONObject()
            .put("name", preset.name)
            .put("dlc", preset.dlc)
            .put("board", rules.board.toJson())
            .put("choicePotions", rules.choicePotions)
            .put("breakableDoors", rules.breakableDoors)
            .put("cardTrading", rules.cardTrading)
            .put("bossDuel", rules.bossDuel)
            .put("daily", rules.daily))
    }
}.toString()

internal fun readPartyPresets(saved: String?): List<PartyPreset> {
    val array = saved?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        runCatching {
            val value = array.getJSONObject(index)
            PartyPreset(value.getString("name"), value.getBoolean("dlc"), CustomRules(
                choicePotions = value.optBoolean("choicePotions"),
                breakableDoors = value.optBoolean("breakableDoors"),
                cardTrading = value.optBoolean("cardTrading"),
                bossDuel = value.optBoolean("bossDuel"),
                daily = value.optBoolean("daily"),
                board = readBoardOptions(value.getJSONObject("board"))
            ))
        }.getOrNull()
    }.distinctBy { it.name.lowercase() }
}
