/*
 * Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.shazam.fork.runner

import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ddmlib.testrunner.TestIdentifier
import com.shazam.fork.Configuration
import com.shazam.fork.ForkConfiguration.ForkIntegrationTestRunType.STUB_PARALLEL_TESTRUN
import com.shazam.fork.utils.DdmsUtils
import com.shazam.fork.utils.DdmsUtils.unescapeInstrumentationArg
import java.util.*

interface IRemoteAndroidTestRunnerFactory {
    fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner
    fun properlyAddInstrumentationArg(runner: RemoteAndroidTestRunner, name: String, value: String)
}

fun androidTestRunnerFactory(configuration: Configuration): IRemoteAndroidTestRunnerFactory {
    return if (configuration.forkIntegrationTestRunType == STUB_PARALLEL_TESTRUN) {
        TestAndroidTestRunnerFactory()
    } else {
        RemoteAndroidTestRunnerFactory()
    }
}

private class RemoteAndroidTestRunnerFactory : IRemoteAndroidTestRunnerFactory {
    override fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner {
        return RemoteAndroidTestRunner(
                testPackage,
                testRunner,
                device)
    }

    override fun properlyAddInstrumentationArg(runner: RemoteAndroidTestRunner, name: String, value: String) {
        DdmsUtils.properlyAddInstrumentationArg(runner, name, value)
    }
}

private class TestAndroidTestRunnerFactory : IRemoteAndroidTestRunnerFactory {
    private val devices = Collections.synchronizedMap(HashMap<IDevice, Int>()) // to identify first/second device

    override fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner {
        synchronized(devices) {
            devices.computeIfAbsent(device) {
                devices.size
            }
        }

        return object : RemoteAndroidTestRunner(
                testPackage,
                testRunner,
                device) {
            override fun run(listenersCollection: Collection<ITestRunListener>) {
                // Do not call super.run to avoid compile errors cause by SDK tools version incompatibilities
                device.executeShellCommand(amInstrumentCommand, NullOutputReceiver())
                stubbedRun(listenersCollection)
            }

            private fun stubbedRun(listenersCollection: Collection<ITestRunListener>) {
                val listeners = BroadcastingListener(listenersCollection)

                operator fun Regex.contains(other: String): Boolean = this.matches(other)
                val command = amInstrumentCommand
                when (command) {
                    in logOnlyCommandPattern -> {
                        listeners.testRunStarted("emulators", testCases.size)
                        testCases
                                .map {
                                    if (it.contains("DeviceNOnly")) {
                                        val deviceIndex = devices[device] as Int
                                        it.replace(
                                                "DeviceNOnly",
                                                "Device${1 + deviceIndex}Only")
                                    } else {
                                        it
                                    }
                                }
                                .forEach {
                                    listeners.fireTest(it)
                                }
                        listeners.testRunEnded(100, emptyMap())
                    }
                    in testCaseCommandPattern -> {
                        // TODO: assert that the current testcase is in the testcases list
                        val (testMethod, testClass) =
                                testCaseCommandPattern.matchEntire(command)
                                        ?.groupValues
                                        ?.drop(1) // group 0 is the entire match
                                        ?.map { unescapeInstrumentationArg(it) }
                                        ?: throw IllegalStateException()
                        listeners.testRunStarted("emulators", 1)
                        listeners.fireTest("$testClass#$testMethod", functionalTestTestcaseDuration)
                        listeners.testRunEnded(functionalTestTestcaseDuration, emptyMap())
                    }
                    else -> throw IllegalStateException("Unexpected command: $command")
                }
            }
        }
    }

    override fun properlyAddInstrumentationArg(runner: RemoteAndroidTestRunner, name: String, value: String) {
        // proper escaping really complicates RemoteAndroidTestRunner#stubbedRun implementation
        //  so skip it here
        runner.addInstrumentationArg(name, value)
    }

    private fun ITestRunListener.fireTest(testCase: String, delayMillis: Long = 0) {
        val (className, testName) = testCase.split("#", limit = 2)
        testStarted(TestIdentifier(className, testName))
        if (delayMillis > 0) {
            Thread.sleep(delayMillis)
        }
        testEnded(TestIdentifier(className, testName), emptyMap())
    }

    companion object {
        private const val expectedTestPackage = "com.github.tarcv.forktestapp.test"
        private const val expectedTestRunner = "android.support.test.runner.AndroidJUnitRunner"
        private val logOnlyCommandPattern =
                ("am\\s+instrument -w -r" +
                        " -e log true" +
                        """\s+$expectedTestPackage\/$expectedTestRunner""")
                        .replace(".", "\\.")
                        .replace(" -", "\\s+-")
                        .toRegex()
        private val testCaseCommandPattern =
                ("am\\s+instrument -w -r" +
                        " -e filterMethod ()" +
                        " -e filter com.shazam.fork.ondevice.ClassMethodFilter" +
                        " -e filterClass ()" +
                        """\s+$expectedTestPackage\/$expectedTestRunner""")
                        .replace(".", "\\.")
                        .replace(" -", "\\s+-")
                        .replace("()", "(.+?)")
                        .toRegex()
        private val testCases = listOf("""com.github.tarcv.test.DangerousNamesTest#test[param = """ + '$' + """THIS_IS_NOT_A_VAR]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param =        1       ]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = #######]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = !!!!!!!]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = ''''''']""",
                "com.github.tarcv.test.DangerousNamesTest#test[param = \"\"\"\"\"\"\"\"]",
                """com.github.tarcv.test.DangerousNamesTest#test[param = ()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = * *.* * *.* * *.* * *.* * *.* * *.* * *.* * *.* *]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = . .. . .. . .. . .. . .. . .. . .. . .. . .. . ..]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = |&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%]""",
                """com.github.tarcv.test.DangerousNamesTest#test[param = ; function {}; while {}; for {}; do {}; done {}; exit]""",
                """com.github.tarcv.test.NormalTest#test""",
                """com.github.tarcv.test.DeviceNOnlyTest#test[param = 1]""",
                """com.github.tarcv.test.DeviceNOnlyTest#test[param = 2]""",
                """com.github.tarcv.test.DeviceNOnlyTest#test[param = 3]""",
                """com.github.tarcv.test.ParameterizedNamedTest#test[param = 1]""",
                """com.github.tarcv.test.ParameterizedNamedTest#test[param = 2]""",
                """com.github.tarcv.test.ParameterizedNamedTest#test[param = 3]""",
                """com.github.tarcv.test.ParameterizedTest#test[0]""",
                """com.github.tarcv.test.ParameterizedTest#test[1]""",
                """com.github.tarcv.test.ParameterizedTest#test[2]""",
                """com.github.tarcv.test.ResetPrefsTest#testPrefsAreClearedBetweenTests[0]""",
                """com.github.tarcv.test.ResetPrefsTest#testPrefsAreClearedBetweenTests[1]""",
                """com.github.tarcv.test.ResetPrefsTest#testPrefsAreClearedBetweenTests[2]""",
                """com.github.tarcv.test.ResetPrefsTest#testPrefsAreClearedBetweenTests[3]"""
        )
    }
}
const val functionalTestTestcaseDuration = 2345L

private class BroadcastingListener(
        private val targetListeners: Collection<ITestRunListener>
) : ITestRunListener {
    override fun testRunStarted(runName: String?, testCount: Int) {
        targetListeners.forEach {
            it.testRunStarted(runName, testCount)
        }
    }

    override fun testStarted(test: TestIdentifier?) {
        targetListeners.forEach {
            it.testStarted(test)
        }
    }

    override fun testAssumptionFailure(test: TestIdentifier?, trace: String?) {
        targetListeners.forEach {
            it.testAssumptionFailure(test, trace)
        }
    }

    override fun testRunStopped(elapsedTime: Long) {
        targetListeners.forEach {
            it.testRunStopped(elapsedTime)
        }
    }

    override fun testFailed(test: TestIdentifier?, trace: String?) {
        targetListeners.forEach {
            it.testFailed(test, trace)
        }
    }

    override fun testEnded(test: TestIdentifier?, testMetrics: Map<String, String>?) {
        targetListeners.forEach {
            it.testEnded(test, testMetrics)
        }
    }

    override fun testIgnored(test: TestIdentifier?) {
        targetListeners.forEach {
            it.testIgnored(test)
        }
    }

    override fun testRunFailed(errorMessage: String?) {
        targetListeners.forEach {
            it.testRunFailed(errorMessage)
        }
    }

    override fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>?) {
        targetListeners.forEach {
            it.testRunEnded(elapsedTime, runMetrics)
        }
    }

}