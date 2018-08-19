#!/usr/bin/env bash

set -e

cd `dirname "$0"`

export DEVICE1="fork-5554"
export DEVICE2="fork-5556"
export CI_STUBBED=true
./functionalTestCustom.sh
