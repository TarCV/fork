package com.shazam.fork.runner.listeners;

import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.model.Device;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class CollectingLogCatTestRunListener extends BaseLogCatTestRunListener {
    public CollectingLogCatTestRunListener(Device device) {
        super(device);
    }

    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        // no-op
    }

    @NotNull
    @Override
    public List<LogCatMessage> getMessages() {
        return super.getMessages();
    }
}
