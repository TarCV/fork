#!/usr/bin/env bash

set -ex

cd `dirname "$0"`

cat $(which android-wait-for-emulator)

echo no | avdmanager create avd --force -n fork-tests-24 -k "system-images;android-24;google_apis;armeabi-v7a"
echo no | avdmanager create avd --force -n fork-tests-18 -k "system-images;android-18;google_apis;armeabi-v7a"

function wait_for_connectible {
    echo "Waiting for ${EMULATOR_NUM} emulators to be online..."
    set +x
    while [ "`adb devices | awk '{print $2}' | grep device | wc -l`" != "${EMULATOR_NUM}" ] ; do printf .; sleep 1; done
    echo "${EMULATOR_NUM} emulators connected. Waiting for them to be online..."
    set -x
}

function wait_for_boot_and_setup {
    date
    echo "Waiting for $ANDROID_SERIAL..."
    adb wait-for-device get-serialno </dev/null
    set +e
    until [[ "`timeout -s 9 60s adb shell getprop init.svc.bootanim 2>&1 </dev/null`" =~ "stopped" ]] ; do printf .; sleep 1; done
    set -e
    adb shell input keyevent 82 </dev/null
    # TODO: Fork itself should set these globals
    adb shell settings put global window_animation_scale 0 </dev/null
    adb shell settings put global transition_animation_scale 0 </dev/null
    adb shell settings put global animator_duration_scale 0 </dev/null
}

EMULATOR_NUM=1

QEMU_AUDIO_DRV=none ${ANDROID_HOME}/emulator/emulator -avd fork-tests-24 -engine classic -no-boot-anim -wipe-data -no-snapshot -no-window -camera-back none -camera-front none -verbose -qemu -m 1536 &
wait_for_connectible
wait_for_boot_and_setup

QEMU_AUDIO_DRV=none ${ANDROID_HOME}/emulator/emulator -avd fork-tests-18 -engine classic -no-boot-anim -wipe-data -no-snapshot -no-window -camera-back none -camera-front none -verbose -qemu -m 512 &
ANDROID_SERIAL='emulator-5556'
export ANDROID_SERIAL
wait_for_connectible
wait_for_boot_and_setup

ANDROID_SERIAL=''
./gradlew :app:fork
./gradlew :app:test
