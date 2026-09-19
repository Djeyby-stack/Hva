package com.example.hva.engine

import java.io.File

/**
 * Shell Pipeline and Redirection Engine for HVA Terminal.
 * Supports:
 *  - Pipes: cmd1 | cmd2 | cmd3
 *  - Output Redirection: cmd > file, cmd >> file
 *  - Input Redirection: cmd < file
 *  - Compound Operators: &&, ||, ;
 */
object PipelineEngine {

    data class CommandSegment(
        val raw: String,
        val command: String,
        val args: List<String>,
        val redirectOutput: File? = null,
        val appendOutput: Boolean = false,
        val redirectInput: File? = null
    )

    fun parseSegment(segmentStr: String, workingDir: File, homeDir: File): CommandSegment {
        var str = segmentStr.trim()
        var redirectOut: File? = null
        var append = false
        var redirectIn: File? = null

        // 1. Check >>
        if (str.contains(">>")) {
            val parts = str.split(">>", limit = 2)
            str = parts[0].trim()
            val targetName = parts[1].trim().split(Regex("\\s+"))[0]
            val resolved = resolveFile(targetName, workingDir, homeDir)
            redirectOut = resolved
            append = true
        } else if (str.contains(">")) {
            // 2. Check >
            val parts = str.split(">", limit = 2)
            str = parts[0].trim()
            val targetName = parts[1].trim().split(Regex("\\s+"))[0]
            val resolved = resolveFile(targetName, workingDir, homeDir)
            redirectOut = resolved
            append = false
        }

        // 3. Check <
        if (str.contains("<")) {
            val parts = str.split("<", limit = 2)
            str = parts[0].trim()
            val targetName = parts[1].trim().split(Regex("\\s+"))[0]
            val resolved = resolveFile(targetName, workingDir, homeDir)
            redirectIn = resolved
        }

        val tokens = parseArgs(str)
        val cmd = tokens.firstOrNull() ?: ""
        val args = if (tokens.isNotEmpty()) tokens.drop(1) else emptyList()

        return CommandSegment(
            raw = segmentStr,
            command = cmd,
            args = args,
            redirectOutput = redirectOut,
            appendOutput = append,
            redirectInput = redirectIn
        )
    }

    private fun resolveFile(name: String, workingDir: File, homeDir: File): File {
        val clean = name.trim('"', '\'')
        return when {
            clean.startsWith("/") -> File(clean)
            clean.startsWith("~/") -> File(homeDir, clean.substring(2))
            clean == "~" -> homeDir
            else -> File(workingDir, clean)
        }
    }

    fun parseArgs(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escape = false

        for (ch in line) {
            if (escape) {
                sb.append(ch)
                escape = false
                continue
            }
            if (ch == '\\') {
                escape = true
                continue
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
                continue
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                continue
            }
            if (ch.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }
}
