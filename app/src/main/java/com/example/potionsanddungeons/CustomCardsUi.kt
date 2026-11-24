package com.example.potionsanddungeons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BoardRulesSummary(
    options: BoardOptions, dlc: Boolean, extra: List<String> = emptyList(),
    choices: Boolean = false, duel: Boolean = false
) {
    val items = buildList {
        options.layout?.let { add(it.name) }
        add("${options.size}×${options.size}")
        if (options.potionPlacement != PotionPlacement.STANDARD) add(options.potionPlacement.label)
        if (options.bossUpgrades) add("усиление босса")
        if (options.deckRecipe == null) {
            add(if (dlc) "DLC" else "Базовая колода")
            if (options.customCards.isNotEmpty()) add("своих карт: ${options.customCards.size}")
        } else {
            val contents = deckContents(dlc, choices, duel, options.customCards, options.deckRecipe)
            add("основная: ${contents.baseCount}")
            if (dlc) add("DLC: ${contents.dlcCount}")
            val customCount = contents.custom.sumOf { it.second }
            if (customCount > 0) add("в том числе своих: $customCount")
        }
        addAll(extra)
    }
    Text(items.joinToString(" · "), color = AppColors.textMuted, fontSize = AppFonts.legend)
}

@Composable
internal fun BoardOptionsEditor(
    options: BoardOptions, cards: List<CustomCard>, layouts: List<BoardLayout>, onChange: (BoardOptions) -> Unit
) {
    Text("Размер поля", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(BOARD_SIZE, 7).forEach { size ->
            FilterChip(selected = options.size == size && options.layout == null,
                onClick = { onChange(options.copy(size = size, layout = null)) },
                label = { Text("$size×$size") })
        }
    }
    val availableLayouts = (layouts + listOfNotNull(options.layout)).distinct()
    if (availableLayouts.isNotEmpty()) {
        Text("Мои поля", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availableLayouts.forEach { layout ->
                FilterChip(selected = options.layout == layout,
                    onClick = { onChange(options.copy(size = layout.size, layout = layout)) },
                    label = { Text(if (layout in layouts) layout.name else "${layout.name} (из пресета)") })
            }
        }
    }
    Text("Зелья на поле", fontWeight = FontWeight.Bold)
    PotionPlacement.entries.forEach { placement ->
        FilterChip(
            selected = options.potionPlacement == placement,
            onClick = { onChange(options.copy(potionPlacement = placement)) },
            label = { Text(placement.label) }
        )
    }
    when (options.potionPlacement) {
        PotionPlacement.STANDARD -> Unit
        PotionPlacement.RANDOM_REPLACEMENT -> Text("Подобранное зелье заменяется на другой свободной клетке, если такая есть.")
        PotionPlacement.EVERY_CELL -> Text("Закончи движение на любой клетке, чтобы подобрать зелье.")
    }
    CustomRuleSwitch("Усиление босса", options.bossUpgrades) { onChange(options.copy(bossUpgrades = it)) }
    if (options.bossUpgrades) {
        Text("С ${BossUpgrade.SPEED.completedRounds + 1}-го хода босса: +1 шаг. " +
            "С ${BossUpgrade.ATTACK.completedRounds + 1}-го: +1 к радиусу атаки.",
            color = AppColors.textMuted, fontSize = AppFonts.hint)
    }
    if (cards.isNotEmpty() && options.deckRecipe == null) {
        CustomRuleSwitch("Добавить мои карты (${cards.size})", options.customCards.isNotEmpty()) {
            onChange(options.copy(customCards = if (it) cards else emptyList()))
        }
    }
}

@Composable
internal fun CustomCardsScreen(cards: List<CustomCard>, onChange: (List<CustomCard>) -> Unit, onBack: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var flavor by rememberSaveable { mutableStateOf("") }
    var effects by rememberSaveable(stateSaver = listSaver<List<CardId>, String>(
        save = { effects -> effects.map { it.name } },
        restore = { names -> names.map { CardId.valueOf(it) } }
    )) { mutableStateOf(emptyList()) }
    var choosingEffect by rememberSaveable { mutableStateOf(false) }
    var testEngine by remember { mutableStateOf<GameEngine?>(null) }
    fun edit(index: Int) {
        val card = cards.getOrNull(index)
        name = card?.name.orEmpty()
        flavor = card?.flavor.orEmpty()
        effects = card?.effects.orEmpty()
        editing = index
    }
    fun startTest() {
        val draft = CustomCard(name.trim().ifEmpty { "Пробное зелье" }, effects, flavor.trim())
        testEngine = GameEngine(true, animateD4 = true).apply {
            setupCardSandbox()
            testCard(draft.effect, draft)
        }
    }
    testEngine?.let { engine ->
        val back = { testEngine = null }
        BackHandler(onBack = back)
        key(engine) {
            GameScreen(engine, onRestart = ::startTest, onOpenSettings = back, cardSandbox = true, playtest = true)
        }
        return
    }

    if (choosingEffect) {
        CardCatalogScreen(onSelectCard = {
            if (effects.size < CustomCard.MAX_EFFECTS) effects = effects + it
            choosingEffect = false
        }, pickupOnly = true) { choosingEffect = false }
        return
    }
    val close = { if (editing == null) onBack() else editing = null }
    BackHandler(onBack = close)
    val index = editing
    val duplicate = cards.withIndex().any { it.index != index && it.value.name.equals(name.trim(), ignoreCase = true) }
    val title = when {
        index == null -> "Мои карты"
        index < 0 -> "Новая карта"
        else -> "Редактировать карту"
    }
    MenuPage(title, close, footer = {
        if (index == null) {
            Button(onClick = { edit(-1) }, enabled = cards.size < CustomCard.MAX_CARDS, modifier = Modifier.fillMaxWidth()) {
                Text("Создать карту")
            }
        } else {
            OutlinedButton(onClick = ::startTest, enabled = effects.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Проверить на полигоне")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (index >= 0) TextButton(onClick = {
                    onChange(cards.filterIndexed { cardIndex, _ -> cardIndex != index })
                    editing = null
                }) { Text("Удалить") }
                Button(onClick = {
                    val card = CustomCard(name.trim(), effects, flavor.trim())
                    onChange(if (index < 0) cards + card else cards.toMutableList().apply { this[index] = card })
                    editing = null
                }, enabled = name.isNotBlank() && effects.isNotEmpty() && !duplicate, modifier = Modifier.weight(1f)) {
                    Text("Сохранить")
                }
            }
        }
    }) {
        if (index == null) {
            if (cards.isEmpty()) MenuPanel {
                Text("Создай зелье из готовых эффектов. Добавить свои зелья в колоду можно в настройках партии.", color = AppColors.textMuted)
            }
            if (cards.size == CustomCard.MAX_CARDS) Text("Можно сохранить до ${CustomCard.MAX_CARDS} карт.")
            cards.forEachIndexed { cardIndex, card ->
                MenuPanel { MenuLink(card.name, onClick = { edit(cardIndex) },
                    detail = card.effects.joinToString(" → ") { it.displayName() }) }
            }
        } else {
            MenuPanel("Карточка") {
                OutlinedTextField(name, { name = it.take(CustomCard.MAX_NAME) }, label = { Text("Название") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(flavor, { flavor = it.take(CustomCard.MAX_FLAVOR) }, label = { Text("Текст карты (необязательно)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4)
                if (duplicate) Text("Карта с таким названием уже есть.", color = AppColors.accent)
            }
            MenuPanel("Эффекты при подборе") {
                effects.forEachIndexed { position, effect ->
                    Column {
                        Text("${position + 1}. ${effect.displayName()}", fontWeight = FontWeight.Bold)
                        Text(CARD_DESCRIPTIONS.getValue(effect), color = AppColors.textMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = position > 0, onClick = {
                                effects = effects.toMutableList().apply { add(position - 1, removeAt(position)) }
                            }, modifier = Modifier.semantics { contentDescription = "Переместить эффект вверх" }) { Text("↑") }
                            TextButton(enabled = position < effects.lastIndex, onClick = {
                                effects = effects.toMutableList().apply { add(position + 1, removeAt(position)) }
                            }, modifier = Modifier.semantics { contentDescription = "Переместить эффект вниз" }) { Text("↓") }
                            TextButton(onClick = { effects = effects.toMutableList().apply { removeAt(position) } }) {
                                Text("Убрать эффект")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { choosingEffect = true }, enabled = effects.size < CustomCard.MAX_EFFECTS,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (effects.size == CustomCard.MAX_EFFECTS) "Достигнут предел эффектов" else "Добавить эффект")
                }
            }
        }
    }
}
