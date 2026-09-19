package com.example.hva.engine

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
                        sb.append("$prefix$numStr$line\n")
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
            val l = text.count { it == '\n' } + if (text.isNotEmpty() && !text.endsWith("\n")) 1 else 0
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
            val key = query.removePrefix(".").trim()
            if (key.isEmpty() || key == ".") {
                0 to trimmed + "\n"
            } else {
                // Robust key extraction without Android platform stub dependency
                val regex = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(\"[^\"]*\"|[^,\\}\\s]+)")
                val match = regex.find(trimmed)
                if (match != null) {
                    val rawVal = match.groupValues[1].trim()
                    val unquoted = if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                        rawVal.substring(1, rawVal.length - 1)
                    } else rawVal
                    0 to unquoted + "\n"
                } else {
                    0 to "null\n"
                }
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
                    if (!args[i].startsWith("-") && urlStr.isEmpty()) {
                        urlStr = args[i]
                    }
                    i++
                }
            }
        }

        if (urlStr.isEmpty()) {
            return 1 to "curl: try 'curl --help' for more information\n"
        }

        return try {
            val url = URL(if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) "https://$urlStr" else urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
            val bytes = stream.readBytes()

            if (outputFile != null) {
                val targetFile = if (outputFile == "AUTO") {
                    val pathName = url.path.substringAfterLast("/")
                    val fn = if (pathName.isNotBlank()) pathName else "index.html"
                    File(workingDir, fn)
                } else {
                    if (outputFile.startsWith("/")) File(outputFile) else File(workingDir, outputFile)
                }
                targetFile.writeBytes(bytes)
                0 to (if (!silent) "  % Total    % Received % Xferd\r\n100 ${bytes.size}  100 ${bytes.size} -> ${targetFile.name}\r\n" else "")
            } else {
                0 to String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            1 to "curl: (6) Could not resolve host: ${e.message}\n"
        }
    }

    fun executeZip(args: List<String>, workingDir: File): Pair<Int, String> {
        val files = args.filter { !it.startsWith("-") }
        if (files.size < 2) {
            return 1 to "usage: zip archive.zip file1 [file2...]\n"
        }
        val zipName = files[0]
        val zipFile = if (zipName.startsWith("/")) File(zipName) else File(workingDir, zipName)
        val targets = files.drop(1)

        return try {
            FileOutputStream(zipFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    for (t in targets) {
                        val f = if (t.startsWith("/")) File(t) else File(workingDir, t)
                        if (f.exists() && f.isFile) {
                            zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            0 to "  adding files to '$zipName'\n"
        } catch (e: Exception) {
            1 to "zip error: ${e.message}\n"
        }
    }

    fun executeUnzip(args: List<String>, workingDir: File): Pair<Int, String> {
        val files = args.filter { !it.startsWith("-") }
        if (files.isEmpty()) {
            return 1 to "usage: unzip archive.zip\n"
        }
        val zipName = files[0]
        val zipFile = if (zipName.startsWith("/")) File(zipName) else File(workingDir, zipName)
        if (!zipFile.exists()) {
            return 1 to "unzip: cannot find or open $zipName\n"
        }

        return try {
            ZipFile(zipFile).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val outFile = File(workingDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
            0 to "Archive:  $zipName\n  extracted successfully.\n"
        } catch (e: Exception) {
            1 to "unzip error: ${e.message}\n"
        }
    }
}
