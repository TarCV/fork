package com.shazam.fork.runner.listeners;

import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.model.Device;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class BaseLogCatTestRunListener implements ITestRunListener {
    protected final Device device;
    private final LogCatReceiverTask logCatReceiverTask;
    private final LogCatListener logCatListener;
    private final List<LogCatMessage> logCatMessages;

    BaseLogCatTestRunListener(Device device) {
        this.device = device;
        logCatReceiverTask = new LogCatReceiverTask(device.getDeviceInterface());
        logCatMessages = new ArrayList<>();
        logCatListener = new MessageCollectingLogCatListener(logCatMessages);
    }

    @Override
    public void testRunStarted(String runName, int testCount) {
        logCatReceiverTask.addLogCatListener(logCatListener);
        new Thread(logCatReceiverTask, "CatLogger-" + runName + "-" + device.getSerial()).start();
    }

    @Override
    public void testStarted(TestIdentifier test) {
    }

    @Override
    public void testFailed(TestIdentifier test, String trace) {
    }

    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
    }

    @Override
    public void testIgnored(TestIdentifier test) {
    }

    @Override
    public void testRunFailed(String errorMessage) {
    }

    @Override
    public void testRunStopped(long elapsedTime) {
    }

    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        logCatReceiverTask.stop();
        logCatReceiverTask.removeLogCatListener(logCatListener);
    }

    @NotNull
    protected List<LogCatMessage> getMessages() {
        List<LogCatMessage> copyOfLogCatMessages;
        synchronized (logCatMessages) {
            int size = logCatMessages.size();
            copyOfLogCatMessages = new ArrayList<>(size);
            copyOfLogCatMessages.addAll(logCatMessages);
        }
        return copyOfLogCatMessages;
    }


    protected final class MessageCollectingLogCatListener implements LogCatListener {
        private final List<LogCatMessage> logCatMessages;

        public MessageCollectingLogCatListener(List<LogCatMessage> messageList) {
            logCatMessages = messageList;
        }

        @Override
        public void log(List<LogCatMessage> msgList) {
            synchronized (logCatMessages) {
                logCatMessages.addAll(msgList);
            }
        }
    }
}
