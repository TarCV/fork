/*
 * Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.suite

import com.android.ddmlib.testrunner.TestIdentifier
import com.shazam.fork.model.Device
import com.shazam.fork.model.TestCaseEvent
import com.shazam.fork.model.TestEventQueue
import java.util.*

class OneDummyTestPerDeviceQueue(val numOfDevices: Int): TestEventQueue {
    private val devices = Collections.synchronizedSet(HashSet<Device>())

    override fun size(): Int = numOfDevices - devices.size

    override fun pollForDevice(device: Device): TestCaseEvent? {
        return if (device in devices) {
            null
        } else {
            devices.add(device)
            TestCaseEvent.newTestCase(
                    TestIdentifier("", "Listing testcases"))
        }
    }

    override fun offerFromDevice(test: TestCaseEvent, device: Device) {
        TODO("not implemented")
    }

    override fun toCollection(): Collection<TestCaseEvent> {
        TODO("not implemented")
    }
}