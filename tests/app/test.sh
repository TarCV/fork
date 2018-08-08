#!/usr/bin/env bash

set -ex

cd `dirname "$0"`

echo no | avdmanager create avd --force -n fork-tests-24 -k "system-images;android-24;google_apis;armeabi-v7a"
echo no | avdmanager create avd --force -n fork-tests-18 -k "system-images;android-18;google_apis;armeabi-v7a"

QEMU_AUDIO_DRV=none ${ANDROID_HOME}/emulator/emulator -avd fork-tests-24 -engine classic -no-window -camera-back none -camera-front none -verbose -qemu -m 1536 &
#QEMU_AUDIO_DRV=none ${ANDROID_HOME}/emulator/emulator -avd fork-tests-18 -no-window -camera-back none -camera-front none -verbose -qemu -m 512 &

echo "Waiting for 1 emulators to be online..."
set +x
while [ "`adb devices | awk '{print $2}' | grep device | wc -l`" != "1" ] ; do printf .; sleep 1; done
echo "1 emulators connected. Waiting for them to be online..."
set -x

adb devices </dev/null | while read line
do
    if [ ! "$line" = "" ] && [ `echo $line | awk '{print $2}'` = "device" ]
    then
        DEVICE=`echo $line | awk '{print $1}'`
        date
        echo "Waiting for $DEVICE..."
        adb -s ${DEVICE} wait-for-device </dev/null
        android-wait-for-emulator
#        set +e
#        while [ "`timeout -s 9 60s adb -s ${DEVICE} shell getprop sys.boot_completed </dev/null | tr -d '\r' `" != "1" ] ; do printf .; sleep 1; done
#        set -e
        adb -s ${DEVICE} shell input keyevent 82 </dev/null
        # TODO: Fork itself should set these globals
        adb -s ${DEVICE} shell settings put global window_animation_scale 0 </dev/null
        adb -s ${DEVICE} shell settings put global transition_animation_scale 0 </dev/null
        adb -s ${DEVICE} shell settings put global animator_duration_scale 0 </dev/null
        echo "Emulator online"
    fi
done
DEVICE=''

./gradlew :app:fork
./gradlew :app:test
