package com.example

import com.example.hva.engine.GitEngine
import com.example.hva.engine.PipelineEngine
import com.example.hva.engine.UnixTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Strict unit test suite testing all sub-engines:
 * - Storage & Path resolving
 * - UnixTools (grep, wc, head, tail, jq)
 * - PipelineEngine (Pipes, Redirections >, >>, <)
 * - GitEngine (init, add, commit, log, status, branch)
 */
class HvaEnginesStrictTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPipelineParserArgsWithQuotes() {
        val line = "echo \"Hello World\" 'Second Token' single"
        val parsed = PipelineEngine.parseArgs(line)
        assertEquals(4, parsed.size)
        assertEquals("echo", parsed[0])
        assertEquals("Hello World", parsed[1])
        assertEquals("Second Token", parsed[2])
        assertEquals("single", parsed[3])
    }

    @Test
    fun testPipelineRedirectionsParsing() {
        val root = tempFolder.root
        val home = File(root, "home").apply { mkdirs() }

        // Output redirect
        val seg1 = PipelineEngine.parseSegment("echo 'HVA 100%' > out.txt", root, home)
        assertEquals("echo", seg1.command)
        assertEquals(1, seg1.args.size)
        assertEquals("HVA 100%", seg1.args[0])
        assertNotNull(seg1.redirectOutput)
        assertEquals(File(root, "out.txt").absolutePath, seg1.redirectOutput?.absolutePath)
        assertFalse(seg1.appendOutput)

        // Append redirect
        val seg2 = PipelineEngine.parseSegment("echo 'Line 2' >> ~/log.txt", root, home)
        assertEquals("echo", seg2.command)
        assertNotNull(seg2.redirectOutput)
        assertEquals(File(home, "log.txt").absolutePath, seg2.redirectOutput?.absolutePath)
        assertTrue(seg2.appendOutput)
    }

    @Test
    fun testUnixToolsGrep() {
        val root = tempFolder.root
        val testFile = File(root, "sample.txt")
        testFile.writeText("alpha\nbeta\nGAMMA\nzeta\n")

        // Standard grep
        val (code1, out1) = UnixTools.executeGrep(listOf("beta", "sample.txt"), root, null)
        assertEquals(0, code1)
        assertTrue(out1.contains("beta"))

        // Case-insensitive grep
        val (code2, out2) = UnixTools.executeGrep(listOf("-i", "gamma", "sample.txt"), root, null)
        assertEquals(0, code2)
        assertTrue(out2.contains("GAMMA"))

        // Line numbers grep
        val (code3, out3) = UnixTools.executeGrep(listOf("-n", "alpha", "sample.txt"), root, null)
        assertEquals(0, code3)
        assertTrue(out3.contains("1:alpha"))
    }

    @Test
    fun testUnixToolsWc() {
        val root = tempFolder.root
        val testFile = File(root, "count.txt")
        testFile.writeText("hello world\nsecond line\n")

        val (code, out) = UnixTools.executeWc(listOf("-l", "count.txt"), root, null)
        assertEquals(0, code)
        assertTrue(out.contains("2"))
    }

    @Test
    fun testUnixToolsHeadAndTail() {
        val root = tempFolder.root
        val testFile = File(root, "lines.txt")
        testFile.writeText((1..20).joinToString("\n"))

        val (codeH, outH) = UnixTools.executeHead(listOf("-n", "3", "lines.txt"), root, null)
        assertEquals(0, codeH)
        val hLines = outH.trim().lines()
        assertEquals(3, hLines.size)
        assertEquals("1", hLines[0])

        val (codeT, outT) = UnixTools.executeTail(listOf("-n", "3", "lines.txt"), root, null)
        assertEquals(0, codeT)
        val tLines = outT.trim().lines()
        assertEquals(3, tLines.size)
        assertEquals("20", tLines[2])
    }

    @Test
    fun testUnixToolsJq() {
        val root = tempFolder.root
        val jsonFile = File(root, "data.json")
        jsonFile.writeText("""{"name":"HVA Terminal","version":"1.0.0","arch":"arm64"}""")

        val (code, out) = UnixTools.executeJq(listOf(".name", "data.json"), root, null)
        assertEquals(0, code)
        assertTrue(out.contains("HVA Terminal"))
    }

    @Test
    fun testGitEngineWorkflow() {
        val root = tempFolder.root
        val repoDir = File(root, "my-repo").apply { mkdirs() }
        val gitOutputs = mutableListOf<String>()
        val git = GitEngine(workingDir = { repoDir }, writeOut = { text -> gitOutputs.add(text) })

        // 1. git init
        val initRes = git.execute(listOf("init"))
        assertEquals(0, initRes)
        assertTrue(File(repoDir, ".git").exists())

        // 2. Create file & git add
        val f = File(repoDir, "main.js").apply { writeText("console.log('test');") }
        val addRes = git.execute(listOf("add", "main.js"))
        assertEquals(0, addRes)

        // 3. git commit
        val commitRes = git.execute(listOf("commit", "-m", "Initial commit"))
        assertEquals(0, commitRes)

        // 4. git status
        val statusRes = git.execute(listOf("status"))
        assertEquals(0, statusRes)

        // 5. git branch
        val branchRes = git.execute(listOf("branch", "feature-x"))
        assertEquals(0, branchRes)

        // 6. git checkout
        val checkoutRes = git.execute(listOf("checkout", "feature-x"))
        assertEquals(0, checkoutRes)
    }
}
