package com.example.potionsanddungeons

import org.json.JSONArray
import org.json.JSONObject

internal fun BoardOptions.toJson(): JSONObject = JSONObject()
    .put("size", size)
    .put("potionPlacement", potionPlacement.name)
    .put("customCards", customCards.toJson())
    .put("layout", layout?.toJson())
    .put("deck", deckRecipe?.toJson())
    .put("bossUpgrades", bossUpgrades)

internal fun readBoardOptions(json: JSONObject?): BoardOptions {
    if (json == null) return BoardOptions()
    return BoardOptions(
        size = json.getInt("size"),
        potionPlacement = PotionPlacement.valueOf(json.getString("potionPlacement")),
        customCards = readCustomCards(json.getJSONArray("customCards")),
        layout = json.optJSONObject("layout")?.let(::readBoardLayout),
        deckRecipe = json.optJSONObject("deck")?.let(::readDeckRecipe),
        bossUpgrades = json.optBoolean("bossUpgrades")
    )
}

internal fun DeckRecipe.toJson() = JSONObject()
    .put("stock", JSONObject().also { json -> stockOverrides.forEach { (card, count) -> json.put(card.name, count) } })
    .put("custom", JSONObject().also { json -> customCopies.forEach { (name, count) -> json.put(name, count) } })

internal fun readDeckRecipe(json: JSONObject): DeckRecipe {
    val stock = json.getJSONObject("stock")
    val custom = json.getJSONObject("custom")
    require(stock.length() <= CardId.entries.size && custom.length() <= CustomCard.MAX_CARDS)
    return DeckRecipe(
        stock.keys().asSequence().associate { CardId.valueOf(it) to stock.getInt(it) },
        custom.keys().asSequence().associateWith { custom.getInt(it) }
    )
}

internal fun BoardLayout.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("size", size)
    .put("tiles", JSONArray(tiles.map { it.row * size + it.col }.sorted()))
    .put("starts", JSONArray(starts.map { it.row * size + it.col }))
    .put("bossStart", bossStart.row * size + bossStart.col)
    .put("potions", JSONArray(potions.map { it.row * size + it.col }.sorted()))
    .put("doors", JSONArray(doors.map { it.row * size + it.col }.sorted()))

internal fun readBoardLayout(json: JSONObject): BoardLayout {
    val size = json.getInt("size")
    require(size == BOARD_SIZE || size == 7)
    fun decode(index: Int): Cell {
        require(index in 0 until size * size)
        return Cell(index / size, index % size)
    }
    fun cells(key: String): List<Cell> {
        val array = json.getJSONArray(key)
        require(array.length() <= size * size)
        return List(array.length()) { decode(array.getInt(it)) }
    }
    return BoardLayout(json.getString("name"), size, cells("tiles").toSet(), cells("starts"),
        decode(json.getInt("bossStart")), cells("potions").toSet(), cells("doors").toSet())
}

internal fun List<CustomCard>.toJson(): JSONArray = JSONArray().also { cards ->
    forEach { card ->
        cards.put(JSONObject()
            .put("name", card.name)
            .put("effects", JSONArray(card.effects.map { it.name }))
            .put("flavor", card.flavor))
    }
}

internal fun readCustomCards(json: JSONArray): List<CustomCard> {
    require(json.length() <= CustomCard.MAX_CARDS)
    return List(json.length()) { index ->
        val card = json.getJSONObject(index)
        val effects = card.optJSONArray("effects")?.let { stored ->
            require(stored.length() in 1..CustomCard.MAX_EFFECTS)
            List(stored.length()) { CardId.valueOf(stored.getString(it)) }
        } ?: listOf(CardId.valueOf(card.getString("effect")))
        CustomCard(card.getString("name"), effects, card.getString("flavor"))
    }
}
