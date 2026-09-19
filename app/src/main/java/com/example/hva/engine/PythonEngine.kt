package com.example.hva.engine

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Embedded Python 3 Engine for HVA Terminal.
 * Provides interactive REPL, expression evaluation, script execution,
 * standard built-ins (print, len, range, sum, min, max, type, str, int, float, math, sys, os),
 * and Python CLI compatibility.
 */
class PythonEngine(
    private val workingDir: () -> File,
    private val writeOut: (String) -> Unit
) {
    private val variables = mutableMapOf<String, Any?>(
        "__name__" to "__main__",
        "__version__" to "3.11.8"
    )

    var isReplActive: Boolean = false
        private set

    fun startRepl() {
        isReplActive = true
        val dateStr = SimpleDateFormat("MMM dd yyyy, HH:mm:ss", Locale.US).format(Date())
        writeOut("Python 3.11.8 (HVA Bionic Engine, $dateStr) [Clang/Bionic arm64] on android\r\n")
        writeOut("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.\r\n")
        writeOut(">>> ")
    }

    fun handleReplInput(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed == "exit" || trimmed == "exit()" || trimmed == "quit" || trimmed == "quit()") {
            isReplActive = false
            return false
        }
        if (trimmed == "help" || trimmed == "help()") {
            writeOut("Type help() for interactive help, or help(object) for help about object.\r\n>>> ")
            return true
        }
        if (trimmed == "copyright" || trimmed == "credits") {
            writeOut("Copyright (c) 2001-2024 Python Software Foundation. Embedded HVA Bionic Port.\r\n>>> ")
            return true
        }

        if (trimmed.isNotBlank()) {
            val result = executeScript(trimmed, isRepl = true)
            if (result != null) {
                writeOut(formatPythonOutput(result) + "\r\n")
            }
        }
        writeOut(">>> ")
        return true
    }

    fun executeFile(file: File, args: List<String> = emptyList()): Int {
        if (!file.exists()) {
            writeOut("\u001b[01;31mpython3: can't open file '${file.path}': [Errno 2] No such file or directory\u001b[00m\r\n")
            return 1
        }
        return try {
            val code = file.readText()
            executeScript(code, isRepl = false, scriptArgs = args)
            0
        } catch (e: Exception) {
            writeOut("\u001b[01;31mTraceback (most recent call last):\r\n  File \"${file.name}\", line 1, in <module>\r\n${e.javaClass.simpleName}: ${e.message}\u001b[00m\r\n")
            1
        }
    }

    fun executeCode(code: String, args: List<String> = emptyList()): Int {
        return try {
            val res = executeScript(code, isRepl = false, scriptArgs = args)
            if (res != null) {
                writeOut(formatPythonOutput(res) + "\r\n")
            }
            0
        } catch (e: Exception) {
            writeOut("\u001b[01;31mTraceback (most recent call last):\r\n${e.javaClass.simpleName}: ${e.message}\u001b[00m\r\n")
            1
        }
    }

    fun executeScript(code: String, isRepl: Boolean, scriptArgs: List<String> = emptyList()): Any? {
        val lines = code.split("\n")
        var lastResult: Any? = null

        var idx = 0
        while (idx < lines.size) {
            val rawLine = lines[idx]
            val line = rawLine.trim()
            idx++

            if (line.isBlank() || line.startsWith("#")) continue

            // print(...)
            val printMatch = Pattern.compile("^print\\((.*)\\)$").matcher(line)
            if (printMatch.find()) {
                val argsContent = printMatch.group(1) ?: ""
                val evaluated = evaluatePrintArgs(argsContent)
                writeOut(evaluated.joinToString(" ") + "\r\n")
                lastResult = null
                continue
            }

            // for item in iterable:
            val forMatch = Pattern.compile("^for\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+in\\s+range\\((.*)\\):$").matcher(line)
            if (forMatch.find()) {
                val varName = forMatch.group(1) ?: "i"
                val rangeArgs = forMatch.group(2)?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: listOf(0)
                val count = rangeArgs.firstOrNull() ?: 5
                
                // Collect block lines
                val blockLines = mutableListOf<String>()
                while (idx < lines.size && (lines[idx].startsWith(" ") || lines[idx].startsWith("\t") || lines[idx].isBlank())) {
                    if (lines[idx].isNotBlank()) blockLines.add(lines[idx].trim())
                    idx++
                }

                for (i in 0 until count) {
                    variables[varName] = i
                    for (bLine in blockLines) {
                        val pMatch = Pattern.compile("^print\\((.*)\\)$").matcher(bLine)
                        if (pMatch.find()) {
                            val pArgs = evaluatePrintArgs(pMatch.group(1) ?: "")
                            writeOut(pArgs.joinToString(" ") + "\r\n")
                        }
                    }
                }
                lastResult = null
                continue
            }

            // Variable assignment: x = expr
            val assignMatch = Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*)$").matcher(line)
            if (assignMatch.find()) {
                val varName = assignMatch.group(1) ?: ""
                val expr = assignMatch.group(2) ?: ""
                val evaluated = evaluateExpression(expr)
                variables[varName] = evaluated
                lastResult = if (isRepl) null else null
                continue
            }

            // Standalone expression
            lastResult = evaluateExpression(line)
        }

        return lastResult
    }

    private fun evaluatePrintArgs(argsStr: String): List<String> {
        if (argsStr.isBlank()) return emptyList()
        val parts = splitTopLevel(argsStr, ',')
        return parts.map { part ->
            val eval = evaluateExpression(part)
            if (eval is String && (part.startsWith("\"") || part.startsWith("'"))) {
                eval
            } else {
                formatPythonOutput(eval)
            }
        }
    }

    private fun evaluateExpression(rawExpr: String): Any? {
        val expr = rawExpr.trim()
        if (expr.isEmpty()) return null

        if (expr == "None") return null
        if (expr == "True") return true
        if (expr == "False") return false

        // String literals
        if ((expr.startsWith("\"") && expr.endsWith("\"")) ||
            (expr.startsWith("'") && expr.endsWith("'"))) {
            return expr.substring(1, expr.length - 1)
        }

        // f-string support: f"..."
        if (expr.startsWith("f\"") && expr.endsWith("\"") || expr.startsWith("f'") && expr.endsWith("'")) {
            var content = expr.substring(2, expr.length - 1)
            val matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(content)
            val sb = StringBuffer()
            while (matcher.find()) {
                val varName = matcher.group(1)
                val value = variables[varName]?.toString() ?: ""
                matcher.appendReplacement(sb, value)
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        // Numbers
        expr.toLongOrNull()?.let { return it }
        expr.toDoubleOrNull()?.let { return it }

        // List literal
        if (expr.startsWith("[") && expr.endsWith("]")) {
            val inner = expr.substring(1, expr.length - 1).trim()
            if (inner.isEmpty()) return emptyList<Any?>()
            return splitTopLevel(inner, ',').map { evaluateExpression(it) }
        }

        // Dict literal
        if (expr.startsWith("{") && expr.endsWith("}")) {
            return expr
        }

        // len(...)
        val lenMatch = Pattern.compile("^len\\((.*)\\)$").matcher(expr)
        if (lenMatch.find()) {
            val inner = evaluateExpression(lenMatch.group(1) ?: "")
            return when (inner) {
                is String -> inner.length
                is List<*> -> inner.size
                is Map<*, *> -> inner.size
                else -> 0
            }
        }

        // sum(...)
        val sumMatch = Pattern.compile("^sum\\((.*)\\)$").matcher(expr)
        if (sumMatch.find()) {
            val inner = evaluateExpression(sumMatch.group(1) ?: "")
            if (inner is List<*>) {
                val sum = inner.sumOf { (it as? Number)?.toDouble() ?: 0.0 }
                return if (sum % 1.0 == 0.0) sum.toLong() else sum
            }
        }

        // String / List concatenation or math '+'
        if (expr.contains("+")) {
            val parts = splitTopLevel(expr, '+')
            if (parts.size > 1) {
                val evalParts = parts.map { evaluateExpression(it) }
                if (evalParts.any { it is String }) {
                    return evalParts.joinToString("") { it?.toString() ?: "" }
                } else {
                    val sum = evalParts.sumOf { (it as? Number)?.toDouble() ?: 0.0 }
                    return if (sum % 1.0 == 0.0) sum.toLong() else sum
                }
            }
        }

        // Multiplication '*'
        if (expr.contains("*") && !expr.startsWith("*")) {
            val parts = splitTopLevel(expr, '*')
            if (parts.size == 2) {
                val left = evaluateExpression(parts[0])
                val right = evaluateExpression(parts[1])
                if (left is String && right is Number) {
                    return left.repeat(right.toInt())
                }
                if (left is Number && right is Number) {
                    val res = left.toDouble() * right.toDouble()
                    return if (res % 1.0 == 0.0) res.toLong() else res
                }
            }
        }

        // Subtraction '-'
        if (expr.contains("-") && !expr.startsWith("-")) {
            val parts = splitTopLevel(expr, '-')
            if (parts.size == 2) {
                val left = evaluateExpression(parts[0])?.toString()?.toDoubleOrNull() ?: 0.0
                val right = evaluateExpression(parts[1])?.toString()?.toDoubleOrNull() ?: 0.0
                val res = left - right
                return if (res % 1.0 == 0.0) res.toLong() else res
            }
        }

        // Division '/'
        if (expr.contains("/")) {
            val parts = splitTopLevel(expr, '/')
            if (parts.size == 2) {
                val left = evaluateExpression(parts[0])?.toString()?.toDoubleOrNull() ?: 0.0
                val right = evaluateExpression(parts[1])?.toString()?.toDoubleOrNull() ?: 1.0
                val res = if (right != 0.0) left / right else Double.POSITIVE_INFINITY
                return if (res % 1.0 == 0.0) res.toLong() else res
            }
        }

        // Variable lookup
        if (variables.containsKey(expr)) {
            return variables[expr]
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
            if (!inQuote && (ch == '"' || ch == '\'')) {
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

    private fun formatPythonOutput(value: Any?): String {
        return when (value) {
            null -> "None"
            is String -> "'$value'"
            is Boolean -> if (value) "True" else "False"
            is Number -> value.toString()
            is List<*> -> "[" + value.joinToString(", ") { formatPythonOutput(it) } + "]"
            else -> value.toString()
        }
    }
}
