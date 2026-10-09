package com.modscout

import android.content.ContentResolver
import android.net.Uri
import java.util.zip.ZipInputStream

data class CheckItem(val ok: Boolean, val text: String)

/**
 * Confere se o .zip de um mod tem todos os arquivos que ele mesmo cita.
 * Foco em Farming Simulator (modDesc.xml), mas o teste de referências
 * funciona para qualquer mod com arquivos .xml/.i3d.
 */
object ModChecker {

    private val attr = Regex("""(?:filename|iconFilename|file)\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

    fun check(resolver: ContentResolver, uri: Uri): List<CheckItem> {
        val names = LinkedHashSet<String>()
        val texts = HashMap<String, String>()

        resolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (!entry.isDirectory) {
                        names.add(name)
                        val lower = name.lowercase()
                        if (lower.endsWith(".xml") || lower.endsWith(".i3d")) {
                            val bytes = zip.readBytes()
                            if (bytes.size < 5_000_000) texts[name] = String(bytes, Charsets.UTF_8)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: return listOf(CheckItem(false, "Não consegui abrir o arquivo."))

        val out = mutableListOf<CheckItem>()
        if (names.isEmpty()) return listOf(CheckItem(false, "O arquivo está vazio ou não é um .zip válido."))

        val exact = names.toHashSet()
        val lowerMap = names.associateBy { it.lowercase() }

        // 1) modDesc.xml na raiz
        val desc = texts["modDesc.xml"]
        if (desc == null) {
            val misplaced = names.firstOrNull { it.endsWith("modDesc.xml", true) }
            out += CheckItem(
                false,
                if (misplaced != null)
                    "modDesc.xml está dentro de uma pasta ($misplaced). Ele precisa ficar na raiz do zip, sem pasta por fora."
                else
                    "Falta o arquivo modDesc.xml. Sem ele o jogo ignora o mod."
            )
        } else {
            out += CheckItem(true, "modDesc.xml encontrado na raiz.")
            if (!desc.contains("descVersion")) out += CheckItem(false, "modDesc.xml não tem descVersion (versão do jogo).")
            else out += CheckItem(true, "descVersion presente.")
        }

        // 2) cada referência de arquivo
        var refs = 0
        var missing = 0
        for ((file, content) in texts) {
            val baseDir = file.substringBeforeLast('/', "")
            for (m in attr.findAll(content)) {
                val ref = m.groupValues[1].trim()
                if (ref.startsWith("$") || ref.startsWith("http") || ref.isEmpty()) continue
                if (!ref.contains('.')) continue
                refs++
                val target = normalize(if (file == "modDesc.xml") ref else join(baseDir, ref))
                val alt = normalize(ref) // algumas referências já são da raiz do mod
                when {
                    target in exact || alt in exact -> {}
                    target.lowercase() in lowerMap || alt.lowercase() in lowerMap -> {
                        val real = lowerMap[target.lowercase()] ?: lowerMap[alt.lowercase()]
                        missing++
                        out += CheckItem(false, "Maiúscula/minúscula diferente: \"$ref\" (citado em $file) existe como \"$real\". No Android isso quebra.")
                    }
                    else -> {
                        missing++
                        out += CheckItem(false, "Falta: \"$ref\" (citado em $file)")
                    }
                }
            }
        }
        if (refs > 0 && missing == 0) out += CheckItem(true, "Todos os $refs arquivos citados estão no zip.")

        // 3) dicas gerais
        if (names.any { it.contains(' ') && !it.contains('/') })
            out += CheckItem(false, "Nome de arquivo na raiz com espaço. Prefira nomes sem espaço.")

        return out
    }

    private fun join(dir: String, ref: String) = if (dir.isEmpty()) ref else "$dir/$ref"

    private fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        for (p in path.replace('\\', '/').split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(p)
            }
        }
        return parts.joinToString("/")
    }
}
