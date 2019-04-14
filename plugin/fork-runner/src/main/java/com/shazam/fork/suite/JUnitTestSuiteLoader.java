package com.shazam.fork.suite;

/*
 * Copyright 2016 Shazam Entertainment Limited
 * Derivative work is Copyright 2018-2019 TarCV
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
import com.google.gson.*;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.shazam.fork.Utils.namedExecutor;
import static com.shazam.fork.injector.ConfigurationInjector.configuration;
import static com.shazam.fork.injector.system.InstallerInjector.installer;
import static com.shazam.fork.suite.ForkTestSuiteLoader.keyValueArraysToProperties;
import static java.lang.Thread.sleep;
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
        // 2. Apply filters we can apply without running them on devices

        Pattern packagePrefixPattern = Pattern.compile("^[a-z_][a-z0-9_.]*\\.");
        Pattern testClassPattern = configuration().getTestClassPattern();

        Collection<TestCaseEvent> result = askDevicesForTests().stream()
                .filter(testCaseEvent ->
                {
                    String fullClassName = testCaseEvent.getTestClass();

                    // Not 100% reliable, but good as far as naming conventions are abode
                    int packagePrefixLength = 0;
                    Matcher matcher = packagePrefixPattern.matcher(fullClassName);
                    if (matcher.lookingAt()) {
                        packagePrefixLength = matcher.end();
                    }
                    String className = fullClassName.substring(packagePrefixLength);

                    return testClassPattern.matcher(className).matches();
                })
                .collect(Collectors.toList());
        if (result.isEmpty()) {
            throw new NoTestCasesFoundException("No tests cases were found");
        }

        return result;
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

                                            remoteAndroidTestRunnerFactory.properlyAddInstrumentationArg(runner, "package", configuration.getTestPackage());

                                            Collection<ITestRunListener> testRunListeners = new ArrayList<>();
                                            testRunListeners.add(testCollector);
                                            testRunListeners.add(logCatCollector);
                                            if (configuration.getForkIntegrationTestRunType() == ForkConfiguration.ForkIntegrationTestRunType.RECORD_LISTENER_EVENTS) {
                                                testRunListeners.add(new RecordingTestRunListener(device, true));
                                            }

                                            try {
                                                runner.run(testRunListeners);

                                                sleep(2500); // make sure all logcat messages are read
                                                BinaryOperator<JsonObject> firstAlways = (o1, o2) -> o1;
                                                Map<TestIdentifier, JsonObject> infoMessages = extractTestInfoMessages(logCatCollector.getMessages())
                                                        .stream()
                                                        .collect(Collectors.toMap((JsonObject o) -> {
                                                            String testClass = o.get("testClass").getAsString();
                                                            String testMethod = o.get("testMethod").getAsString();
                                                            return new TestIdentifier(testClass, testMethod);
                                                        }, identity(), firstAlways));
                                                testInfoMessages.putAll(infoMessages);
                                            } catch (ShellCommandUnresponsiveException | TimeoutException | InterruptedException e) {
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
                        List<String> permissionsToGrant = new ArrayList<>();
                        Map<String, String> properties = new HashMap<>();

                        JsonElement annotations = info.get("annotations");
                        if (annotations != null) {
                            for (JsonElement annotationElement : annotations.getAsJsonArray()) {
                                JsonObject annotation = annotationElement.getAsJsonObject();
                                String annotationType = annotation.get("annotationType").getAsString();
                                switch (annotationType) {
                                    case "com.shazam.fork.GrantPermission":
                                        annotation.getAsJsonArray("value")
                                                .forEach(jsonElement -> permissionsToGrant.add(jsonElement.getAsString()));
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
                                false, permissionsToGrant, properties, info);
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

    static class TestInfoCatCollector implements Collector<LogCatMessage, ArrayList<MessageTriple>, List<JsonObject>> {
        private final static JsonParser jsonParser = new JsonParser();
        private final static Logger logger = LoggerFactory.getLogger(TestInfoCatCollector.class);

        @Override
        public Supplier<ArrayList<MessageTriple>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<ArrayList<MessageTriple>, LogCatMessage> accumulator() {
            return (triples, logCatMessage) -> {
                String[] parts = logCatMessage.getMessage().split(":", 2);
                String[] indexParts = parts[0].split("-", 2);
                MessageTriple triple = new MessageTriple(indexParts[0], indexParts[1], parts[1]);
                triples.add(triple);
            };
        }

        @Override
        public BinaryOperator<ArrayList<MessageTriple>> combiner() {
            return (triples1, triples2) -> {
                ArrayList<MessageTriple> output = new ArrayList<>(triples1.size() + triples2.size());
                output.addAll(triples1);
                output.addAll(triples2);
                return output;
            };
        }

        @Override
        public Function<ArrayList<MessageTriple>, List<JsonObject>> finisher() {
            return triples -> {
                String joined = triples.stream()
                        .sorted()
                        .map(messageTriple -> messageTriple.line)
                        .collect(Collectors.joining());
                if (joined.endsWith(",")) {
                    joined = joined.substring(0, joined.length() - 1);
                }
                joined = "[" + joined + "]";

                try {
                    JsonArray array = jsonParser.parse(joined).getAsJsonArray();
                    return StreamSupport.stream(array.spliterator(), false)
                            .map(JsonElement::getAsJsonObject)
                            .collect(Collectors.toList());
                } catch (JsonParseException e) {
                    throw new RuntimeException("Failed to parse: " + joined, e);
                }
            };
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }
    static class MessageTriple implements Comparable<MessageTriple> {
        private final String objectId;
        private final String index;
        private final String line;

        MessageTriple(String objectId, String index, String line) {
            this.objectId = objectId;
            this.index = index;
            this.line = line;
        }

        @Override
        public int compareTo(MessageTriple other) {
            int result;

            result = objectId.compareTo(other.objectId);
            if (result != 0) return result;

            return index.compareTo(other.index);
        }
    }
}
