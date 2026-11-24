package com.example.potionsanddungeons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyPresetTest {
    @Test
    fun startsEmptyAndRestoresSavedMapsDecksAndLocalRules() {
        val values = mutableMapOf<String, String>()
        val preferences = object : GamePreferences {
            override fun values(): Map<String, String> = values.toMap()
            override fun put(changes: Map<String, Any?>) {
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value.toString() }
            }
        }
        val library = PartyPresetLibrary(preferences)
        assertTrue(library.presets.isEmpty())

        val tiles = List(25) { Cell(it / 5, it % 5) }.toSet() - Cell(1, 1)
        val layout = BoardLayout("Поле", 5, tiles, boardCorners(5), boardCenter(5))
        val card = CustomCard("Зелье", listOf(CardId.SPEED, CardId.SHIELD))
        val recipe = DeckRecipe(mapOf(CardId.STUN to 0), mapOf(card.name to 2))
        val board = BoardOptions(5, PotionPlacement.RANDOM_REPLACEMENT, listOf(card), layout, recipe, bossUpgrades = true)
        val shared = PartyPreset("С друзьями", true, CustomRules(board = board))
        val local = PartyPreset("Дуэль", false, CustomRules(bossDuel = true, daily = true, board = board))
        library.save(shared)
        library.save(local)

        val restored = PartyPresetLibrary(preferences).presets
        assertEquals(listOf(shared, local), restored)
        assertTrue(restored.first().canUseInNetwork)
        assertFalse(restored.last().canUseInNetwork)
        assertThrows(IllegalArgumentException::class.java) { library.save(shared.copy(name = "с друзьями")) }
        assertEquals(listOf(shared, local), PartyPresetLibrary(preferences).presets)
    }
}
