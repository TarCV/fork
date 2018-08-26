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

package com.shazam.fork.runner;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.shazam.fork.Configuration;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestCaseEvent;
import com.shazam.fork.runner.listeners.TestRunListenersFactory;

import java.util.List;
import java.util.Queue;

import static com.shazam.fork.runner.TestRunParameters.Builder.testRunParameters;
import static com.shazam.fork.system.PermissionGrantingManager.permissionGrantingManager;

public class TestCaseRunFactory implements TestRunFactory {

    private final Configuration configuration;
    private final TestRunListenersFactory testRunListenersFactory;

    public TestCaseRunFactory(Configuration configuration, TestRunListenersFactory testRunListenersFactory) {
        this.configuration = configuration;
        this.testRunListenersFactory = testRunListenersFactory;
    }

    @Override
    public TestRun createTestRun(TestCaseEvent testCase,
                                 Device device,
                                 Pool pool,
                                 ProgressReporter progressReporter,
                                 Queue<TestCaseEvent> queueOfTestsInPool) {
        TestRunParameters testRunParameters = testRunParameters()
                .withDeviceInterface(device.getDeviceInterface())
                .withTest(testCase)
                .withTestPackage(configuration.getInstrumentationPackage())
                .withApplicationPackage(configuration.getApplicationPackage())
                .withTestRunner(configuration.getTestRunnerClass())
                .withTestSize(configuration.getTestSize())
                .withTestOutputTimeout((int) configuration.getTestOutputTimeout())
                .withCoverageEnabled(configuration.isCoverageEnabled())
                .withExcludedAnnotation(configuration.getExcludedAnnotation())
                .build();

        List<ITestRunListener> testRunListeners = testRunListenersFactory.createTestListeners(
                testCase,
                device,
                pool,
                progressReporter,
                queueOfTestsInPool);

        return new TestCaseRun(
                pool.getName(),
                testRunParameters,
                testRunListeners,
                permissionGrantingManager());
    }
}
