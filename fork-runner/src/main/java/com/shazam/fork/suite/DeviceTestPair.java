/*
 * Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.suite;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.model.Device;

class DeviceTestPair {
    private final Device device;
    private final TestIdentifier testIdentifier;

    DeviceTestPair(Device device, TestIdentifier testIdentifier) {
        this.device = device;
        this.testIdentifier = testIdentifier;
    }

    public Device getDevice() {
        return device;
    }

    public TestIdentifier getTestIdentifier() {
        return testIdentifier;
    }
}
