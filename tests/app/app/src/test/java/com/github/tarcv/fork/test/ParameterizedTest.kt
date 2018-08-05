package com.github.tarcv.fork.test

import com.github.tarcv.test.Config.PACKAGE
import org.json.JSONObject
import org.junit.Test

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class ParameterizedTest {
    private val packageForRegex = PACKAGE.replace("", """\.""")

    @Test
    fun testAllTestcasesExecutedExactlyOnce() {
        val simplifiedResults = getSimplifiedResults()

        simplifiedResults.fold(HashSet<String>()) { acc, result ->
            assert(acc.add(result.first)) { "All  tests should be executed only once" }
            acc
        }

        assert(simplifiedResults.size == 7) { "All tests should be executed" }
    }

    @Test
    fun testNumberedParameterizedTestExecutedCorrectly() {
        doAssertionsForParameterizedTests("""$packageForRegex\.ParameterizedTest#test\[\d+]""".toRegex())
    }

    @Test
    fun testNamedParameterizedTestExecutedCorrectly() {
        doAssertionsForParameterizedTests("""$packageForRegex\.ParameterizedNamedTest#test\[\s*param = .+]""".toRegex())
    }

    private fun doAssertionsForParameterizedTests(pattern: Regex) {
        val simplifiedResults = getSimplifiedResults()

        val testsPerDevice = simplifiedResults
                .filter { pattern.matches(it.first) }
                .fold(HashMap<String, AtomicInteger>()) { acc, test ->
                    acc
                            .computeIfAbsent(test.second) { _ -> AtomicInteger(0) }
                            .incrementAndGet()
                    acc
                }
                .values
                .toList()
        assert(testsPerDevice.isNotEmpty()) { "Parameterized tests should be executed" }
        assert(testsPerDevice.size == 2) { "There should be exactly 2 devices" }
        assert(testsPerDevice[0].get() > 0) { "At least one parameterized test should be executed on 1st device" }
        assert(testsPerDevice[1].get() > 0) { "At least one parameterized test should be executed on 2nd device" }
        assert(testsPerDevice[0].get() + testsPerDevice[1].get() == 3) {
            "Exactly 3 parameterized tests should be executed"
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
}
