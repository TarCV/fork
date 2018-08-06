#!/usr/bin/env bash

set -ex

cd `dirname "$0"`

echo no | avdmanager create avd --force -n fork-tests-25 -k "system-images;android-25;google_apis;armeabi-v7a"
echo no | avdmanager create avd --force -n fork-tests-18 -k "system-images;android-18;google_apis;armeabi-v7a"

QEMU_AUDIO_DRV=none ${ANDROID_HOME}/emulator/emulator -avd fork-tests-25 -no-window &
QEMU_AUDIO_DRV=none ${ANDROID_HOME}/emulator/emulator -avd fork-tests-18 -no-window &

echo "Waiting for 2 emulators to be online..."
set +x
while [ "`adb devices | awk '{print $2}' | grep device | wc -l`" != "2" ] ; do printf .; sleep 1; done
echo "2 emulators connected. Waiting for them to be online..."
set -x

adb devices </dev/null | while read line
do
    if [ ! "$line" = "" ] && [ `echo $line | awk '{print $2}'` = "device" ]
    then
        ANDROID_SERIAL=`echo $line | awk '{print $1}'`
        date
        echo "Waiting for $ANDROID_SERIAL..."
        adb wait-for-device </dev/null
        set +x
        while [ "`timeout 15s adb -s shell getprop sys.boot_completed </dev/null | tr -d '\r' `" != "1" ] ; do printf .; sleep 1; done
        set -x
        adb shell input keyevent 82 </dev/null
        # TODO: Fork itself should set these globals
        adb shell settings put global window_animation_scale 0 </dev/null
        adb shell settings put global transition_animation_scale 0 </dev/null
        adb shell settings put global animator_duration_scale 0 </dev/null
        echo "Emulator online"
    fi
done
ANDROID_SERIAL=''

./gradlew :app:fork
./gradlew :app:test
