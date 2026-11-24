package com.example.potionsanddungeons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BoardLayoutsScreen(
    layouts: List<BoardLayout>, onChange: (List<BoardLayout>) -> Unit, onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    val index = editing
    if (index != null) {
        BoardLayoutEditor(layouts.getOrNull(index), layouts.filterIndexed { i, _ -> i != index }.map { it.name },
            onSave = { layout ->
                onChange(if (index < 0) layouts + layout else layouts.toMutableList().apply { this[index] = layout })
                editing = null
            },
            onDelete = if (index >= 0) ({
                onChange(layouts.filterIndexed { i, _ -> i != index })
                editing = null
            }) else null,
            onBack = { editing = null }
        )
        return
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val files = rememberBoardFiles(onImport = { result ->
        busy = false
        result?.fold(onSuccess = { layout ->
            when {
                layout in layouts -> message = "Поле «${layout.name}» уже есть."
                layouts.size >= BoardLayout.MAX_LAYOUTS -> message = "Освободи место в списке полей перед импортом."
                else -> {
                    val imported = layout.withAvailableName(layouts)
                    onChange(layouts + imported)
                    message = "Импортировано поле «${imported.name}»."
                }
            }
        }, onFailure = { message = "Не удалось открыть поле. Выбери файл, экспортированный из игры." })
    }, onExport = { result ->
        busy = false
        result?.fold(onSuccess = { message = "Файл поля сохранён. Им можно поделиться." },
            onFailure = { message = "Не удалось сохранить файл поля." })
    })
    fun chooseBoardFile(action: () -> Unit) {
        message = null
        busy = true
        runCatching(action).onFailure {
            busy = false
            message = "Не удалось открыть окно выбора файла."
        }
    }
    val back = { if (!busy) onBack() }
    BackHandler(onBack = back)
    MenuPage("Мои поля", back, footer = {
        val hasSpace = layouts.size < BoardLayout.MAX_LAYOUTS
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = -1 }, enabled = !busy && hasSpace,
                modifier = Modifier.weight(1f)) { Text("Создать поле") }
            OutlinedButton(onClick = { chooseBoardFile(files.pick) }, enabled = !busy && hasSpace,
                modifier = Modifier.weight(1f)) { Text("Импорт") }
        }
    }) {
        message?.let { Text(it, color = AppColors.accent) }
        if (layouts.isEmpty()) MenuPanel {
            Text("Создай поле, чтобы выбрать его перед локальной или сетевой партией.", color = AppColors.textMuted)
        }
        if (layouts.size == BoardLayout.MAX_LAYOUTS) Text("Можно сохранить до ${BoardLayout.MAX_LAYOUTS} полей.")
        layouts.forEachIndexed { i, layout ->
            MenuPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MenuLink(layout.name, onClick = { if (!busy) editing = i }, modifier = Modifier.weight(1f),
                        detail = "${layout.size}×${layout.size} · Клеток: ${layout.tiles.size}")
                    TextButton(enabled = !busy, onClick = { chooseBoardFile { files.save(layout) } }) {
                        Text("Экспорт")
                    }
                }
            }
        }
    }
}

private enum class MapTool(val label: String, val symbol: String) {
    TILE("Клетки", ""), BOSS("Босс", "Б"), POTION("Зелье", "З"), DOOR("Дверь", "Д"),
    PLAYER1("Игрок 1", "1"), PLAYER2("Игрок 2", "2"), PLAYER3("Игрок 3", "3"), PLAYER4("Игрок 4", "4")
}

@Composable
private fun BoardLayoutEditor(
    initial: BoardLayout?, existingNames: List<String>, onSave: (BoardLayout) -> Unit,
    onDelete: (() -> Unit)?, onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var size by remember { mutableIntStateOf(initial?.size ?: 7) }
    val grid = remember(size) { List(size * size) { Cell(it / size, it % size) } }
    var tiles by remember { mutableStateOf(initial?.tiles ?: grid.toSet()) }
    var starts by remember {
        mutableStateOf(buildMap {
            initial?.let { layout ->
                put(MapTool.BOSS, layout.bossStart)
                layout.starts.forEachIndexed { index, cell -> put(MapTool.entries[MapTool.PLAYER1.ordinal + index], cell) }
            }
        })
    }
    var potions by remember { mutableStateOf(initial?.potions.orEmpty()) }
    var doors by remember { mutableStateOf(initial?.doors.orEmpty()) }
    var tool by remember { mutableStateOf(MapTool.TILE) }
    var practice by remember { mutableStateOf<GameEngine?>(null) }
    fun changeSize(next: Int) {
        if (size == next) return
        val previous = size
        size = next
        tiles = tiles.filter { it.inBounds(next) }.toSet() +
            List(next * next) { Cell(it / next, it % next) }.filterNot { it.inBounds(previous) }
        starts = starts.filterValues { it in tiles }
        potions = potions.intersect(tiles)
        doors = doors.intersect(tiles)
    }
    fun edit(cell: Cell) {
        when (tool) {
            MapTool.TILE -> if (cell in tiles) {
                tiles = tiles - cell
                starts = starts.filterValues { it != cell }
                potions = potions - cell
                doors = doors - cell
            } else tiles = tiles + cell
            MapTool.POTION -> if (cell in tiles && cell !in doors) {
                potions = if (cell in potions) potions - cell else potions + cell
            }
            MapTool.DOOR -> if (cell in tiles && cell !in starts.values) {
                doors = if (cell in doors) doors - cell else doors + cell
                potions = potions - cell
            }
            else -> if (cell in tiles && cell !in doors) {
                val kept = starts.filterValues { it != cell }
                starts = if (starts[tool] == cell) kept else kept + (tool to cell)
            }
        }
    }
    val connected = tiles.firstOrNull()?.let { boardDistances(it, tiles, size).size == tiles.size } == true
    val duplicate = existingNames.any { it.equals(name.trim(), ignoreCase = true) }
    val geometryProblem = when {
        (tiles - doors).size < BoardLayout.MIN_TILES -> "Нужно минимум пять клеток без дверей для игроков и босса."
        !connected -> "Соедини все клетки по сторонам."
        else -> null
    }
    val problem = geometryProblem ?: if (duplicate) "Поле с таким названием уже есть." else null
    fun makeLayout(title: String): BoardLayout {
        val placements = starts.toMutableMap()
        val preferred = listOf(boardCenter(size)) + boardCorners(size)
        val markers = listOf(MapTool.BOSS, MapTool.PLAYER1, MapTool.PLAYER2, MapTool.PLAYER3, MapTool.PLAYER4)
        markers.forEachIndexed { i, marker ->
            if (marker !in placements) {
                val origin = preferred[i]
                placements[marker] = grid.filter { it in tiles && it !in doors && it !in placements.values }
                    .minBy { kotlin.math.abs(it.row - origin.row) + kotlin.math.abs(it.col - origin.col) }
            }
        }
        return BoardLayout(title, size, tiles, markers.drop(1).map(placements::getValue),
            placements.getValue(MapTool.BOSS), potions, doors)
    }
    fun startPractice() {
        val layout = makeLayout(name.trim().ifEmpty { "Пробное поле" })
        val rules = CustomRules(board = BoardOptions(size = layout.size, layout = layout))
        practice = GameEngine(false, animateD4 = true, offerReroll = true, customRules = rules).apply {
            setupGame(listOf("Ты", "Бот 1", "Бот 2", "Бот 3"), setOf(1, 2, 3), fixedTurnOrder = true)
        }
    }
    practice?.let { engine ->
        val exit = { practice = null }
        BackHandler(onBack = exit)
        key(engine) {
            GameScreen(engine, onRestart = ::startPractice, onOpenSettings = exit, playtest = true)
        }
        return
    }
    BackHandler(onBack = onBack)
    MenuPage(if (initial == null) "Новое поле" else "Редактор поля", onBack, footer = {
        if (problem != null) Text(problem, color = AppColors.accent, fontSize = AppFonts.legend)
        OutlinedButton(onClick = ::startPractice, enabled = geometryProblem == null,
            modifier = Modifier.fillMaxWidth()) { Text("Проверить в игре") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить") }
            Button(enabled = name.isNotBlank() && problem == null, modifier = Modifier.weight(1f), onClick = {
                onSave(makeLayout(name.trim()))
            }) { Text("Сохранить поле") }
        }
    }) {
        OutlinedTextField(name, { name = it.take(BoardLayout.MAX_NAME) }, label = { Text("Название поля") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BOARD_SIZE, 7).forEach { side ->
                FilterChip(size == side, onClick = { changeSize(side) }, label = { Text("$side×$side") })
            }
            TextButton(onClick = { tiles = emptySet(); starts = emptyMap(); potions = emptySet(); doors = emptySet(); tool = MapTool.TILE }) {
                Text("Очистить")
            }
            TextButton(onClick = { tiles = grid.toSet() }) { Text("Заполнить") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapTool.entries.forEach { item ->
                FilterChip(tool == item, onClick = { tool = item }, label = { Text(item.label) })
            }
        }
        Text(if (tool == MapTool.TILE) "Нажимай на клетки, чтобы менять контур поля."
            else "Нажми на клетку, чтобы поставить или убрать «${tool.label}».", color = AppColors.textMuted)
        MenuPanel {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val step = minOf(maxWidth / size, 60.dp)
            Column {
                grid.chunked(size).forEach { row ->
                    Row {
                        row.forEach { cell ->
                            val active = cell in tiles
                            val marker = starts.entries.firstOrNull { it.value == cell }?.key ?: when (cell) {
                                in doors -> MapTool.DOOR
                                in potions -> MapTool.POTION
                                else -> null
                            }
                            val shape = RoundedCornerShape(6.dp)
                            Box(Modifier.size(step).padding(2.dp)
                                .background(if (active) AppColors.cellBg else AppColors.boardBg, shape)
                                .border(1.dp, if (marker != null) AppColors.accent else AppColors.textMuted.copy(alpha = 0.35f), shape)
                                .clickable { edit(cell) }
                                .semantics {
                                    contentDescription = "Ряд ${cell.row + 1}, столбец ${cell.col + 1}. " +
                                        (marker?.label ?: if (active) "Клетка" else "Пусто")
                                }, contentAlignment = Alignment.Center) {
                                marker?.let { Text(it.symbol, color = AppColors.accent, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
        }
        Text("Если не задать стартовые позиции или зелья, игра расставит их сама.", color = AppColors.textMuted)
    }
}
