package com.shazam.fork

import com.shazam.fork.model.Device
import com.shazam.fork.model.Pool
import com.shazam.fork.model.TestCaseEvent.newTestCase
import com.shazam.fork.model.TestEventQueue
import com.shazam.fork.pooling.PoolLoader
import com.shazam.fork.pooling.StubDevice
import com.shazam.fork.runner.*
import com.shazam.fork.suite.TestSuiteLoader
import com.shazam.fork.summary.SummaryGeneratorHook
import com.shazam.fork.system.adb.Installer
import org.hamcrest.CoreMatchers.`is`
import org.jmock.Expectations
import org.jmock.auto.Mock
import org.jmock.integration.junit4.JUnitRuleMockery
import org.jmock.lib.concurrent.Synchroniser
import org.junit.Rule
import org.junit.Test
import java.util.*

class ForkRunnerTest {
    @get:Rule
    val mockery = object : JUnitRuleMockery() {
        init {
            setThreadingPolicy(Synchroniser())
        }
    }

    @JvmField
    @Mock
    var reporter: ProgressReporter? = null

    @JvmField
    @Mock
    var testRunFactory: TestRunFactory? = null

    @Test
    fun eachPoolExecutesAllTestsOnce() {
        val device1: Device = createStubDevice("device1", 25)
        val device2: Device = createStubDevice("device2", 25)
        val pool1 = Pool.Builder()
                .addDevice(device1)
                .build()
        val pool2 = Pool.Builder()
                .addDevice(device2)
                .build()

        val test1 = newTestCase("aTestMethod1", "aTestClass", false, emptyList(), emptyMap())
        val test2 = newTestCase("aTestMethod2", "aTestClass", false, emptyList(), emptyMap())

        mockery.checking(object : Expectations() {
            init {
                allowing(reporter)

                oneOf(testRunFactory)!!.createTestRun(with(`is`(test1)), with(`is`(device1)), with(`is`(pool1)),
                        with(`is`(reporter)), with(any(TestEventQueue::class.java)))
                oneOf(testRunFactory)!!.createTestRun(with(`is`(test2)), with(`is`(device1)), with(`is`(pool1)),
                        with(`is`(reporter)), with(any(TestEventQueue::class.java)))
                oneOf(testRunFactory)!!.createTestRun(with(`is`(test1)), with(`is`(device2)), with(`is`(pool2)),
                        with(`is`(reporter)), with(any(TestEventQueue::class.java)))
                oneOf(testRunFactory)!!.createTestRun(with(`is`(test2)), with(`is`(device2)), with(`is`(pool2)),
                        with(`is`(reporter)), with(any(TestEventQueue::class.java)))
            }
        })

        ForkRunner(
                PoolLoader { Arrays.asList(pool1, pool2) },
                TestSuiteLoader { Arrays.asList(test1, test2) },
                PoolTestRunnerFactory(DeviceTestRunnerFactory(stubInstaller(), testRunFactory)),
                reporter,
                object : SummaryGeneratorHook(null) {
                    override fun defineOutcome(): Boolean {
                        return true
                    }
                }
        ).run()
    }
}

private fun stubInstaller(): Installer {
    return Installer { }
}

private fun createStubDevice(serial: String, api: Int): Device {
    val manufacturer = "fork"
    val model = "TEST"
    val stubDevice = StubDevice(serial, manufacturer, model, serial, api, "",
            functionalTestTestcaseDuration)
    return stubDevice.asDevice()
}
