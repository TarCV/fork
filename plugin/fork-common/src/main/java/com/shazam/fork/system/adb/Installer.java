package com.shazam.fork.system.adb;

import com.android.ddmlib.IDevice;

public interface Installer {
    void prepareInstallation(IDevice device);
}
