package com.example.potionsanddungeons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LanAvatarTest {
    @Test
    fun thumbnailPixelsSurviveSerializationAndBitmapCreation() {
        val pixels = IntArray(LAN_AVATAR_SIZE * LAN_AVATAR_SIZE) { 0xff356000.toInt() + it }
        val avatar = LanAvatar.fromPixels(pixels)
        val restored = checkNotNull(LanAvatar.parse(avatar.encoded))
        val bitmap = lanAvatarBitmap(restored.pixels())
        val rendered = IntArray(pixels.size)
        bitmap.readPixels(rendered)
        assertArrayEquals(pixels, rendered)
        assertNull(LanAvatar.parse(avatar.encoded.dropLast(1)))
        assertNull(LanAvatar.parse("g" + avatar.encoded.drop(1)))
    }

    @Test
    fun roomKeepsAvatarsDlcAndBoardRulesThroughRematch() = runBlocking {
        withContext(Dispatchers.Main) {
            val host = LanRoom(InetAddress::getByName)
            val guest = LanRoom(InetAddress::getByName)
            val hostAvatar = LanAvatar.fromPixels(IntArray(LAN_AVATAR_SIZE * LAN_AVATAR_SIZE) { 0xff992244.toInt() })
            val guestAvatar = LanAvatar.fromPixels(IntArray(LAN_AVATAR_SIZE * LAN_AVATAR_SIZE) { 0xff228833.toInt() })
            val expected = mapOf(0 to hostAvatar, 1 to guestAvatar)
            val tiles = List(49) { Cell(it / 7, it % 7) }.toSet() - Cell(3, 2)
            val layout = BoardLayout("Проход", 7, tiles, boardCorners(7), boardCenter(7))
            val special = CustomCard("Флакон ветра", listOf(CardId.SPEED, CardId.SHIELD))
            val recipe = DeckRecipe(mapOf(CardId.SLOWDOWN to 0, CardId.SHIELD to 6), mapOf(special.name to 2))
            val board = BoardOptions(7, PotionPlacement.EVERY_CELL, listOf(special), layout, recipe, bossUpgrades = true)
            try {
                host.host("Ведущий", vpnAddress = "127.0.0.1", avatar = hostAvatar)
                awaitState { !host.busy }
                assertTrue(host.status, host.connected)
                guest.join(host.hostAddress, host.roomCode, "Гость", viaVpn = true, avatar = guestAvatar)
                awaitState { guest.names.size == 2 || !guest.busy && !guest.connected }
                assertTrue("${guest.status} [${host.status}]", guest.connected)
                assertEquals(expected, host.avatars)
                assertEquals(expected, guest.avatars)
                assertFalse(host.dlcEnabled)
                assertFalse(guest.dlcEnabled)
                guest.configureDlc(true)
                assertFalse(guest.dlcEnabled)
                val preset = PartyPreset("Сетевая партия", true, CustomRules(board = board))
                val saved = readPartyPresets(encodePartyPresets(listOf(preset))).single()
                host.configureOptions(saved.dlc, saved.rules.board)
                awaitState { guest.dlcEnabled && guest.boardOptions == board }
                host.start()
                awaitState { guest.engine != null }
                val first = checkNotNull(guest.engine)
                assertEquals(guest.names, first.players.map { it.name })
                assertEquals(7, first.boardSize)
                assertTrue(first.bossUpgradesEnabled)
                assertEquals(tiles, first.potions.map { it.position }.toSet())
                assertTrue(first.potions.none { it.cardId == CardId.SLOWDOWN })
                assertEquals(checkNotNull(host.engine).potions.map { it.card }, first.potions.map { it.card })
                assertTrue(first.potions.any { it.source == DeckSource.DLC })
                host.configureDlc(false)
                assertTrue(host.dlcEnabled)
                checkNotNull(host.engine).gameOver = true
                host.rematch()
                awaitState { guest.engine !== first }
                assertEquals(board, guest.boardOptions)
                assertEquals(tiles, checkNotNull(guest.engine).potions.map { it.position }.toSet())
                assertTrue(checkNotNull(guest.engine).potions.any { it.source == DeckSource.DLC })
                assertEquals(expected, guest.avatars)
                assertEquals(expected, host.avatars)
            } finally {
                guest.close()
                host.close()
            }
        }
    }

    private suspend fun awaitState(ready: () -> Boolean) {
        withTimeout(10000) {
            while (!ready()) delay(25)
        }
    }
}
