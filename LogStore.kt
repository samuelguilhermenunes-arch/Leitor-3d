package com.modscout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda o que o jogo fez, para a tela e o relatório. */
object LogStore {
    val lines = MutableStateFlow<List<String>>(emptyList())
    private val firstSeen = LinkedHashMap<String, String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun add(kind: String, path: String) {
        val time = fmt.format(Date())
        if (kind != "INFO" && !firstSeen.containsKey(path)) firstSeen[path] = kind
        lines.update { (it + "[$time] $kind  $path").takeLast(1500) }
    }

    @Synchronized
    fun clear() {
        firstSeen.clear()
        lines.value = emptyList()
    }

    /** Relatório simples: cada arquivo aparece uma vez, na ordem em que surgiu. */
    @Synchronized
    fun report(): String = buildString {
        appendLine("Arquivos usados pelo jogo (${firstSeen.size}):")
        firstSeen.forEach { (path, kind) -> appendLine("- $path  ($kind)") }
    }
}
