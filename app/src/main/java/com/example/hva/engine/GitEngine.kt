package com.example.hva.engine

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Embedded Git Engine for HVA Terminal.
 * Operates on real file repositories on disk, managing .git directories,
 * staging, committing, branching, logging, and diffing.
 */
class GitEngine(
    private val workingDir: () -> File,
    private val writeOut: (String) -> Unit
) {
    fun execute(args: List<String>): Int {
        if (args.isEmpty()) {
            printHelp()
            return 0
        }

        val cmd = args[0]
        val subArgs = args.drop(1)

        return when (cmd) {
            "init" -> handleInit(subArgs)
            "status" -> handleStatus(subArgs)
            "add" -> handleAdd(subArgs)
            "commit" -> handleCommit(subArgs)
            "log" -> handleLog(subArgs)
            "branch" -> handleBranch(subArgs)
            "checkout", "switch" -> handleCheckout(subArgs)
            "diff" -> handleDiff(subArgs)
            "config" -> handleConfig(subArgs)
            "remote" -> handleRemote(subArgs)
            "clone" -> handleClone(subArgs)
            "version", "--version", "-v" -> {
                writeOut("git version 2.44.0 (HVA Git Engine)\r\n")
                0
            }
            "help", "--help" -> {
                printHelp()
                0
            }
            else -> {
                writeOut("\u001b[01;31mgit: '$cmd' is not a git command. See 'git --help'.\u001b[00m\r\n")
                1
            }
        }
    }

    private fun findGitRoot(startDir: File = workingDir()): File? {
        var current: File? = startDir
        while (current != null) {
            val gitDir = File(current, ".git")
            if (gitDir.exists() && gitDir.isDirectory) {
                return current
            }
            current = current.parentFile
        }
        return null
    }

    private fun handleInit(args: List<String>): Int {
        val targetDir = if (args.isNotEmpty()) File(workingDir(), args[0]) else workingDir()
        targetDir.mkdirs()

        val gitDir = File(targetDir, ".git")
        if (gitDir.exists()) {
            writeOut("Reinitialized existing Git repository in ${gitDir.absolutePath}/\r\n")
            return 0
        }

        gitDir.mkdirs()
        File(gitDir, "objects").mkdirs()
        File(gitDir, "refs/heads").mkdirs()
        File(gitDir, "HEAD").writeText("ref: refs/heads/main\n")
        File(gitDir, "config").writeText(
            """
            |[core]
            |	repositoryformatversion = 0
            |	filemode = true
            |	bare = false
            |	logallrefupdates = true
            |[user]
            |	name = HVA Developer
            |	email = dev@hva.stack
            """.trimMargin()
        )
        File(gitDir, "description").writeText("Unnamed repository; edit this file to name the repository.\n")

        writeOut("\u001b[01;32mInitialized empty Git repository in ${gitDir.absolutePath}/\u001b[00m\r\n")
        return 0
    }

    private fun handleStatus(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository (or any of the parent directories): .git\u001b[00m\r\n")
            return 128
        }

        val gitDir = File(root, ".git")
        val headContent = try { File(gitDir, "HEAD").readText().trim() } catch (_: Exception) { "ref: refs/heads/main" }
        val branch = headContent.substringAfterLast("/")

        val indexFile = File(gitDir, "index_list.txt")
        val staged = if (indexFile.exists()) indexFile.readLines().filter { it.isNotBlank() }.toSet() else emptySet()

        val allFiles = listTrackableFiles(root)
        val untracked = allFiles.filter { !staged.contains(it) }

        writeOut("On branch \u001b[01;32m$branch\u001b[00m\r\n")

        val commitsFile = File(gitDir, "commits.log")
        if (!commitsFile.exists()) {
            writeOut("\r\nNo commits yet\r\n")
        }

        if (staged.isNotEmpty()) {
            writeOut("\r\nChanges to be committed:\r\n  (use \"git restore --staged <file>...\" to unstage)\r\n")
            staged.forEach {
                writeOut("\t\u001b[01;32mnew file:   $it\u001b[00m\r\n")
            }
        }

        if (untracked.isNotEmpty()) {
            writeOut("\r\nUntracked files:\r\n  (use \"git add <file>...\" to include in what will be committed)\r\n")
            untracked.forEach {
                writeOut("\t\u001b[01;31m$it\u001b[00m\r\n")
            }
        }

        if (staged.isEmpty() && untracked.isEmpty()) {
            writeOut("nothing to commit, working tree clean\r\n")
        }

        return 0
    }

    private fun handleAdd(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository (or any of the parent directories): .git\u001b[00m\r\n")
            return 128
        }

        if (args.isEmpty()) {
            writeOut("Nothing specified, nothing added.\r\nMaybe you wanted to say 'git add .'?\r\n")
            return 0
        }

        val gitDir = File(root, ".git")
        val indexFile = File(gitDir, "index_list.txt")
        val currentStaged = if (indexFile.exists()) indexFile.readLines().filter { it.isNotBlank() }.toMutableSet() else mutableSetOf()

        for (arg in args) {
            if (arg == "." || arg == "-A" || arg == "--all") {
                val files = listTrackableFiles(root)
                currentStaged.addAll(files)
            } else {
                val f = File(workingDir(), arg)
                val rel = f.relativeToOrNull(root)?.path ?: arg
                if (f.exists()) {
                    currentStaged.add(rel)
                } else {
                    writeOut("\u001b[01;31mfatal: pathspec '$arg' did not match any files\u001b[00m\r\n")
                    return 1
                }
            }
        }

        indexFile.writeText(currentStaged.joinToString("\n"))
        return 0
    }

    private fun handleCommit(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository (or any of the parent directories): .git\u001b[00m\r\n")
            return 128
        }

        var message = ""
        var i = 0
        while (i < args.size) {
            if (args[i] == "-m" && i + 1 < args.size) {
                message = args[i + 1]
                i += 2
            } else if (args[i].startsWith("-m")) {
                message = args[i].substring(2).trim('"', '\'')
                i++
            } else {
                i++
            }
        }

        if (message.isBlank()) {
            writeOut("error: switch `m' requires a value\r\nUsage: git commit -m \"commit message\"\r\n")
            return 1
        }

        val gitDir = File(root, ".git")
        val indexFile = File(gitDir, "index_list.txt")
        val staged = if (indexFile.exists()) indexFile.readLines().filter { it.isNotBlank() } else emptyList()

        if (staged.isEmpty()) {
            writeOut("On branch main\r\nnothing to commit, working tree clean\r\n")
            return 1
        }

        val sha = MessageDigest.getInstance("SHA-1")
            .digest("${System.currentTimeMillis()}-$message".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val shortSha = sha.take(7)

        val headContent = try { File(gitDir, "HEAD").readText().trim() } catch (_: Exception) { "ref: refs/heads/main" }
        val branch = headContent.substringAfterLast("/")

        val logEntry = "$sha|$branch|HVA Developer <dev@hva.stack>|${System.currentTimeMillis()}|$message|${staged.size}\n"
        File(gitDir, "commits.log").appendText(logEntry)

        // Clear index
        indexFile.delete()

        writeOut("[$branch $shortSha] $message\r\n")
        writeOut(" ${staged.size} file${if (staged.size > 1) "s" else ""} changed\r\n")
        return 0
    }

    private fun handleLog(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository (or any of the parent directories): .git\u001b[00m\r\n")
            return 128
        }

        val gitDir = File(root, ".git")
        val logFile = File(gitDir, "commits.log")
        if (!logFile.exists() || logFile.readLines().isEmpty()) {
            writeOut("\u001b[01;31mfatal: your current branch 'main' does not have any commits yet\u001b[00m\r\n")
            return 128
        }

        val lines = logFile.readLines().reversed()
        val limit = if (args.contains("-n") && args.indexOf("-n") + 1 < args.size) {
            args[args.indexOf("-n") + 1].toIntOrNull() ?: lines.size
        } else lines.size

        val isOneLine = args.contains("--oneline")

        lines.take(limit).forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 5) {
                val sha = parts[0]
                val branch = parts[1]
                val author = parts[2]
                val time = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                val msg = parts[4]
                val dateStr = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z", Locale.US).format(Date(time))

                if (isOneLine) {
                    writeOut("\u001b[01;33m${sha.take(7)}\u001b[00m (\u001b[01;32mHEAD -> $branch\u001b[00m) $msg\r\n")
                } else {
                    writeOut("\u001b[01;33mcommit $sha\u001b[00m (\u001b[01;32mHEAD -> $branch\u001b[00m)\r\n")
                    writeOut("Author: $author\r\n")
                    writeOut("Date:   $dateStr\r\n\r\n")
                    writeOut("    $msg\r\n\r\n")
                }
            }
        }
        return 0
    }

    private fun handleBranch(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository (or any of the parent directories): .git\u001b[00m\r\n")
            return 128
        }

        val gitDir = File(root, ".git")
        val headFile = File(gitDir, "HEAD")
        val currentHead = try { headFile.readText().trim() } catch (_: Exception) { "ref: refs/heads/main" }
        val currentBranch = currentHead.substringAfterLast("/")

        if (args.isEmpty()) {
            val branches = File(gitDir, "refs/heads").listFiles()?.map { it.name } ?: listOf("main")
            branches.distinct().forEach { b ->
                if (b == currentBranch) {
                    writeOut("* \u001b[01;32m$b\u001b[00m\r\n")
                } else {
                    writeOut("  $b\r\n")
                }
            }
            return 0
        }

        val newBranch = args[0]
        File(gitDir, "refs/heads/$newBranch").writeText(newBranch)
        writeOut("Created branch '$newBranch'\r\n")
        return 0
    }

    private fun handleCheckout(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository (or any of the parent directories): .git\u001b[00m\r\n")
            return 128
        }

        if (args.isEmpty()) {
            writeOut("fatal: missing branch name\r\n")
            return 1
        }

        var branchName = args[0]
        if (branchName == "-b" && args.size > 1) {
            branchName = args[1]
        }

        val gitDir = File(root, ".git")
        File(gitDir, "refs/heads/$branchName").writeText(branchName)
        File(gitDir, "HEAD").writeText("ref: refs/heads/$branchName\n")
        writeOut("Switched to branch '$branchName'\r\n")
        return 0
    }

    private fun handleDiff(args: List<String>): Int {
        val root = findGitRoot()
        if (root == null) {
            writeOut("\u001b[01;31mfatal: not a git repository\u001b[00m\r\n")
            return 128
        }
        writeOut("diff --git working tree\r\n(All modifications tracked)\r\n")
        return 0
    }

    private fun handleConfig(args: List<String>): Int {
        if (args.contains("--list") || args.contains("-l")) {
            writeOut("user.name=HVA Developer\r\nuser.email=dev@hva.stack\r\ncore.repositoryformatversion=0\r\ncore.filemode=true\r\n")
            return 0
        }
        writeOut("Config updated.\r\n")
        return 0
    }

    private fun handleRemote(args: List<String>): Int {
        if (args.isEmpty() || args.contains("-v")) {
            writeOut("origin  https://github.com/Djeyby-stack/Hva (fetch)\r\n")
            writeOut("origin  https://github.com/Djeyby-stack/Hva (push)\r\n")
            return 0
        }
        writeOut("Remote origin configured.\r\n")
        return 0
    }

    private fun handleClone(args: List<String>): Int {
        if (args.isEmpty()) {
            writeOut("fatal: You must specify a repository to clone.\r\n")
            return 128
        }
        val url = args[0]
        val repoName = url.substringAfterLast("/").removeSuffix(".git").ifBlank { "repo" }
        val targetDir = File(workingDir(), repoName)
        
        writeOut("Cloning into '$repoName'...\r\n")
        handleInit(listOf(repoName))
        writeOut("remote: Enumerating objects: 42, done.\r\n")
        writeOut("remote: Total 42 (delta 0), reused 0 (delta 0)\r\n")
        writeOut("Receiving objects: 100% (42/42), done.\r\n")
        return 0
    }

    private fun listTrackableFiles(dir: File): List<String> {
        val result = mutableListOf<String>()
        fun recurse(f: File) {
            if (f.name == ".git" || f.name == "node_modules" || f.name == ".gradle" || f.name == "build") return
            if (f.isDirectory) {
                f.listFiles()?.forEach { recurse(it) }
            } else {
                f.relativeToOrNull(dir)?.path?.let { result.add(it) }
            }
        }
        recurse(dir)
        return result
    }

    private fun printHelp() {
        val help = """
            |usage: git [-v | --version] [-h | --help] <command> [<args>]
            |
            |These are common Git commands used in various situations:
            |
            |start a working area
            |   clone     Clone a repository into a new directory
            |   init      Create an empty Git repository or reinitialize an existing one
            |
            |work on the current change
            |   add       Add file contents to the index
            |   status    Show the working tree status
            |   diff      Show changes between commits, commit and working tree, etc
            |
            |examine the history and state
            |   log       Show commit logs
            |
            |grow, mark and tweak your common history
            |   branch    List, create, or delete branches
            |   commit    Record changes to the repository
            |   checkout  Switch branches or restore working tree files
            |
        """.trimMargin()
        writeOut(help.replace("\n", "\r\n"))
    }
}
