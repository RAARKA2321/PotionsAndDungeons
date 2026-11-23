package com.example.potionsanddungeons

import org.json.JSONArray
import org.json.JSONObject

internal fun BoardOptions.toJson(): JSONObject = JSONObject()
    .put("size", size)
    .put("potionPlacement", potionPlacement.name)
    .put("customCards", customCards.toJson())

internal fun readBoardOptions(json: JSONObject?): BoardOptions {
    if (json == null) return BoardOptions()
    return BoardOptions(
        size = json.getInt("size"),
        potionPlacement = PotionPlacement.valueOf(json.getString("potionPlacement")),
        customCards = readCustomCards(json.getJSONArray("customCards"))
    )
}

internal fun List<CustomCard>.toJson(): JSONArray = JSONArray().also { cards ->
    forEach { card ->
        cards.put(JSONObject()
            .put("name", card.name)
            .put("effect", card.effect.name)
            .put("flavor", card.flavor))
    }
}

internal fun readCustomCards(json: JSONArray): List<CustomCard> {
    require(json.length() <= CustomCard.MAX_CARDS)
    return List(json.length()) { index ->
        val card = json.getJSONObject(index)
        CustomCard(card.getString("name"), CardId.valueOf(card.getString("effect")), card.getString("flavor"))
    }
}
