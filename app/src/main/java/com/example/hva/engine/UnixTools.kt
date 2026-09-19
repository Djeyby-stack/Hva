package com.example.hva.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Built-in UNIX Utilities for HVA Terminal.
 * Implements real file stream processing, regular expressions, JSON query filtering,
 * archiving, and HTTP networking.
 */
object UnixTools {

    fun executeGrep(args: List<String>, workingDir: File, stdin: String?): Pair<Int, String> {
        var ignoreCase = false
        var invert = false
        var lineNumbers = false
        var countOnly = false
        val positional = mutableListOf<String>()

        for (arg in args) {
            if (arg == "-i" || arg == "--ignore-case") ignoreCase = true
            else if (arg == "-v" || arg == "--invert-match") invert = true
            else if (arg == "-n" || arg == "--line-number") lineNumbers = true
            else if (arg == "-c" || arg == "--count") countOnly = true
            else if (!arg.startsWith("-")) positional.add(arg)
        }

        if (positional.isEmpty()) {
            return 1 to "usage: grep [-ivnc] pattern [file...]\n"
        }

        val patternStr = positional[0]
        val regex = try {
            if (ignoreCase) Regex(patternStr, RegexOption.IGNORE_CASE) else Regex(patternStr)
        } catch (_: Exception) {
            return 1 to "grep: invalid regular expression '$patternStr'\n"
        }

        val fileArgs = positional.drop(1)
        val sb = StringBuilder()
        var matchCount = 0

        fun processLines(lines: List<String>, prefix: String = "") {
            lines.forEachIndexed { idx, line ->
                val matches = regex.containsMatchIn(line)
                val matchesCondition = if (invert) !matches else matches
                if (matchesCondition) {
                    matchCount++
                    if (!countOnly) {
                        val numStr = if (lineNumbers) "${idx + 1}:" else ""
                        val highlighted = if (!invert) {
                            regex.replace(line) { "\u001b[01;31m${it.value}\u001b[00m" }
                        } else line
                        sb.append("$prefix$numStr$highlighted\n")
                    }
                }
            }
        }

        if (fileArgs.isEmpty()) {
            val lines = (stdin ?: "").split("\n")
            processLines(lines)
        } else {
            for (fn in fileArgs) {
                val f = if (fn.startsWith("/")) File(fn) else File(workingDir, fn)
                if (!f.exists()) {
                    sb.append("grep: ${f.name}: No such file or directory\n")
                    continue
                }
                val prefix = if (fileArgs.size > 1) "${f.name}:" else ""
                processLines(f.readLines(), prefix)
            }
        }

        if (countOnly) {
            sb.append("$matchCount\n")
        }

        val code = if (matchCount > 0) 0 else 1
        return code to sb.toString()
    }

    fun executeWc(args: List<String>, workingDir: File, stdin: String?): Pair<Int, String> {
        var countLines = false
        var countWords = false
        var countBytes = false
        val files = mutableListOf<String>()

        for (arg in args) {
            if (arg == "-l" || arg == "--lines") countLines = true
            else if (arg == "-w" || arg == "--words") countWords = true
            else if (arg == "-c" || arg == "--bytes") countBytes = true
            else if (!arg.startsWith("-")) files.add(arg)
        }

        if (!countLines && !countWords && !countBytes) {
            countLines = true
            countWords = true
            countBytes = true
        }

        val sb = StringBuilder()

        fun countText(text: String, label: String = "") {
            val l = text.lines().size
            val w = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val c = text.toByteArray().size
            val parts = mutableListOf<String>()
            if (countLines) parts.add(String.format("%7d", l))
            if (countWords) parts.add(String.format("%7d", w))
            if (countBytes) parts.add(String.format("%7d", c))
            if (label.isNotBlank()) parts.add(label)
            sb.append(parts.joinToString(" ") + "\n")
        }

        if (files.isEmpty()) {
            countText(stdin ?: "")
        } else {
            for (fn in files) {
                val f = if (fn.startsWith("/")) File(fn) else File(workingDir, fn)
                if (!f.exists()) {
                    sb.append("wc: ${f.name}: No such file or directory\n")
                    continue
                }
                countText(f.readText(), f.name)
            }
        }

        return 0 to sb.toString()
    }

    fun executeHead(args: List<String>, workingDir: File, stdin: String?): Pair<Int, String> {
        var n = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            if (args[i] == "-n" && i + 1 < args.size) {
                n = args[i + 1].toIntOrNull() ?: 10
                i += 2
            } else if (args[i].startsWith("-n")) {
                n = args[i].substring(2).toIntOrNull() ?: 10
                i++
            } else if (!args[i].startsWith("-")) {
                files.add(args[i])
                i++
            } else i++
        }

        val sb = StringBuilder()
        if (files.isEmpty()) {
            val lines = (stdin ?: "").lines().take(n)
            sb.append(lines.joinToString("\n") + "\n")
        } else {
            for (fn in files) {
                val f = if (fn.startsWith("/")) File(fn) else File(workingDir, fn)
                if (f.exists()) {
                    val lines = f.readLines().take(n)
                    sb.append(lines.joinToString("\n") + "\n")
                } else {
                    sb.append("head: cannot open '${f.name}': No such file\n")
                }
            }
        }
        return 0 to sb.toString()
    }

    fun executeTail(args: List<String>, workingDir: File, stdin: String?): Pair<Int, String> {
        var n = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            if (args[i] == "-n" && i + 1 < args.size) {
                n = args[i + 1].toIntOrNull() ?: 10
                i += 2
            } else if (args[i].startsWith("-n")) {
                n = args[i].substring(2).toIntOrNull() ?: 10
                i++
            } else if (!args[i].startsWith("-")) {
                files.add(args[i])
                i++
            } else i++
        }

        val sb = StringBuilder()
        if (files.isEmpty()) {
            val lines = (stdin ?: "").lines().takeLast(n)
            sb.append(lines.joinToString("\n") + "\n")
        } else {
            for (fn in files) {
                val f = if (fn.startsWith("/")) File(fn) else File(workingDir, fn)
                if (f.exists()) {
                    val lines = f.readLines().takeLast(n)
                    sb.append(lines.joinToString("\n") + "\n")
                } else {
                    sb.append("tail: cannot open '${f.name}': No such file\n")
                }
            }
        }
        return 0 to sb.toString()
    }

    fun executeJq(args: List<String>, workingDir: File, stdin: String?): Pair<Int, String> {
        val query = args.firstOrNull { !it.startsWith("-") } ?: "."
        val fileArg = args.filter { !it.startsWith("-") }.getOrNull(1)

        val jsonStr = if (fileArg != null) {
            val f = if (fileArg.startsWith("/")) File(fileArg) else File(workingDir, fileArg)
            if (!f.exists()) return 1 to "jq: error: could not open file $fileArg\n"
            f.readText()
        } else {
            stdin ?: ""
        }

        if (jsonStr.isBlank()) {
            return 1 to "jq: parse error: empty input\n"
        }

        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                if (query == "." || query.isBlank()) {
                    0 to obj.toString(2) + "\n"
                } else {
                    val key = query.removePrefix(".").trim()
                    if (obj.has(key)) {
                        0 to obj.get(key).toString() + "\n"
                    } else {
                        0 to "null\n"
                    }
                }
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                if (query == "." || query.isBlank()) {
                    0 to arr.toString(2) + "\n"
                } else if (query.contains("[") && query.contains("]")) {
                    val idx = query.substringAfter("[").substringBefore("]").toIntOrNull() ?: 0
                    if (idx in 0 until arr.length()) {
                        0 to arr.get(idx).toString() + "\n"
                    } else {
                        0 to "null\n"
                    }
                } else {
                    0 to arr.toString(2) + "\n"
                }
            } else {
                0 to trimmed + "\n"
            }
        } catch (e: Exception) {
            1 to "jq: parse error: ${e.message}\n"
        }
    }

    fun executeCurl(args: List<String>, workingDir: File): Pair<Int, String> {
        var urlStr = ""
        var outputFile: String? = null
        var method = "GET"
        var silent = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-o" -> {
                    if (i + 1 < args.size) { outputFile = args[i + 1]; i += 2 } else i++
                }
                "-O" -> {
                    outputFile = "AUTO"
                    i++
                }
                "-X" -> {
                    if (i + 1 < args.size) { method = args[i + 1]; i += 2 } else i++
                }
                "-s", "--silent" -> { silent = true; i++ }
                else -> {
                    if (!args[i].startsWith("-")) urlStr = args[i]
                    i++
                }
            }
        }

        if (urlStr.isBlank()) {
            return 1 to "curl: try 'curl --help' for more information\n"
        }

        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "https://$urlStr"
        }

        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "HvaTerminal/0.0.8 (Android; arm64)")

            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""

            if (outputFile == "AUTO") {
                val name = urlStr.substringAfterLast("/").ifBlank { "index.html" }
                val f = File(workingDir, name)
                f.writeText(body)
                0 to (if (!silent) "Saved to ${f.name} (${body.length} bytes)\n" else "")
            } else if (outputFile != null) {
                val f = if (outputFile.startsWith("/")) File(outputFile) else File(workingDir, outputFile)
                f.parentFile?.mkdirs()
                f.writeText(body)
                0 to (if (!silent) "Saved to ${f.name} (${body.length} bytes)\n" else "")
            } else {
                0 to body + "\n"
            }
        } catch (e: Exception) {
            1 to "curl: (6) Could not resolve host: ${e.message}\n"
        }
    }
}
