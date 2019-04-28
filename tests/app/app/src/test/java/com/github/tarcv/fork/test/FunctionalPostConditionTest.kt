package com.github.tarcv.fork.test

import com.github.tarcv.test.Config.PACKAGE
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class FunctionalPostConditionTest {
    private val packageForRegex = PACKAGE.replace(".", """\.""")

    @Test
    fun testAllTestcasesExecutedExactlyOnce() {
        val simplifiedResults = getSimplifiedResults()

        simplifiedResults.fold(HashSet<String>()) { acc, result ->
            assert(acc.add(result.first)) { "All tests should be executed only once (${result.first} was executed more times)" }
            acc
        }

        assert(simplifiedResults.size == 11+2+2+1+8+8+2+4) { "All tests should be executed" }
    }

    @Test
    fun testNumberedParameterizedTestExecutedCorrectly() {
        doAssertionsForParameterizedTests(
                """$packageForRegex\.ParameterizedTest#test\[\d+]""".toRegex(), 8)
    }

    @Test
    fun testNamedParameterizedTestExecutedCorrectly() {
        doAssertionsForParameterizedTests(
                """$packageForRegex\.ParameterizedNamedTest#test\[\s*param = .+]""".toRegex(), 8)
    }

    @Test
    fun testDangerousNamesTestExecutedCorrectly() {
        doAssertionsForParameterizedTests(
                """$packageForRegex\.DangerousNamesTest#test\[\s*param = .+]""".toRegex(), 11)
    }

    @Test
    fun testVideoRecorderIsCalledWithGoodFilename() {
        Assume.assumeTrue("Executing on a *NIX system", File(shellBinary).exists())
        Assume.assumeTrue("Running tests with stubbed ADB",
                System.getenv("CI_STUBBED")?.toBoolean() ?: false)

        readAdbLogLines { lines ->
            lines.filter { it.contains("[START SCREEN RECORDER] ") }
                    .map {
                        """.+\[START SCREEN RECORDER] (.+?),\{.+"""
                                .toRegex()
                                .matchEntire(it)
                                ?.groupValues
                                ?.get(1)
                                ?: throw AssertionError("Unexpected screenrecord line ${it}")
                    }
                    .forEach { actualPath ->
                        this@FunctionalPostConditionTest.assertNotMangledByShell(actualPath)
                    }
        }
    }

    @Test
    fun testSuiteIsExecutedForLogWithGoodArgument() {
        Assume.assumeTrue("Executing on a *NIX system", File(shellBinary).exists())
        Assume.assumeTrue("Running tests with stubbed ADB",
                System.getenv("CI_STUBBED")?.toBoolean() ?: false)

        readAdbLogLines { lines ->
            lines.filter { it.contains("[START SCREEN RECORDER] ") }
                    .map {
                        """.+\[START SCREEN RECORDER] (.+?),\{.+"""
                                .toRegex()
                                .matchEntire(it)
                                ?.groupValues
                                ?.get(1)
                                ?: throw AssertionError("Unexpected screenrecord line ${it}")
                    }
                    .forEach { actualPath ->
                        this@FunctionalPostConditionTest.assertNotMangledByShell(actualPath)
                    }
        }
    }

    @Test
    fun testCasesAreExecutedWithGoodArgument() {
        Assume.assumeTrue("Executing on a *NIX system", File(shellBinary).exists())
        Assume.assumeTrue("Running tests with stubbed ADB",
                System.getenv("CI_STUBBED")?.toBoolean() ?: false)

        readAdbLogLines { lines ->
            lines.filter { it.contains("[START SCREEN RECORDER] ") }
                    .map {
                        """.+\[START SCREEN RECORDER] (.+?),\{.+"""
                                .toRegex()
                                .matchEntire(it)
                                ?.groupValues
                                ?.get(1)
                                ?: throw AssertionError("Unexpected screenrecord line ${it}")
                    }
                    .forEach { actualPath ->
                        this@FunctionalPostConditionTest.assertNotMangledByShell(actualPath)
                    }
        }
    }

    private fun readAdbLogLines(block: (List<String>) -> Unit) {
        getAdbLogFiles(".").let {
            if (it.isEmpty()) {
                getAdbLogFiles("..")
            } else {
                it
            }
        }
                .also {
                    assert(it.size == 2) {
                        "there should be 2 adb log files (got ${it.size})"
                    }
                }
                .forEach { file ->
                    Files
                            .readAllLines(file.toPath())
                            .let(block)
                }
    }

    private fun assertNotMangledByShell(str: String) {
        val echoCmd = CommandLine(shellBinary)
                .addArgument("-c")
                .addArgument("echo \$1", false)
                .addArgument("--")
                .addArgument(str, false)
        val receivedPath = executeCommandWithOutput(echoCmd).trim()

        assert(str.length - receivedPath.length <= 5) {
            "Path passed to screenrecord command should not be" +
                    " mangled by the shell, test 2 (got $receivedPath)"
        }
    }

    private fun getAdbLogFiles(dirPath: String): Array<out File> {
        val currentDir = File(dirPath)
        return currentDir
                .listFiles { dir, filename ->
                    filename.toLowerCase().endsWith("_adb.log")
                            && currentDir == dir
                }
    }

}

private const val shellBinary = "/bin/sh"

private fun executeCommandWithOutput(cmd: CommandLine): String {
    val outputStream = ByteArrayOutputStream()
    val pumper = PumpStreamHandler(outputStream)
    DefaultExecutor().apply {
        setExitValue(0)
        streamHandler = pumper
        execute(cmd)
    }
    return outputStream.toString()
}

private fun doAssertionsForParameterizedTests(pattern: Regex, expectedCount: Int) {
    val simplifiedResults = getSimplifiedResults()

    val testsPerDevice = simplifiedResults
            .filter { pattern.matches(it.first) }
            .fold(HashMap<String, AtomicInteger>()) { acc, test ->
                acc
                        .computeIfAbsent(test.second) { _ -> AtomicInteger(0) }
                        .incrementAndGet()
                acc
            }
            .entries
            .toList()
    assert(testsPerDevice.isNotEmpty()) { "Parameterized tests should be executed" }
    assert(testsPerDevice.size == 2) { "Variants should be executed on exactly 2 devices (got ${testsPerDevice.size})" }
    assert(testsPerDevice[0].value.get() > 0) { "At least one parameterized test should be executed on ${testsPerDevice[0].key} device" }
    assert(testsPerDevice[1].value.get() > 0) { "At least one parameterized test should be executed on ${testsPerDevice[1].key} device" }
    assert(testsPerDevice[0].value.get() + testsPerDevice[1].value.get() == expectedCount) {
        "Exactly $expectedCount parameterized tests should be executed" +
                " (device1=${testsPerDevice[0].value.get()}, device2=${testsPerDevice[1].value.get()})"
    }
}

private fun getSimplifiedResults(): List<Pair<String, String>> {
    val poolSummaries = getSummaryData().getJSONArray("poolSummaries")
    assert(poolSummaries.length() == 1)
    val testResults = (poolSummaries[0] as JSONObject).getJSONArray("testResults")
    val simplifiedResults = testResults.map {
        val result = it as JSONObject
        val serial = result.getJSONObject("device").getString("serial")
        val testClass = result.getString("testClass")
        val testMethod = result.getString("testMethod")
        "$testClass#$testMethod" to serial
    }
    return simplifiedResults
}

private fun getSummaryData(): JSONObject {
    val summaryJsonFile = getSummaryJsonFile()
    return JSONObject(String(Files.readAllBytes(summaryJsonFile.toPath())))
}

private fun getSummaryJsonFile(): File {
    val summaryDir = File("build/reports/fork/debugAndroidTest/summary")
    val jsons = summaryDir.listFiles { file, s ->
        summaryDir == file && s.toLowerCase().endsWith(".json")
    }
    assert(jsons != null && jsons.isNotEmpty()) { "Summary json should be created" }
    return jsons[0]
}
