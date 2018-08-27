/*
 * Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.model

import java.util.*

/**
 * Queue of test case events with ability to poll events for a specific device
 */
class TestEventQueueImpl(eventCollection: Collection<TestCaseEvent>)
    : TestEventQueue {
    private val eventList: MutableList<TestCaseEvent> = Collections.synchronizedList(ArrayList(eventCollection))

    override fun size(): Int = eventList.size

    override fun offerFromDevice(test: TestCaseEvent, device: Device) {
        // TODO: option to requeue on the same device
        synchronized(eventList) {
            val numForOfferredTest = numOfSupportedDevices(test)
            val insertBefore = eventList
                    .indexOfFirst {
                        numForOfferredTest >= numOfSupportedDevices(it)
                    }
                    .takeIf { it >= 0 } ?: eventList.size
            eventList.add(insertBefore, test)
        }
    }

    private fun numOfSupportedDevices(test: TestCaseEvent): Int {
        return if (test is LimitedTestCaseEvent) {
            test.supportedDevices.size
        } else {
            Int.MAX_VALUE
        }
    }

    override fun pollForDevice(device: Device): TestCaseEvent? {
        synchronized(eventList) {
            var index = 0
            for (testCaseEvent in eventList) {
                val found = if (testCaseEvent is LimitedTestCaseEvent) {
                    testCaseEvent.isSupportedDevice(device)
                } else {
                    true
                }

                if (found) {
                    eventList.removeAt(index)
                    return testCaseEvent
                } else {
                    ++index
                }
            }
            return null
        }
    }

    override fun toCollection(): Collection<TestCaseEvent> {
        return Collections.unmodifiableCollection(ArrayList(eventList))
    }
}
