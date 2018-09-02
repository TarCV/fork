/*
 * Copyright 2014 Shazam Entertainment Limited
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
package com.shazam.fork.system.io;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.model.Device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.shazam.fork.utils.DdmsUtils.escapeArgumentForCommandLine;


public class RemoteFileManager {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileManager.class);
    private static final NullOutputReceiver NO_OP_RECEIVER = new NullOutputReceiver();

    private RemoteFileManager() {}

    private static String getForkDirectory(Device device) {
        return device.getExternalStoragePath() + "/fork";
    }

    private static String getCoverageDirectory(Device device) {
        return getForkDirectory(device) + "/coverage";
    }

    public static void removeRemotePath(IDevice device, String remotePath) {
        executeCommand(device, "rm " + remotePath, "Could not delete remote file(s): " + remotePath);
    }

    public static void createCoverageDirectory(Device device) {
        executeCommand(device.getDeviceInterface(), "mkdir " + getCoverageDirectory(device),
                       "Could not create remote directory: " + getCoverageDirectory(device));
    }

    public static String getCoverageFileName(Device device, TestIdentifier testIdentifier) {
        return getCoverageDirectory(device) + "/" +testIdentifier.toString() + ".ec";
    }

    public static void createRemoteDirectory(Device device) {
        String forkDirectory = getForkDirectory(device);
        executeCommand(device.getDeviceInterface(), "mkdir " + forkDirectory, "Could not create remote directory: " + forkDirectory);
    }

    public static void removeRemoteDirectory(Device device) {
        String forkDirectory = getForkDirectory(device);
        executeCommand(device.getDeviceInterface(), "rm -r " + forkDirectory, "Could not delete remote directory: " + forkDirectory);
    }

    private static void executeCommand(IDevice device, String command, String errorMessage) {
        try {
            device.executeShellCommand(command, NO_OP_RECEIVER);
        } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
            logger.error(errorMessage, e);
        }
    }

    public static String remoteVideoForTest(Device device, TestIdentifier test) {
        String path = remoteFileForTest(device, videoFileName(test));
        return escapeArgumentForCommandLine(path);
    }

    private static String remoteFileForTest(Device device, String filename) {
        return getForkDirectory(device) + "/" + filename;
    }

    private static String videoFileName(TestIdentifier test) {
        final String className = test.getClassName();
        if (className.contains("-")) {
            throw new IllegalArgumentException("Test class names must not contain '-' character");
        }
        return String.format("%s-%s.mp4", className, test.getTestName());
    }
}
