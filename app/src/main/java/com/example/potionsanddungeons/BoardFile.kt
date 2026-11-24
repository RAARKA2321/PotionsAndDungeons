package com.example.potionsanddungeons

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val BOARD_FILE_FORMAT = "potions-and-dungeons-board"
private const val BOARD_FILE_VERSION = 1
private const val MAX_BOARD_FILE_BYTES = 16 * 1024

internal class BoardFileActions(val pick: () -> Unit, val save: (BoardLayout) -> Unit)

internal fun BoardLayout.fileName(): String {
    val cleaned = name.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("").trim('-').ifEmpty { "map" }
    return "field-$cleaned.json"
}

internal fun BoardLayout.encodeFile(): String =
    JSONObject().put("format", BOARD_FILE_FORMAT).put("version", BOARD_FILE_VERSION)
        .put("board", toJson()).toString(2)

internal fun InputStream.readBoardFile(): BoardLayout {
    val data = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        require(data.size() + read <= MAX_BOARD_FILE_BYTES)
        data.write(buffer, 0, read)
    }
    val file = JSONObject(data.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF"))
    require(file.getString("format") == BOARD_FILE_FORMAT && file.getInt("version") == BOARD_FILE_VERSION)
    return readBoardLayout(file.getJSONObject("board"))
}

internal fun BoardLayout.withAvailableName(layouts: List<BoardLayout>): BoardLayout {
    if (layouts.none { it.name.equals(name, ignoreCase = true) }) return this
    val available = generateSequence(2) { it + 1 }.map(::copyName).first { candidate ->
        layouts.none { it.name.equals(candidate, ignoreCase = true) }
    }
    return copy(name = available)
}

private fun BoardLayout.copyName(number: Int): String {
    val suffix = " ($number)"
    return name.take(BoardLayout.MAX_NAME - suffix.length).trimEnd() + suffix
}
