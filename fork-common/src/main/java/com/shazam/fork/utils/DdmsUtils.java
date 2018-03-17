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
        String escapedMethodName = escapeArgumentForCommandLine(testClassName + "#" + testMethodName);
        runner.setClassName(escapedMethodName);
    }

    /**
     * Properly sets string argument for the passed runner
     *
     * {@link RemoteAndroidTestRunner} has bug that string values are not properly quoted
     * for command line. This method provides a workaround.
     *
     * @param runner Method name to quote
     * @param name  Argument name to use
     * @param value Argument value to use
     */
    public static void properlyAddInstrumentationArg(RemoteAndroidTestRunner runner,
                                                     String name,
                                                     String value) {
        String escapedValue = escapeArgumentForCommandLine(value);
        runner.addInstrumentationArg(name, escapedValue);
    }

    private static String escapeArgumentForCommandLine(String value) {
        return String.format("'%s'", value.replaceAll("'", "\\'"));
    }
}
