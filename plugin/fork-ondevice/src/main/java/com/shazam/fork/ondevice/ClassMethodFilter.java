package com.shazam.fork.ondevice;

import android.os.Bundle;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

public class ClassMethodFilter extends Filter {
    private final String expectedClassName;
    private final String expectedMethodName;

    public ClassMethodFilter(Bundle bundle) {
        this.expectedClassName = bundle.getString("filterClass");
        this.expectedMethodName = bundle.getString("filterMethod");
    }

    @Override
    public boolean shouldRun(Description description) {
        if (description.isTest()) {
            return checkTest(description);
        } else {
            // Allow suite to be run when it contains at least one allowed test
            for (Description child : description.getChildren()) {
                if (shouldRun(child)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean checkTest(Description description) {
        String className = description.getClassName();
        String methodName = description.getMethodName();
        return expectedClassName.equals(className)
                    && expectedMethodName.equals(methodName);
    }

    @Override
    public String describe() {
        return null;
    }
}
