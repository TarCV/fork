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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.LimitedTestCaseEvent;
import com.shazam.fork.model.TestCaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class JUnitTestSuiteLoader implements TestSuiteLoader {
    private final Logger logger = LoggerFactory.getLogger(JUnitTestSuiteLoader.class);
    private final DeviceTestPairsLoader deviceTestPairsLoader;

    public JUnitTestSuiteLoader(
            DeviceTestPairsLoader deviceTestPairsLoader) {
        this.deviceTestPairsLoader = deviceTestPairsLoader;
    }

    @Override
    public Collection<TestCaseEvent> loadTestSuite() throws NoTestCasesFoundException {
        Stream<DeviceTestPair> knownTestsPairs = deviceTestPairsLoader.askDevicesForTests();
        List<TestCaseEvent> limitedTestCaseEvents = buildTestSlotList(knownTestsPairs);
        if (limitedTestCaseEvents.isEmpty()) {
            throw new NoTestCasesFoundException("No compatible tests cases were found");
        }
        return limitedTestCaseEvents;
    }

    private static List<TestCaseEvent> buildTestSlotList(Stream<DeviceTestPair> deviceTestPairStream) {
        Map<TestIdentifier, Set<Device>> testDevicesMap = deviceTestPairStream.collect(groupingBy(
                DeviceTestPair::getTestIdentifier,
                mapping(DeviceTestPair::getDevice, toSet())));
        return testDevicesMap.entrySet().stream()
                .map(testIdentifierSetEntry -> new LimitedTestCaseEvent(testIdentifierSetEntry.getKey(), testIdentifierSetEntry.getValue()))
                .sorted(Comparator.comparingInt(o -> o.getSupportedDevices().size())) // TODO: move this line into TestEventQueue
                    // To make execution optimal tests supporting the least devices should be first
                .collect(toList());
    }

}