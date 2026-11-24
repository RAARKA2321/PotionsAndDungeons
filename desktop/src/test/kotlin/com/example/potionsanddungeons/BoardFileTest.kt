package com.example.potionsanddungeons

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoardFileTest {
    private val layout = BoardLayout(
        "Тропа", 5, List(25) { Cell(it / 5, it % 5) }.toSet() - Cell(1, 1),
        boardCorners(5), Cell(2, 2), potions = setOf(Cell(2, 1)), doors = setOf(Cell(2, 3))
    )

    @Test
    fun fileRoundTripKeepsAllBoardData() {
        val encoded = layout.encodeFile()
        assertEquals(layout, encoded.byteInputStream().readBoardFile())
        assertEquals(layout, ("\uFEFF" + encoded).byteInputStream().readBoardFile())
    }

    @Test
    fun rejectsUnsupportedVersionsAndInvalidLayouts() {
        val encoded = JSONObject(layout.encodeFile())
        val futureVersion = encoded.put("version", 999).toString()
        assertThrows(IllegalArgumentException::class.java) { futureVersion.byteInputStream().readBoardFile() }

        encoded.put("version", 1).getJSONObject("board").put("bossStart", 6)
        assertThrows(IllegalArgumentException::class.java) { encoded.toString().byteInputStream().readBoardFile() }
        assertThrows(IllegalArgumentException::class.java) { ByteArray(17 * 1024).inputStream().readBoardFile() }
    }

    @Test
    fun importUsesNextAvailableNameWithoutReplacingExistingBoard() {
        val taken = listOf(layout.copy(name = "тропа"), layout.copy(name = "Тропа (2)"))
        assertEquals(layout.copy(name = "Тропа (3)"), layout.withAvailableName(taken))
    }
}
