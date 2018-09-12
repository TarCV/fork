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
import com.shazam.fork.model.LimitedTestCaseEvent;
import com.shazam.fork.model.TestCaseEvent;
import org.hamcrest.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.shazam.fork.model.Device.Builder.aDevice;
import static java.util.Collections.singleton;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.AllOf.allOf;

public class JUnitTestSuiteLoaderTest {
    private final Device device1 = uniqueDevice();
    private final Device device2 = uniqueDevice();

    @Test
    public void testNonIntersectedSetsAreCorrectlyMerged() throws NoTestCasesFoundException {
        StubDeviceTestPairsLoader pairsLoader = deviceTestPairsLoaderReturning(
                testCaseDevice("class1", "test1", device1),
                testCaseDevice("class1", "test2", device1),
                testCaseDevice("class2", "test1", device2),
                testCaseDevice("class2", "test2", device2)
        );

        Collection<TestCaseEvent> result = new JUnitTestSuiteLoader(pairsLoader).loadTestSuite();

        Assert.assertThat(result, containsInAnyOrder(
                allOf(
                        testCase("class1", "test1"),
                        supportingDevices(equalTo(singleton(device1)))
                ),
                allOf(
                        testCase("class1", "test2"),
                        supportingDevices(equalTo(singleton(device1)))
                ),
                allOf(
                        testCase("class2", "test1"),
                        supportingDevices(equalTo(singleton(device2)))
                ),
                allOf(
                        testCase("class2", "test2"),
                        supportingDevices(equalTo(singleton(device2)))
                )
        ));
    }

    @Test
    public void testCasesAreNotDuplicated() throws NoTestCasesFoundException {
        StubDeviceTestPairsLoader pairsLoader = deviceTestPairsLoaderReturning(
                testCaseDevice("class", "test", device1),
                testCaseDevice("class", "test", device2)
        );

        Collection<TestCaseEvent> result = new JUnitTestSuiteLoader(pairsLoader).loadTestSuite();

        Assert.assertThat(result, contains(
                allOf(
                        testCase("class", "test"),
                        supportingDevices(equalTo(setOf(device1, device2)))
                )
        ));
    }

    @Test
    public void testPartiallyIntersectedSetsAreCorrectlyMerged() throws NoTestCasesFoundException {
        StubDeviceTestPairsLoader pairsLoader = deviceTestPairsLoaderReturning(
                testCaseDevice("class1", "test1", device1),
                testCaseDevice("class1", "test1", device2),
                testCaseDevice("class1", "test2", device1),
                testCaseDevice("class1", "test2", device2),
                testCaseDevice("class21", "test1", device1),
                testCaseDevice("class21", "test2", device1),
                testCaseDevice("class22", "test1", device2),
                testCaseDevice("class22", "test2", device2)
        );

        Collection<TestCaseEvent> result = new JUnitTestSuiteLoader(pairsLoader).loadTestSuite();

        Assert.assertThat(result, containsInAnyOrder(
                allOf(
                        testCase("class1", "test1"),
                        supportingDevices(equalTo(setOf(device1, device2)))
                ),
                allOf(
                        testCase("class1", "test2"),
                        supportingDevices(equalTo(setOf(device1, device2)))
                ),
                allOf(
                        testCase("class21", "test1"),
                        supportingDevices(equalTo(singleton(device1)))
                ),
                allOf(
                        testCase("class21", "test2"),
                        supportingDevices(equalTo(singleton(device1)))
                ),
                allOf(
                        testCase("class22", "test1"),
                        supportingDevices(equalTo(singleton(device2)))
                ),
                allOf(
                        testCase("class22", "test2"),
                        supportingDevices(equalTo(singleton(device2)))
                )
        ));
    }

    private static DeviceTestPair testCaseDevice(String className, String methodName, Device device) {
        return new DeviceTestPair(device, new TestIdentifier(className, methodName));
    }

    @SafeVarargs
    private final <T> Set<T> setOf(T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    private static Device uniqueDevice() {
        return aDevice().build();
    }

    private static StubDeviceTestPairsLoader deviceTestPairsLoaderReturning(DeviceTestPair... pairs) {
        return new StubDeviceTestPairsLoader(Arrays.asList(pairs));
    }

    private static class StubDeviceTestPairsLoader implements DeviceTestPairsLoader {
        private final Collection<DeviceTestPair> pairs;

        private StubDeviceTestPairsLoader(Collection<DeviceTestPair> pairs) {
            this.pairs = pairs;
        }

        @Override
        public Stream<DeviceTestPair> askDevicesForTests() {
            return pairs.stream();
        }
    }

    private static Matcher<TestCaseEvent> testCase(String className, String methodName) {
        return allOf(
                hasProperty("testClass", equalTo(className)),
                hasProperty("testMethod", equalTo(methodName))
        );
    }

    private static Matcher<TestCaseEvent> supportingDevices(Matcher<Set<Device>> deviceMatcher) {
        TypeSafeMatcher<LimitedTestCaseEvent> safeDeviceMatcher = new TypeSafeMatcher<LimitedTestCaseEvent>(LimitedTestCaseEvent.class) {
            private final Matcher<LimitedTestCaseEvent> innerMatcher = new FeatureMatcher<LimitedTestCaseEvent, Set<Device>>(
                    deviceMatcher, "a TestCase supporting device", "supported devices"
            ) {
                @Override
                protected Set<Device> featureValueOf(LimitedTestCaseEvent actual) {
                    return actual.getSupportedDevices();
                }
            };


            @Override
            public void describeTo(Description description) {
                innerMatcher.describeTo(description);
            }

            @Override
            protected boolean matchesSafely(LimitedTestCaseEvent item) {
                return innerMatcher.matches(item);
            }
        };

        return new BaseMatcher<TestCaseEvent>() {
            @Override
            public boolean matches(Object item) {
                return safeDeviceMatcher.matches(item);
            }

            @Override
            public void describeTo(Description description) {
                safeDeviceMatcher.describeTo(description);
            }
        };
    }
}
