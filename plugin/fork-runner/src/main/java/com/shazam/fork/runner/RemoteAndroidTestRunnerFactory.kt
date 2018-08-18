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

interface IRemoteAndroidTestRunnerFactory {
    fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner
}

class RemoteAndroidTestRunnerFactory : IRemoteAndroidTestRunnerFactory {
    override fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner {
        return RemoteAndroidTestRunner(
                testPackage,
                testRunner,
                device)
    }
}

class TestAndroidTestRunnerFactory : IRemoteAndroidTestRunnerFactory {
    override fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner {
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
                        listeners.testRunStarted("emulators", 7)
                        listeners.fireTest("com.github.tarcv.test.NormalTest#test")
                        listeners.fireTest("com.github.tarcv.test.ParameterizedNamedTest#test[param = 1]")
                        listeners.fireTest("com.github.tarcv.test.ParameterizedNamedTest#test[param = 2]")
                        listeners.fireTest("com.github.tarcv.test.ParameterizedNamedTest#test[param = 3]")
                        listeners.fireTest("com.github.tarcv.test.ParameterizedTest#test[0]")
                        listeners.fireTest("com.github.tarcv.test.ParameterizedTest#test[1]")
                        listeners.fireTest("com.github.tarcv.test.ParameterizedTest#test[2]")
                        listeners.testRunEnded(1234, emptyMap())
                    }
                    in testCaseCommandPattern -> {
                        val (testMethod, testClass) =
                                testCaseCommandPattern.matchEntire(command)?.destructured
                                        ?: throw IllegalStateException()
                        listeners.testRunStarted("emulators", 1)
                        listeners.fireTest("$testClass#$testMethod", 1234)
                        listeners.testRunEnded(1234, emptyMap())
                    }
                    else -> throw IllegalStateException("Unexpected command: $command")
                }
            }
        }
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
        val logOnlyCommandPattern =
                ("am\\s+instrument -w -r" +
                        " -e log true" +
                        """\s+$expectedTestPackage\/$expectedTestRunner""")
                        .replace(".", "\\.")
                        .replace(" -", "\\s+-")
                        .toRegex()
        val testCaseCommandPattern =
                ("am\\s+instrument -w -r" +
                        " -e filterMethod '()'" +
                        " -e filter com.shazam.fork.ondevice.ClassMethodFilter" +
                        " -e filterClass '()'" +
                        """\s+$expectedTestPackage\/$expectedTestRunner""")
                        .replace(".", "\\.")
                        .replace(" -", "\\s+-")
                        .replace("()", "(.+)")
                        .toRegex()
    }
}

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