/*
 * Copyright 2014 Shazam Entertainment Limited
 * Derivative work is Copyright 2018-2019 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.shazam.fork.runner.listeners;

import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.google.gson.Gson;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.Pool;
import com.shazam.fork.system.io.FileManager;

import java.util.List;
import java.util.Map;

class LogCatTestRunListener extends BaseLogCatTestRunListener {
    private final FileManager fileManager;
    private final Pool pool;
	private final Gson gson;

    public LogCatTestRunListener(Gson gson, FileManager fileManager, Pool pool, Device device) {
		super(device);
		this.gson = gson;
        this.fileManager = fileManager;
        this.pool = pool;
	}

	@Override
	public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
		List<LogCatMessage> copyOfLogCatMessages = getMessages();
		LogCatWriter logCatWriter = new CompositeLogCatWriter(
                new JsonLogCatWriter(gson, fileManager, pool, device),
                new RawLogCatWriter(fileManager, pool, device));
        LogCatSerializer logCatSerializer = new LogCatSerializer(test, logCatWriter);
		logCatSerializer.serializeLogs(copyOfLogCatMessages);
	}
}
