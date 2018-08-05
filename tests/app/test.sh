#!/usr/bin/env bash

avdmanager create avd --force -n fork-tests-27 -g google_apis_playstore --abi x86 -k 'system-images;android-27;google_apis_playstore;x86'
avdmanager create avd --force -n fork-tests-18 -g google_apis --abi x86 -k 'system-images;android-18;google_apis;x86'

emulator -avd fork-tests-27 -no-audio -no-window &
emulator -avd fork-tests-18 -no-audio -no-window &

echo "Waiting for 2 emulators to be online"
adb devices | while read line
do
    if [ ! "$line" = "" ] && [ `echo $line | awk '{print $2}'` = "device" ]
    then
        device=`echo $line | awk '{print $1}'`
        while [ "`adb -s $device shell getprop sys.boot_completed </dev/null | tr -d '\r' `" != "1" ] ; do printf .; sleep 1; done
    fi
done

./gradlew :app:fork
./gradlew :app:test
