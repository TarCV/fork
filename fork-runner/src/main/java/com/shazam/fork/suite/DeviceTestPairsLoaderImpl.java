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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.Configuration;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestCaseEvent;
import com.shazam.fork.pooling.NoDevicesForPoolException;
import com.shazam.fork.pooling.NoPoolLoaderConfiguredException;
import com.shazam.fork.pooling.PoolLoader;
import com.shazam.fork.runner.DeviceTestRunnerFactory;
import com.shazam.fork.runner.PoolTestRunnerFactory;
import com.shazam.fork.runner.ProgressReporter;
import com.shazam.fork.runner.listeners.TestRunListenersFactory;
import com.shazam.fork.system.adb.Installer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static com.shazam.fork.Utils.namedExecutor;
import static com.shazam.fork.injector.ConfigurationInjector.configuration;
import static com.shazam.fork.injector.system.InstallerInjector.installer;

public class DeviceTestPairsLoaderImpl implements DeviceTestPairsLoader {
    private final Logger logger = LoggerFactory.getLogger(DeviceTestPairsLoaderImpl.class);
    private final PoolLoader poolLoader;

    public DeviceTestPairsLoaderImpl(PoolLoader poolLoader) {
        this.poolLoader = poolLoader;
    }

    @Override
    public Stream<DeviceTestPair> askDevicesForTests() {
        ExecutorService poolExecutor = null;
        try {
            List<TestCollectingListener> testCollectors = Collections.synchronizedList(new ArrayList<>());
            Collection<Pool> pools = poolLoader.loadPools();
            int numberOfPools = pools.size();
            CountDownLatch poolCountDownLatch = new CountDownLatch(numberOfPools);
            poolExecutor = namedExecutor(numberOfPools, "PoolSuiteLoader-%d");

            TestRunListenersFactory testCollectorFactory = new TestCollectingListenersFactory(testCollectors);

            final Installer installer = installer();
            final Configuration configuration = configuration();
            TestListingRunFactory testListingRunFactory = new TestListingRunFactory(configuration, testCollectorFactory);

            DeviceTestRunnerFactory deviceTestRunnerFactory =
                    new DeviceTestRunnerFactory(
                            installer,
                            testListingRunFactory
                    );

            ArrayDeque<TestCaseEvent> dummyQueue = createDummyQueue();

            PoolTestRunnerFactory poolTestRunnerFactory = new PoolTestRunnerFactory(
                    deviceTestRunnerFactory
            );

            ProgressReporter progressReporter = new TestListingProgressTracker();
            progressReporter.start();
            for (Pool pool : pools) {
                Runnable poolTestRunner = poolTestRunnerFactory.createPoolTestRunner(pool, dummyQueue,
                        poolCountDownLatch, progressReporter);
                poolExecutor.execute(poolTestRunner);
            }
            poolCountDownLatch.await();
            progressReporter.stop();
            logger.info("Successfully loaded test cases");

            return testCollectors.stream()
                    .flatMap(testCollector -> testCollector.getTests().stream());
        } catch (NoPoolLoaderConfiguredException | NoDevicesForPoolException e) {
            // TODO: replace with concrete exception
            throw new RuntimeException("Configuring devices and pools failed." +
                    " Suites can not be read without devices", e);
        } catch (InterruptedException e) {
            // TODO: replace with concrete exception
            throw new RuntimeException("Reading suites were interrupted");
        } finally {
            if (poolExecutor != null) {
                poolExecutor.shutdown();
            }
        }
    }

    private ArrayDeque<TestCaseEvent> createDummyQueue() {
        ArrayDeque<TestCaseEvent> dummyQueue = new ArrayDeque<>();
        dummyQueue.add(TestCaseEvent.newTestCase(
                new TestIdentifier("", "Building test suite")));
        return dummyQueue;
    }
}
