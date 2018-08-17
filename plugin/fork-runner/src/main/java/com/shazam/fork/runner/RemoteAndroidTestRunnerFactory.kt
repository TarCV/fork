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
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner

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
            override fun run(listeners: MutableCollection<ITestRunListener>) {
                // TODO fire recorded events matching current getArgsCommand
            }
        }
    }
}