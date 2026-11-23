package com.example.potionsanddungeons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BoardOptionsEditor(options: BoardOptions, cards: List<CustomCard>, onChange: (BoardOptions) -> Unit) {
    Text("Размер поля", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(BOARD_SIZE, 7).forEach { size ->
            FilterChip(selected = options.size == size, onClick = { onChange(options.copy(size = size)) },
                label = { Text("$size×$size") })
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
    if (cards.isNotEmpty()) {
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
    var effect by rememberSaveable { mutableStateOf<CardId?>(null) }
    var choosingEffect by rememberSaveable { mutableStateOf(false) }
    fun edit(index: Int) {
        val card = cards.getOrNull(index)
        name = card?.name.orEmpty()
        flavor = card?.flavor.orEmpty()
        effect = card?.effect
        editing = index
    }

    if (choosingEffect) {
        CardCatalogScreen(onSelectCard = { effect = it; choosingEffect = false }, pickupOnly = true) { choosingEffect = false }
        return
    }
    val close = { if (editing == null) onBack() else editing = null }
    BackHandler(onBack = close)
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = close) { Text("Назад") }
        val index = editing
        if (index == null) {
            Text("Мои карты", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
            Text("Создай зелье с готовым эффектом и добавь его в колоду в настройках партии.", color = AppColors.textMuted)
            Button(onClick = { edit(-1) }, enabled = cards.size < CustomCard.MAX_CARDS) { Text("Создать карту") }
            if (cards.size == CustomCard.MAX_CARDS) Text("Можно сохранить до ${CustomCard.MAX_CARDS} карт.")
            cards.forEachIndexed { cardIndex, card ->
                OutlinedButton(onClick = { edit(cardIndex) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${card.name} · ${card.effect.displayName()}")
                }
            }
        } else {
            Text(if (index < 0) "Новая карта" else "Редактировать карту", fontSize = AppFonts.title, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it.take(CustomCard.MAX_NAME) }, label = { Text("Название") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(flavor, { flavor = it.take(CustomCard.MAX_FLAVOR) }, label = { Text("Текст карты (необязательно)") },
                modifier = Modifier.fillMaxWidth(), maxLines = 4)
            val selectedEffect = effect
            OutlinedButton(onClick = { choosingEffect = true }) { Text(selectedEffect?.displayName() ?: "Выбрать эффект") }
            if (selectedEffect != null) Text(CARD_DESCRIPTIONS.getValue(selectedEffect))
            val duplicate = cards.withIndex().any { it.index != index && it.value.name.equals(name.trim(), ignoreCase = true) }
            if (duplicate) Text("Карта с таким названием уже есть.")
            Button(onClick = {
                val card = CustomCard(name.trim(), checkNotNull(selectedEffect), flavor.trim())
                onChange(if (index < 0) cards + card else cards.toMutableList().apply { this[index] = card })
                editing = null
            }, enabled = name.isNotBlank() && selectedEffect != null && !duplicate) { Text("Сохранить") }
            if (index >= 0) {
                TextButton(onClick = {
                    onChange(cards.filterIndexed { cardIndex, _ -> cardIndex != index })
                    editing = null
                }) { Text("Удалить карту") }
            }
        }
    }
}
