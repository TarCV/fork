/*
 * Copyright 2018 Shazam Entertainment Limited
 * Derivative work is Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.injector.runner;

import com.shazam.fork.ForkConfiguration;
import com.shazam.fork.runner.IRemoteAndroidTestRunnerFactory;
import com.shazam.fork.runner.TestAndroidTestRunnerFactory;

import static com.shazam.fork.injector.ConfigurationInjector.configuration;

public class RemoteAndroidTestRunnerFactoryInjector {

    private RemoteAndroidTestRunnerFactoryInjector() {}

    public static IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory() {
        if (configuration().getForkIntegrationTestRunType() == ForkConfiguration.ForkIntegrationTestRunType.STUB_PARALLEL_TESTRUN) {
            return new TestAndroidTestRunnerFactory();
        } else {
            return new com.shazam.fork.runner.RemoteAndroidTestRunnerFactory();
        }
    }
}
