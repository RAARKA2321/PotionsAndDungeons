package com.example.potionsanddungeons

internal class PlayerRecap {
    val potions = linkedMapOf<String, Int>()
    val playedCards = linkedMapOf<CardId, Int>()
    var lastPotion: String? = null
    var lastTurnCell: Cell? = null
}

internal sealed interface EliminationCause {
    data class Boss(val name: String, val from: Cell, val attackFrom: Cell, val radius: Int) : EliminationCause
    data class Werewolf(val name: String) : EliminationCause
}

internal data class Elimination(
    val player: Player, val cell: Cell, val cause: EliminationCause,
    val lastTurnCell: Cell?, val lastPotion: String?
)
