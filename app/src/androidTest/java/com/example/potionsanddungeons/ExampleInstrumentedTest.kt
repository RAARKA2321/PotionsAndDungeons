package com.example.potionsanddungeons

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun savedMatchRestoresAnUnconfirmedRoll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("test_match_replay", 0)
        val match = MatchRecording(listOf("A", "B"), emptySet(), true, seed = 42)
        while (match.engine.d4Rolls.isNotEmpty()) match.perform(MatchAction.D4)
        val rolled = match.perform(MatchAction.ROLL)
        match.save(preferences)

        val restored = checkNotNull(MatchRecording.read(preferences))
        assertTrue(restored.engine.awaitingRollDecision)
        assertEquals(rolled, restored.engine.lastDiceRoll)
        assertEquals(match.engine.turnOrder.map { it.name }, restored.engine.turnOrder.map { it.name })
        assertEquals(match.engine.potions.map { it.cardId }, restored.engine.potions.map { it.cardId })
        assertEquals(match.perform(MatchAction.REROLL), restored.perform(MatchAction.REROLL))
    }
    @Test
    fun savedMatchKeepsEachRandomBotStyle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("test_bot_styles", 0)
        val match = MatchRecording(listOf("A", "B", "C", "D"), setOf(1, 2, 3), true, seed = 42)
        match.save(preferences)

        val restored = checkNotNull(MatchRecording.read(preferences))
        val styles = match.engine.players.filter { it.isBot }.map { it.botStyle }
        assertEquals(3, styles.size)
        assertEquals(styles, restored.engine.players.filter { it.isBot }.map { it.botStyle })
        val observedStyles = (0..15).flatMap { seed ->
            MatchRecording(match.names, match.bots, true, seed = seed).engine.players
                .filter { it.isBot }.map { it.botStyle }
        }.toSet()
        assertEquals(BotStyle.entries.toSet(), observedStyles)
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.potionsanddungeons", appContext.packageName)
    }
}

@RunWith(AndroidJUnit4::class)
class GameScreenTest {
    @Test
    fun tutorialLetsPlayerRollAndMoveAfterAttackingBoss() {
        ActivityScenario.launch(MainActivity::class.java).use {
            click("Обучение")
            click("Бросить кубик")
            click("Ряд 1, столбец 2", cell = true)
            click("Продолжить")
            click("Карты в руке (1)")
            click("Атака")
            click("Сыграть карту")
            click("Ряд 2, столбец 2", cell = true)
            click("Бросить кубик")
            click("Ряд 1, столбец 3", cell = true)
            findText("Обучение пройдено.")
        }
    }

    @Test
    fun fourPlayerGameSurvivesRecreationAndReturningFromSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            click("Как играть")
            findText("Цель — остаться последним игроком на поле.")
            click("Понятно")
            click("Новая игра")
            click("4")
            click("Далее")
            click("Начать игру")
            findText("Бросить кубик")
            click("Зелья")
            findText("Справочник зелий")
            click("DLC")
            click("Назад")
            click("Бросить кубик")
            findText("Перебросить кубик")
            val steps = findText("Нажми на клетку с бирюзовым контуром.", exact = false).text.toString()

            scenario.recreate()
            findText(steps)
            click("Меню")
            click("Главное меню")
            findText("Potions and Dungeons")
            click("Продолжить партию")
            findText(steps)
            click("Журнал")
            val order = findText("Порядок ходов:", exact = false).text.toString()
            assertEquals(3, order.windowed(2).count { it == "->" })
        }
    }

    private fun click(label: String, cell: Boolean = false) {
        var node: AccessibilityNodeInfo? = findText(label, exact = !cell, description = cell)
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
        }
        error("Could not click: $label")
    }

    private fun findText(label: String, exact: Boolean = true, description: Boolean = false): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val timeout = SystemClock.uptimeMillis() + 30_000
        do {
            val root = automation.rootInActiveWindow
            val node = root?.let {
                findNode(it) { candidate ->
                    val value = if (description) candidate.contentDescription else candidate.text
                    val nodeText = value?.toString() ?: return@findNode false
                    candidate.isVisibleToUser && (!description || candidate.isEnabled && candidate.isClickable) && if (exact) nodeText == label else nodeText.contains(label)
                }
            }
            if (node != null) return node
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < timeout)
        val seen = mutableListOf<String>()
        automation.rootInActiveWindow?.let { root ->
            findNode(root) {
                if (it.isVisibleToUser) it.text?.let { text -> seen.add(text.toString()) }
                false
            }
        }
        error("Visible text not found: $label. Visible labels: $seen")
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (matches(root)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findNode(child, matches)
            if (match != null) return match
        }
        return null
    }
}
