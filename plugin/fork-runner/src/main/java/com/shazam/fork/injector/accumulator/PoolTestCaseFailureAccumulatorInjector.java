package com.shazam.fork.injector.accumulator;

import com.shazam.fork.model.PoolTestCaseFailureAccumulator;

public class PoolTestCaseFailureAccumulatorInjector {

    private PoolTestCaseFailureAccumulatorInjector() {}

    public static PoolTestCaseFailureAccumulator poolTestCaseFailureAccumulator() {
        return new PoolTestCaseFailureAccumulator();
    }
}
