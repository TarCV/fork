/*
 * Copyright 2015 Shazam Entertainment Limited
 * Derivative work is Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.suite;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.shazam.fork.runner.IRemoteAndroidTestRunnerFactory;
import com.shazam.fork.runner.TestRun;
import com.shazam.fork.runner.TestRunParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

class TestListingRun implements TestRun {
    private static final Logger logger = LoggerFactory.getLogger(TestListingRun.class);
    private final String poolName;
    private final TestRunParameters testRunParameters;
    private final List<ITestRunListener> testRunListeners;
    private final IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory;

    public TestListingRun(String poolName,
                          TestRunParameters testRunParameters,
                          List<ITestRunListener> testRunListeners,
                          IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory)
    {
        this.poolName = poolName;
        this.testRunParameters = testRunParameters;
        this.testRunListeners = testRunListeners;
        this.remoteAndroidTestRunnerFactory = remoteAndroidTestRunnerFactory;
    }

    @Override
    public void execute() {
        IDevice device = testRunParameters.getDeviceInterface();

		RemoteAndroidTestRunner runner =
				remoteAndroidTestRunnerFactory.createRemoteAndroidTestRunner(testRunParameters.getTestPackage(), testRunParameters.getTestRunner(), device);

        runner.setRunName(poolName);
        runner.setMaxtimeToOutputResponse(testRunParameters.getTestOutputTimeout());

        runner.addBooleanArg("log", true);
        try {
            runner.run(testRunListeners);
        } catch (ShellCommandUnresponsiveException | TimeoutException e) {
            logger.warn("Test runner got stuck and test list collection was interrupeted. " +
                    " Depending on number of available devices some tests will not be run." +
                    " You can increase the timeout in settings if it's too strict");
        } catch (AdbCommandRejectedException | IOException e) {
            throw new RuntimeException("Error while getting list of testcases from the test runner", e);
        }
    }
}
