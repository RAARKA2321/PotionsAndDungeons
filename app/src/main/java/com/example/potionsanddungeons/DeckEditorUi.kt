package com.example.potionsanddungeons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private enum class DeckTab(val title: String) {
    BASE("Основная"), DLC("DLC"), CUSTOM("Мои карты")
}

@Composable
internal fun DeckEditorScreen(
    board: BoardOptions, library: List<CustomCard>, dlc: Boolean, choices: Boolean = false, duel: Boolean = false,
    onApply: (BoardOptions) -> Unit, onBack: () -> Unit
) {
    val recipeSaver = Saver<DeckRecipe, String>(
        save = { it.toJson().toString() }, restore = { readDeckRecipe(JSONObject(it)) }
    )
    var recipe by rememberSaveable(stateSaver = recipeSaver) {
        mutableStateOf(board.deckRecipe ?: DeckRecipe(customCopies = board.customCards.associate { it.name to 1 }))
    }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(DeckTab.BASE) }
    val allCustom = (board.customCards + library).distinctBy { it.name }
    val availableCustom = allCustom.filter { it.isAllowedInDeck(duel) }
    val selected = allCustom.filter { it.name in recipe.customCopies }
    val draft = board.copy(customCards = selected, deckRecipe = recipe)
    val problem = draft.deckProblem(dlc, choices, duel)
    val stock = stockDeckCards(dlc, choices, duel)

    BackHandler(onBack = onBack)
    MenuPage("Колода партии", onBack, scroll = false, footer = {
        BoardRulesSummary(draft, dlc, choices = choices, duel = duel)
        if (problem != null) Text(problem, color = AppColors.accent, fontSize = AppFonts.hint)
        Button(onClick = { onApply(draft) }, enabled = problem == null, modifier = Modifier.fillMaxWidth()) {
            Text("Применить колоду")
        }
    }) {
        OutlinedTextField(query, { query = it }, label = { Text("Найти карту") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckTab.entries.filter { it != DeckTab.DLC || dlc }.forEach { item ->
                FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.title) })
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val search = query.trim()
            if (tab == DeckTab.CUSTOM) {
                val cards = availableCustom.filter { it.name.contains(search, ignoreCase = true) }
                if (cards.isEmpty()) item {
                    Text(if (availableCustom.isEmpty()) "Нет своих карт для этого режима." else "Карты не найдены.")
                }
                items(cards) { card ->
                    DeckCountRow(card.name, recipe.customCopies[card.name] ?: 0) { count ->
                        val copies = if (count == 0) recipe.customCopies - card.name else recipe.customCopies + (card.name to count)
                        recipe = recipe.copy(customCopies = copies)
                    }
                }
            } else {
                val cards = stock.entries.filter { (card, _) ->
                    card.isDlc == (tab == DeckTab.DLC) && card.displayName().contains(search, ignoreCase = true)
                }
                if (cards.isEmpty()) item { Text("Карты не найдены.") }
                items(cards) { (card, standard) ->
                    DeckCountRow(card.displayName(), recipe.stockOverrides[card] ?: standard) { count ->
                        val overrides = if (count == standard) recipe.stockOverrides - card
                            else recipe.stockOverrides + (card to count)
                        recipe = recipe.copy(stockOverrides = overrides)
                    }
                }
            }
        }
        if (board.deckRecipe != null || recipe.stockOverrides.isNotEmpty() || recipe.customCopies.isNotEmpty()) {
            TextButton(onClick = { onApply(board.copy(customCards = emptyList(), deckRecipe = null)) }) {
                Text("Вернуть стандартную колоду")
            }
        }
    }
}

@Composable
private fun DeckCountRow(name: String, count: Int, onChange: (Int) -> Unit) {
    MenuPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium,
                color = if (count == 0) AppColors.textMuted else AppColors.textPrimary)
            IconButton(onClick = { onChange(count - 1) }, enabled = count > 0,
                modifier = Modifier.semantics { contentDescription = "Убрать копию карты «$name»" }) { Text("−") }
            Text("$count", modifier = Modifier.widthIn(min = 20.dp))
            IconButton(onClick = { onChange(count + 1) }, enabled = count < DeckRecipe.MAX_COPIES,
                modifier = Modifier.semantics { contentDescription = "Добавить копию карты «$name»" }) { Text("+") }
        }
    }
}
