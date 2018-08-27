package com.shazam.fork.runner;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestCaseEvent;
import com.shazam.fork.model.TestEventQueue;

import static com.shazam.fork.model.TestCaseEvent.newTestCase;

public class TestRetryerImpl implements TestRetryer {
    private final ProgressReporter progressReporter;
    private final Pool pool;
    private final TestEventQueue queueOfTestsInPool;

    public TestRetryerImpl(ProgressReporter progressReporter, Pool pool, TestEventQueue queueOfTestsInPool) {
        this.progressReporter = progressReporter;
        this.pool = pool;
        this.queueOfTestsInPool = queueOfTestsInPool;
    }

    @Override
    public boolean rescheduleTestExecution(TestIdentifier testIdentifier, TestCaseEvent testCaseEvent, Device device) {
        progressReporter.recordFailedTestCase(pool, newTestCase(testIdentifier));
        if (progressReporter.requestRetry(pool, newTestCase(testIdentifier))) {
            queueOfTestsInPool.offerFromDevice(testCaseEvent, device);
            return true;
        }
        return false;
    }
}
