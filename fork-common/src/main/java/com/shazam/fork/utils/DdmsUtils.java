package com.shazam.fork.utils;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;

/**
 * Various utils for DDMS
 */
public class DdmsUtils {
    private DdmsUtils() {}

    /**
     * Properly sets classname and method name for the passed runner
     *
     * {@link RemoteAndroidTestRunner} has bug that test method names are not properly quoted
     * for command line. This method provides a workaround.
     *
     * @param runner Method name to quote
     * @param testClassName Class name to use
     * @param testMethodName Method name to use
     */
    public static void properlySetMethodName(
            RemoteAndroidTestRunner runner,
            String testClassName,
            String testMethodName) {
        String escapedMethodName = (testClassName + "#" + testMethodName).replaceAll("'", "\\'");
        runner.setClassName(String.format("'%s'", escapedMethodName));
    }
}
