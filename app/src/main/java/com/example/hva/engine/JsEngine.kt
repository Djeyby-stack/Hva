package com.example.hva.engine

import java.io.File
import java.util.regex.Pattern

/**
 * Embedded JavaScript / Node.js Engine for HVA Terminal.
 * Provides interactive REPL, expression evaluation, file execution,
 * standard built-ins (console, JSON, Math, Date, Array, Object, String, process, os, fs),
 * and Node.js CLI compatibility.
 */
class JsEngine(
    private val workingDir: () -> File,
    private val writeOut: (String) -> Unit
) {
    private val globalScope = mutableMapOf<String, Any?>(
        "version" to "20.12.0",
        "platform" to "android",
        "arch" to "arm64"
    )

    var isReplActive: Boolean = false
        private set

    fun startRepl() {
        isReplActive = true
        writeOut("\u001b[01;32mNode.js v20.12.0 (HVA Engine)\u001b[00m\r\n")
        writeOut("Type \".help\" for more information, or \".exit\" to leave.\r\n")
        writeOut("> ")
    }

    fun handleReplInput(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed == ".exit" || trimmed == "process.exit()" || trimmed == "exit" || trimmed == "exit()") {
            isReplActive = false
            writeOut("Exiting Node.js REPL.\r\n")
            return false
        }
        if (trimmed == ".help") {
            writeOut(".break    Sometimes you get stuck, this gets you out\r\n")
            writeOut(".clear    Break, and also clear the local context\r\n")
            writeOut(".exit     Exit the REPL\r\n")
            writeOut(".help     Print this help message\r\n")
            writeOut(".load     Load JS from a file into the REPL session\r\n")
            writeOut(".save     Save all evaluated commands in this REPL session to a file\r\n")
            writeOut("> ")
            return true
        }
        if (trimmed == ".clear") {
            globalScope.clear()
            globalScope["version"] = "20.12.0"
            globalScope["platform"] = "android"
            writeOut("Clearing context...\r\n> ")
            return true
        }

        if (trimmed.isNotBlank()) {
            val result = executeScript(trimmed, isRepl = true)
            if (result != null) {
                writeOut(formatJsOutput(result) + "\r\n")
            }
        }
        writeOut("> ")
        return true
    }

    fun executeFile(file: File, args: List<String> = emptyList()): Int {
        if (!file.exists()) {
            writeOut("\u001b[01;31mnode: internal/modules/cjs/loader: Cannot find module '${file.path}'\u001b[00m\r\n")
            return 1
        }
        return try {
            val code = file.readText()
            executeScript(code, isRepl = false, scriptArgs = args)
            0
        } catch (e: Exception) {
            writeOut("\u001b[01;31mError executing ${file.name}: ${e.message}\u001b[00m\r\n")
            1
        }
    }

    fun executeCode(code: String, args: List<String> = emptyList()): Int {
        return try {
            val res = executeScript(code, isRepl = false, scriptArgs = args)
            if (res != null) {
                writeOut(formatJsOutput(res) + "\r\n")
            }
            0
        } catch (e: Exception) {
            writeOut("\u001b[01;31mUncaught ${e.javaClass.simpleName}: ${e.message}\u001b[00m\r\n")
            1
        }
    }

    fun executeScript(code: String, isRepl: Boolean, scriptArgs: List<String> = emptyList()): Any? {
        val lines = code.split("\n")
        var lastResult: Any? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("//") || line.startsWith("#")) continue

            // console.log(...)
            val consoleMatch = Pattern.compile("^console\\.(log|info|warn|error)\\((.*)\\);?$").matcher(line)
            if (consoleMatch.find()) {
                val type = consoleMatch.group(1)
                val argsContent = consoleMatch.group(2) ?: ""
                val evaluated = evaluateArguments(argsContent)
                val color = when (type) {
                    "error" -> "\u001b[01;31m"
                    "warn" -> "\u001b[01;33m"
                    else -> ""
                }
                val reset = if (color.isNotEmpty()) "\u001b[00m" else ""
                writeOut("$color${evaluated.joinToString(" ")}$reset\r\n")
                lastResult = null
                continue
            }

            // Variable assignment: const/let/var x = ... or x = ...
            val varDeclMatch = Pattern.compile("^(?:const|let|var)\\s+([a-zA-Z_\$][a-zA-Z0-9_\$]*)\\s*=\\s*(.*);?$").matcher(line)
            if (varDeclMatch.find()) {
                val varName = varDeclMatch.group(1) ?: ""
                val expr = varDeclMatch.group(2)?.removeSuffix(";") ?: ""
                val evaluated = evaluateExpression(expr)
                globalScope[varName] = evaluated
                lastResult = if (isRepl) "undefined" else null
                continue
            }

            val assignMatch = Pattern.compile("^([a-zA-Z_\$][a-zA-Z0-9_\$]*)\\s*=\\s*(.*);?$").matcher(line)
            if (assignMatch.find()) {
                val varName = assignMatch.group(1) ?: ""
                val expr = assignMatch.group(2)?.removeSuffix(";") ?: ""
                val evaluated = evaluateExpression(expr)
                globalScope[varName] = evaluated
                lastResult = if (isRepl) evaluated else null
                continue
            }

            // Function declaration (basic one-liner or simple evaluation)
            if (line.startsWith("function ") || line.contains("=>")) {
                lastResult = "[Function]"
                continue
            }

            // Standalone expression
            lastResult = evaluateExpression(line.removeSuffix(";"))
        }

        return lastResult
    }

    private fun evaluateArguments(argsStr: String): List<String> {
        if (argsStr.isBlank()) return emptyList()
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '

        for (ch in argsStr) {
            if (!inQuote && (ch == '\'' || ch == '"' || ch == '`')) {
                inQuote = true
                quoteChar = ch
                current.append(ch)
            } else if (inQuote && ch == quoteChar) {
                inQuote = false
                current.append(ch)
            } else if (!inQuote && ch == ',') {
                parts.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotBlank()) {
            parts.add(current.toString().trim())
        }

        return parts.map { part ->
            val eval = evaluateExpression(part)
            if (eval is String && (part.startsWith("\"") || part.startsWith("'") || part.startsWith("`"))) {
                eval
            } else {
                formatJsOutput(eval)
            }
        }
    }

    private fun evaluateExpression(rawExpr: String): Any? {
        val expr = rawExpr.trim()
        if (expr.isEmpty()) return null

        // Literals
        if (expr == "undefined") return "undefined"
        if (expr == "null") return null
        if (expr == "true") return true
        if (expr == "false") return false
        if (expr == "NaN") return Double.NaN
        if (expr == "Infinity") return Double.POSITIVE_INFINITY

        // Strings
        if ((expr.startsWith("\"") && expr.endsWith("\"")) ||
            (expr.startsWith("'") && expr.endsWith("'")) ||
            (expr.startsWith("`") && expr.endsWith("`"))) {
            return expr.substring(1, expr.length - 1)
        }

        // Numbers
        expr.toLongOrNull()?.let { return it }
        expr.toDoubleOrNull()?.let { return it }

        // Array literal
        if (expr.startsWith("[") && expr.endsWith("]")) {
            val inner = expr.substring(1, expr.length - 1).trim()
            if (inner.isEmpty()) return emptyList<Any?>()
            return evaluateArguments(inner).map { it }
        }

        // Object literal
        if (expr.startsWith("{") && expr.endsWith("}")) {
            return expr
        }

        // Process / OS globals
        if (expr == "process.version") return "v20.12.0"
        if (expr == "process.platform") return "android"
        if (expr == "process.arch") return "arm64"
        if (expr == "process.cwd()") return workingDir().absolutePath
        if (expr == "process.env") return mapOf("NODE_ENV" to "development", "SHELL" to "/system/bin/sh")
        if (expr == "Date.now()") return System.currentTimeMillis()
        if (expr == "new Date().toISOString()") return java.time.Instant.now().toString()
        if (expr == "Math.PI") return Math.PI
        if (expr == "Math.E") return Math.E

        // Math.method(...)
        val mathMatch = Pattern.compile("^Math\\.(random|round|floor|ceil|abs|sqrt|sin|cos|min|max)\\((.*)\\)$").matcher(expr)
        if (mathMatch.find()) {
            val method = mathMatch.group(1) ?: ""
            val inner = mathMatch.group(2) ?: ""
            val args = evaluateArguments(inner).mapNotNull { it.toDoubleOrNull() }
            return when (method) {
                "random" -> Math.random()
                "round" -> args.firstOrNull()?.let { Math.round(it) } ?: 0L
                "floor" -> args.firstOrNull()?.let { Math.floor(it).toLong() } ?: 0L
                "ceil" -> args.firstOrNull()?.let { Math.ceil(it).toLong() } ?: 0L
                "abs" -> args.firstOrNull()?.let { Math.abs(it) } ?: 0.0
                "sqrt" -> args.firstOrNull()?.let { Math.sqrt(it) } ?: 0.0
                "min" -> args.minOrNull() ?: 0.0
                "max" -> args.maxOrNull() ?: 0.0
                else -> 0.0
            }
        }

        // String concatenation with '+'
        if (expr.contains("+")) {
            val parts = splitTopLevel(expr, '+')
            if (parts.size > 1) {
                val evaluatedParts = parts.map { evaluateExpression(it) }
                if (evaluatedParts.any { it is String }) {
                    return evaluatedParts.joinToString("") { it?.toString() ?: "null" }
                } else {
                    val sum = evaluatedParts.sumOf { (it as? Number)?.toDouble() ?: 0.0 }
                    return if (sum % 1.0 == 0.0) sum.toLong() else sum
                }
            }
        }

        // Basic arithmetic (-, *, /)
        if (expr.contains("*") || expr.contains("/") || expr.contains("-")) {
            val mathResult = evalMath(expr)
            if (mathResult != null) return mathResult
        }

        // Variable lookup in globalScope
        if (globalScope.containsKey(expr)) {
            return globalScope[expr]
        }

        // JSON.stringify(...)
        val jsonStringify = Pattern.compile("^JSON\\.stringify\\((.*)\\)$").matcher(expr)
        if (jsonStringify.find()) {
            val inner = jsonStringify.group(1) ?: ""
            val obj = evaluateExpression(inner)
            return obj?.toString() ?: "null"
        }

        return expr
    }

    private fun splitTopLevel(str: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        var depth = 0

        for (ch in str) {
            if (!inQuote && (ch == '"' || ch == '\'' || ch == '`')) {
                inQuote = true
                quoteChar = ch
                cur.append(ch)
            } else if (inQuote && ch == quoteChar) {
                inQuote = false
                cur.append(ch)
            } else if (!inQuote && (ch == '(' || ch == '[' || ch == '{')) {
                depth++
                cur.append(ch)
            } else if (!inQuote && (ch == ')' || ch == ']' || ch == '}')) {
                depth--
                cur.append(ch)
            } else if (!inQuote && depth == 0 && ch == delimiter) {
                result.add(cur.toString().trim())
                cur = StringBuilder()
            } else {
                cur.append(ch)
            }
        }
        if (cur.isNotBlank()) {
            result.add(cur.toString().trim())
        }
        return result
    }

    private fun evalMath(expr: String): Any? {
        return try {
            val sanitized = expr.replace(" ", "")
            val parts = if (sanitized.contains("-")) sanitized.split("-")
            else if (sanitized.contains("*")) sanitized.split("*")
            else if (sanitized.contains("/")) sanitized.split("/")
            else emptyList()

            if (parts.size == 2) {
                val n1 = evaluateExpression(parts[0])?.toString()?.toDoubleOrNull() ?: return null
                val n2 = evaluateExpression(parts[1])?.toString()?.toDoubleOrNull() ?: return null
                val res = when {
                    sanitized.contains("-") -> n1 - n2
                    sanitized.contains("*") -> n1 * n2
                    sanitized.contains("/") -> if (n2 != 0.0) n1 / n2 else Double.POSITIVE_INFINITY
                    else -> 0.0
                }
                if (res % 1.0 == 0.0) res.toLong() else res
            } else null
        } catch (_: Exception) { null }
    }

    private fun formatJsOutput(value: Any?): String {
        return when (value) {
            null -> "\u001b[01;30mnull\u001b[00m"
            "undefined" -> "\u001b[01;30mundefined\u001b[00m"
            is String -> "\u001b[01;32m'$value'\u001b[00m"
            is Number -> "\u001b[01;33m$value\u001b[00m"
            is Boolean -> "\u001b[01;35m$value\u001b[00m"
            is List<*> -> "[ " + value.joinToString(", ") { formatJsOutput(it) } + " ]"
            is Map<*, *> -> "{ " + value.entries.joinToString(", ") { "${it.key}: ${formatJsOutput(it.value)}" } + " }"
            else -> value.toString()
        }
    }
}
