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

import com.android.ddmlib.*;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shazam.fork.Configuration;
import com.shazam.fork.ForkConfiguration;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestCaseEvent;
import com.shazam.fork.pooling.NoDevicesForPoolException;
import com.shazam.fork.pooling.NoPoolLoaderConfiguredException;
import com.shazam.fork.pooling.PoolLoader;
import com.shazam.fork.runner.IRemoteAndroidTestRunnerFactory;
import com.shazam.fork.runner.listeners.CollectingLogCatTestRunListener;
import com.shazam.fork.runner.listeners.RecordingTestRunListener;
import com.shazam.fork.system.adb.Installer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.shazam.fork.Utils.namedExecutor;
import static com.shazam.fork.injector.ConfigurationInjector.configuration;
import static com.shazam.fork.injector.system.InstallerInjector.installer;
import static com.shazam.fork.suite.ForkTestSuiteLoader.keyValueArraysToProperties;
import static java.util.function.Function.identity;

public class JUnitTestSuiteLoader implements TestSuiteLoader {
    private final Logger logger = LoggerFactory.getLogger(JUnitTestSuiteLoader.class);
    private final PoolLoader poolLoader;
    private final IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory;

    public JUnitTestSuiteLoader(
            PoolLoader poolLoader,
            IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory) {
        this.poolLoader = poolLoader;
        this.remoteAndroidTestRunnerFactory = remoteAndroidTestRunnerFactory;
    }

    @Override
    public Collection<TestCaseEvent> loadTestSuite() throws NoTestCasesFoundException {
        // 1. Ask instrumentation runner to provide list of testcases for us
        Set<TestCaseEvent> knownTests = askDevicesForTests();
        return new ArrayList<>(knownTests);
    }

    private Set<TestCaseEvent> askDevicesForTests() {
        ExecutorService poolExecutor = null;
        try {
            TestCollectingListener testCollector = new TestCollectingListener();
            Map<TestIdentifier, JsonObject> testInfoMessages = Collections.synchronizedMap(new HashMap<>());
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
                                Runnable deviceTestRunner = new Runnable() {
                                    @Override
                                    public void run() {
                                        IDevice deviceInterface = device.getDeviceInterface();
                                        CollectingLogCatTestRunListener logCatCollector =
                                                new CollectingLogCatTestRunListener(device);
                                        try {
                                            DdmPreferences.setTimeOut(30000);
                                            installer.prepareInstallation(deviceInterface);

                                            RemoteAndroidTestRunner runner =
                                                    remoteAndroidTestRunnerFactory.createRemoteAndroidTestRunner(
                                                            configuration.getInstrumentationPackage(),
                                                            configuration.getTestRunnerClass(),
                                                            deviceInterface);

                                            runner.setRunName(poolName);
                                            runner.setMaxtimeToOutputResponse((int) configuration.getTestOutputTimeout());

                                            runner.addBooleanArg("log", true);
                                            runner.addInstrumentationArg("filter", "com.shazam.fork.ondevice.AnnontationReadingFilter");

                                            Collection<ITestRunListener> testRunListeners = new ArrayList<>();
                                            testRunListeners.add(testCollector);
                                            testRunListeners.add(logCatCollector);
                                            if (configuration.getForkIntegrationTestRunType() == ForkConfiguration.ForkIntegrationTestRunType.RECORD_LISTENER_EVENTS) {
                                                testRunListeners.add(new RecordingTestRunListener(device, true));
                                            }

                                            try {
                                                runner.run(testRunListeners);

                                                BinaryOperator<JsonObject> firstAlways = (o1, o2) -> o1;
                                                Map<TestIdentifier, JsonObject> infoMessages = extractTestInfoMessages(logCatCollector.getMessages())
                                                        .stream()
                                                        .collect(Collectors.toMap((JsonObject o) -> {
                                                            String testClass = o.get("testClass").getAsString();
                                                            String testMethod = o.get("testMethod").getAsString();
                                                            return new TestIdentifier(testClass, testMethod);
                                                        }, identity(), firstAlways));
                                                testInfoMessages.putAll(infoMessages);
                                            } catch (ShellCommandUnresponsiveException | TimeoutException e) {
                                                logger.warn("Test runner got stuck and test list collection was interrupeted. " +
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
            return joinTestInfo(testCollector.getTests(), testInfoMessages);
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

    private Set<TestCaseEvent> joinTestInfo(Set<TestIdentifier> tests, Map<TestIdentifier, JsonObject> testInfos) {
        return tests.stream()
                .map(testIdentifier -> {
                    JsonObject info = testInfos.get(testIdentifier);
                    if (info != null) {
                        List<String> permissionsToRevoke = new ArrayList<>();
                        Map<String, String> properties = new HashMap<>();

                        JsonElement annotations = info.get("annotations");
                        if (annotations != null) {
                            for (JsonElement annotationElement : annotations.getAsJsonArray()) {
                                JsonObject annotation = annotationElement.getAsJsonObject();
                                String annotationType = annotation.get("annotationType").getAsString();
                                switch (annotationType) {
                                    case "com.shazam.fork.RevokePermission":
                                        annotation.getAsJsonArray("value")
                                                .forEach(jsonElement -> permissionsToRevoke.add(jsonElement.getAsString()));
                                        break;
                                    case "com.shazam.fork.TestProperties":
                                        List<String> keys = toStringList(annotation.getAsJsonArray("keys"));
                                        List<String> values = toStringList(annotation.getAsJsonArray("values"));
                                        keyValueArraysToProperties(properties, keys, values);
                                        break;
                                }
                            }
                        }

                        return TestCaseEvent.newTestCase(testIdentifier.getTestName(), testIdentifier.getClassName(),
                                false, permissionsToRevoke, properties, info);
                    } else {
                        return TestCaseEvent.newTestCase(testIdentifier);
                    }
                })
                .collect(Collectors.toSet());
    }

    private ArrayList<String> toStringList(JsonArray array) {
        ArrayList<String> output = new ArrayList<>(array.size());
        array.forEach(jsonElement -> {
            output.add(jsonElement.getAsString());
        });
        return output;
    }

    @NotNull
    private List<JsonObject> extractTestInfoMessages(List<LogCatMessage> messages) {
        return messages.stream()
                .filter(logCatMessage -> "Fork.TestInfo".equals(logCatMessage.getTag()))
                .collect(new TestInfoCatCollector());
    }

    // TODO: implement testcases
    private static class TestInfoCatCollector implements Collector<LogCatMessage, ArrayList<StringBuilder>, List<JsonObject>> {
        private final static JsonParser jsonParser = new JsonParser();
        private final static Logger logger = LoggerFactory.getLogger(TestInfoCatCollector.class);

        @Override
        public Supplier<ArrayList<StringBuilder>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<ArrayList<StringBuilder>, LogCatMessage> accumulator() {
            return (strings, logCatMessage) -> {
                String line = logCatMessage.getMessage();
                if (!strings.isEmpty() && line.startsWith(" ")) {
                    int lastIndex = strings.size() - 1;
                    strings.set(lastIndex, strings.get(lastIndex).append(line));
                } else {
                    strings.add(new StringBuilder(line));
                }
            };
        }

        @Override
        public BinaryOperator<ArrayList<StringBuilder>> combiner() {
            return (lines1, lines2) -> {
                List<StringBuilder> secondBlock = lines2;
                if (!lines1.isEmpty() && !lines2.isEmpty()) {
                    StringBuilder firstLineOfSecondBlock = secondBlock.get(0);
                    if (firstLineOfSecondBlock.charAt(0) == ' ') {
                        int lastIndex = lines1.size() - 1;
                        lines1.get(lastIndex).append(firstLineOfSecondBlock);
                        secondBlock = secondBlock.subList(1, secondBlock.size());
                    }
                }

                ArrayList<StringBuilder> output = new ArrayList<>(lines1.size() + secondBlock.size());
                output.addAll(lines1);
                output.addAll(secondBlock);

                return output;
            };
        }

        @Override
        public Function<ArrayList<StringBuilder>, List<JsonObject>> finisher() {
            return lines -> lines.stream()
                    .map(StringBuilder::toString)
                    .map(string -> {
                        logger.debug(string);
                        return jsonParser.parse(string).getAsJsonObject();
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }
}
