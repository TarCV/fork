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

import com.android.ddmlib.testrunner.ITestRunListener;
import com.shazam.fork.ForkConfiguration;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestCaseEvent;
import com.shazam.fork.runner.ProgressReporter;
import com.shazam.fork.runner.listeners.RecordingTestRunListener;
import com.shazam.fork.runner.listeners.TestRunListenersFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

class TestCollectingListenersFactory implements TestRunListenersFactory {
    private final List<TestCollectingListener> testCollectorsOut;
    private final ForkConfiguration configuration;

    TestCollectingListenersFactory(List<TestCollectingListener> testCollectorsOut, ForkConfiguration configuration) {
        this.testCollectorsOut = testCollectorsOut;
        this.configuration = configuration;
    }

    @Override
    public List<ITestRunListener> createTestListeners(TestCaseEvent testCase, Device device, Pool pool, ProgressReporter progressReporter, Queue<TestCaseEvent> testCaseEventQueue) {
        TestCollectingListener testCollector = new TestCollectingListener(device);
        testCollectorsOut.add(testCollector);

        List<ITestRunListener> testRunListeners = new ArrayList<>();
        testRunListeners.add(testCollector);

        if (configuration.getForkIntegrationTestRunType() == ForkConfiguration.ForkIntegrationTestRunType.RECORD_LISTENER_EVENTS) {
            testRunListeners.add(new RecordingTestRunListener(device, true));
        }
        return testRunListeners;
    }
}
