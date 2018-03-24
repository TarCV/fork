package com.shazam.fork.suite;

/*
 * Copyright 2016 Shazam Entertainment Limited
 * Derivative work is Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.Configuration;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.LimitedTestCaseEvent;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestEventQueue;
import com.shazam.fork.pooling.NoDevicesForPoolException;
import com.shazam.fork.pooling.NoPoolLoaderConfiguredException;
import com.shazam.fork.pooling.PoolLoader;
import com.shazam.fork.system.adb.Installer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static com.shazam.fork.Utils.namedExecutor;
import static com.shazam.fork.injector.ConfigurationInjector.configuration;
import static com.shazam.fork.injector.system.InstallerInjector.installer;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class JUnitTestSuiteLoader implements TestSuiteLoader {
    private final Logger logger = LoggerFactory.getLogger(JUnitTestSuiteLoader.class);
    private final PoolLoader poolLoader;

    public JUnitTestSuiteLoader(
            PoolLoader poolLoader
    ) {
        this.poolLoader = poolLoader;
    }

    @Override
    public TestEventQueue loadTestSuite() throws NoTestCasesFoundException {
        Stream<DeviceTestPair> knownTestsPairs = askDevicesForTests();
        List<LimitedTestCaseEvent> limitedTestCaseEvents = buildTestSlotList(knownTestsPairs);
        if (limitedTestCaseEvents.isEmpty()) {
            throw new NoTestCasesFoundException("No compatible tests cases were found");
        }
        return new TestEventQueue(limitedTestCaseEvents);
    }

    private Stream<DeviceTestPair> askDevicesForTests() {
        ExecutorService poolExecutor = null;
        try {
            List<TestCollectingListener> testCollectors = Collections.synchronizedList(new ArrayList<>());
            Collection<Pool> pools = poolLoader.loadPools();
            int numberOfPools = pools.size();
            CountDownLatch poolCountDownLatch = new CountDownLatch(numberOfPools);
            poolExecutor = namedExecutor(numberOfPools, "PoolSuiteLoader-%d");

            for (Pool pool : pools) {
                Runnable poolTestRunner = new Runnable() {
                    @Override
                    public void run() {
                        ExecutorService concurrentDeviceExecutor = null;
                        String poolName = pool.getName();
                        try {
                            int devicesInPool = pool.size();
                            concurrentDeviceExecutor = namedExecutor(devicesInPool, "DeviceExecutor-%d");
                            CountDownLatch deviceCountDownLatch = new CountDownLatch(devicesInPool);
                            logger.info("Pool {} started", poolName);
                            final Installer installer = installer();
                            final Configuration configuration = configuration();
                            for (Device device : pool.getDevices()) {
                                TestCollectingListener testCollector = new TestCollectingListener(device);
                                testCollectors.add(testCollector);
                                Runnable deviceTestRunner = new Runnable() {
                                    @Override
                                    public void run() {
                                        IDevice deviceInterface = device.getDeviceInterface();
                                        try {
                                            DdmPreferences.setTimeOut(30000);
                                            installer.prepareInstallation(deviceInterface);

                                            RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                                                    configuration.getInstrumentationPackage(),
                                                    configuration.getTestRunnerClass(),
                                                    deviceInterface);

                                            runner.setRunName(poolName);
                                            runner.setMaxtimeToOutputResponse((int) configuration.getTestOutputTimeout());

                                            Collection<ITestRunListener> testRunListeners = new ArrayList<>();
                                            testRunListeners.add(testCollector);

                                            runner.addBooleanArg("log", true);
                                            try {
                                                runner.run(testRunListeners);
                                            } catch (ShellCommandUnresponsiveException | TimeoutException e) {
                                                logger.warn("Test runner got stuck and test list collection was interrupted. " +
                                                        " Depending on number of available devices some tests will not be run." +
                                                        " You can increase the timeout in settings if it's too strict");
                                            } catch (AdbCommandRejectedException | IOException e) {
                                                throw new RuntimeException("Error while getting list of testcases from the test runner", e);
                                            }
                                        } finally {
                                            logger.info("Device {} from pool {} finished", device.getSerial(), pool.getName());
                                            deviceCountDownLatch.countDown();
                                        }
                                    }
                                };
                                concurrentDeviceExecutor.execute(deviceTestRunner);
                            }
                            deviceCountDownLatch.await();
                        } catch (InterruptedException e) {
                            logger.warn("Pool {} was interrupted while running", poolName);
                        } finally {
                            if (concurrentDeviceExecutor != null) {
                                concurrentDeviceExecutor.shutdown();
                            }
                            logger.info("Pool {} finished", poolName);
                            poolCountDownLatch.countDown();
                            logger.info("Pools remaining: {}", poolCountDownLatch.getCount());
                        }
                    }
                };
                poolExecutor.execute(poolTestRunner);
            }
            poolCountDownLatch.await();
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

    private static List<LimitedTestCaseEvent> buildTestSlotList(Stream<DeviceTestPair> deviceTestPairStream) {
        Map<TestIdentifier, Set<Device>> testDevicesMap = deviceTestPairStream.collect(groupingBy(
                DeviceTestPair::getTestIdentifier,
                mapping(DeviceTestPair::getDevice, toSet())));
        return testDevicesMap.entrySet().stream()
                .map(testIdentifierSetEntry -> new LimitedTestCaseEvent(testIdentifierSetEntry.getKey(), testIdentifierSetEntry.getValue()))
                .sorted(Comparator.comparingInt(o -> o.getSupportedDevices().size()))
                    // To make execution optimal tests supporting the least devices should be first
                .collect(toList());
    }

}