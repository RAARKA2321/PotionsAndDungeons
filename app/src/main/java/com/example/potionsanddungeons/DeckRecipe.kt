package com.example.potionsanddungeons

data class DeckRecipe(
    val stockOverrides: Map<CardId, Int> = emptyMap(),
    val customCopies: Map<String, Int> = emptyMap()
) {
    init {
        require(stockOverrides.values.all { it in 0..MAX_COPIES })
        require(customCopies.size <= CustomCard.MAX_CARDS)
        require(customCopies.all { (name, count) ->
            name.isNotBlank() && name.length <= CustomCard.MAX_NAME && count in 1..MAX_COPIES
        })
    }

    companion object {
        const val MAX_COPIES = 20
    }
}

internal val choiceDeckCards = listOf(CardId.CHOICE_SHIELD, CardId.CHOICE_SPEED)
internal val duelExcludedCards = setOf(
    CardId.BODY_SWAP, CardId.EFFECT_SWAP, CardId.BFF, CardId.PUPPETEER, CardId.THIEF, CardId.WEREWOLF
)

internal fun stockDeckCards(dlc: Boolean, choices: Boolean, duel: Boolean): Map<CardId, Int> = buildMap {
    CARD_QUANTITIES.forEach { (card, count) ->
        if ((dlc || !card.isDlc) && (!duel || card !in duelExcludedCards)) put(card, count)
    }
    if (choices) choiceDeckCards.forEach { put(it, 2) }
}

internal fun CustomCard.isAllowedInDeck(duel: Boolean): Boolean =
    !duel || effects.none { it in duelExcludedCards }

internal class DeckContents(val stock: Map<CardId, Int>, val custom: List<Pair<CustomCard, Int>>) {
    val baseCount = stock.entries.filterNot { it.key.isDlc }.sumOf { it.value } + custom.sumOf { it.second }
    val dlcCount = stock.entries.filter { it.key.isDlc }.sumOf { it.value }
}

internal fun deckContents(
    dlc: Boolean, choices: Boolean, duel: Boolean, customCards: List<CustomCard>, recipe: DeckRecipe?
): DeckContents = DeckContents(
    stockDeckCards(dlc, choices, duel).mapValues { (card, count) -> recipe?.stockOverrides?.get(card) ?: count },
    customCards.filter { it.isAllowedInDeck(duel) }.map { it to (recipe?.customCopies?.get(it.name) ?: 1) }
)

internal fun BoardOptions.deckProblem(dlc: Boolean, choices: Boolean = false, duel: Boolean = false): String? {
    val recipe = deckRecipe ?: return null
    val contents = deckContents(dlc, choices, duel, customCards, recipe)
    val total = contents.baseCount + contents.dlcCount
    val tiles = layout?.tiles?.size ?: size * size
    return when {
        contents.baseCount == 0 -> "Добавь хотя бы одну карту в основную колоду."
        dlc && contents.dlcCount == 0 -> "Добавь хотя бы одну карту DLC или отключи DLC."
        potionPlacement == PotionPlacement.EVERY_CELL && total < tiles ->
            "Для зелий на каждой клетке нужно карт: $tiles. Сейчас в колодах: $total."
        else -> null
    }
}
