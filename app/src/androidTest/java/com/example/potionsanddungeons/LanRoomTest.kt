package com.example.potionsanddungeons

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanRoomTest {
    @Test
    fun hostAndGuestSynchronizeTurnsAndRematchInTheSameRoom() = runBlocking {
        withContext(Dispatchers.Main) {
            val host = LanRoom()
            val guest = LanRoom()
            try {
                host.host("Ведущий")
                awaitState { !host.busy }
                assertTrue(host.status, host.connected)
                guest.join(host.hostAddress, host.roomCode, "Гость")
                awaitState { guest.names.size == 2 }
                host.start()
                awaitState {
                    host.engine?.turnOrder?.size == 2 && guest.engine?.turnOrder?.size == 2
                }
                val hostGame = checkNotNull(host.engine)
                val guestGame = checkNotNull(guest.engine)
                assertEquals(hostGame.turnOrder.map { it.name }, guestGame.turnOrder.map { it.name })
                val active = if (host.canAct) host else guest
                val other = if (active === host) guest else host
                assertTrue(active.canAct)
                assertFalse(other.canAct)
                other.perform(MatchAction.ROLL_INLINE, 0, 0)
                assertEquals(0, hostGame.pendingSteps)
                active.perform(MatchAction.ROLL_INLINE, 0, 0)
                awaitState { hostGame.pendingSteps > 0 && guestGame.pendingSteps > 0 }
                assertEquals(hostGame.lastDiceRoll, guestGame.lastDiceRoll)
                assertEquals(hostGame.pendingSteps, guestGame.pendingSteps)
                val cell = hostGame.turnOrder[hostGame.currentTurnIndex].position.neighbors4().first(hostGame::isBoardTarget)
                val current = hostGame.turnOrder[hostGame.currentTurnIndex].name
                awaitState { active.canAct }
                active.perform(MatchAction.CELL, cell.row, cell.col)
                awaitState {
                    hostGame.players.first { it.name == current }.position == cell &&
                        guestGame.players.first { it.name == current }.position == cell
                }
                val code = host.roomCode
                val guestSeat = guest.seat
                host.rematch()
                assertTrue(host.engine === hostGame)
                hostGame.gameOver = true
                guest.rematch()
                assertTrue(guest.engine === guestGame)
                assertTrue(host.canRematch)
                host.rematch()
                awaitState {
                    host.engine !== hostGame && guest.engine !== guestGame &&
                        host.engine?.turnOrder?.size == 2 && guest.engine?.turnOrder?.size == 2
                }
                val nextHostGame = checkNotNull(host.engine)
                val nextGuestGame = checkNotNull(guest.engine)
                assertEquals(code, host.roomCode)
                assertEquals(guestSeat, guest.seat)
                assertEquals(host.names, guest.names)
                assertFalse(nextHostGame.gameOver)
                assertFalse(nextGuestGame.gameOver)
                assertEquals(nextHostGame.turnOrder.map { it.name }, nextGuestGame.turnOrder.map { it.name })
                val nextActive = if (host.canAct) host else guest
                nextActive.perform(MatchAction.ROLL_INLINE, 0, 0)
                awaitState { nextHostGame.pendingSteps > 0 && nextGuestGame.pendingSteps > 0 }
                assertEquals(nextHostGame.lastDiceRoll, nextGuestGame.lastDiceRoll)
                guest.close()
                awaitState { host.paused }
                assertFalse(host.canAct)
            } finally {
                guest.close()
                host.close()
            }
        }
    }

    private suspend fun awaitState(ready: () -> Boolean) {
        withTimeout(15000) {
            while (!ready()) delay(30)
        }
    }
}
