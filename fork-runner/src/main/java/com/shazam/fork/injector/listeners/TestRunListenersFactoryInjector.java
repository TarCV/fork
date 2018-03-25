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

package com.shazam.fork.injector.listeners;

import com.shazam.fork.runner.listeners.TestCaseRunListenersFactory;
import com.shazam.fork.runner.listeners.TestRunListenersFactory;

import static com.shazam.fork.injector.ConfigurationInjector.configuration;
import static com.shazam.fork.injector.GsonInjector.gson;
import static com.shazam.fork.injector.system.FileManagerInjector.fileManager;

public class TestRunListenersFactoryInjector {

    private TestRunListenersFactoryInjector() {}

    public static TestRunListenersFactory testRunListenersFactory() {
        return new TestCaseRunListenersFactory(configuration(), fileManager(), gson());
    }
}
