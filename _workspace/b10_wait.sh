#!/bin/bash
# Wait until the B10 window ends, then stop logcat, measure memory and analyze.
A=/Users/bbqman/Library/Android/sdk/platform-tools/adb
W=/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace
END=$1          # epoch seconds
while [ "$(date +%s)" -lt "$END" ]; do sleep 5; done
$A shell dumpsys meminfo com.timemoredot.app | grep -E "TOTAL PSS|Java Heap|Native Heap" | head -3 > $W/04_device_b_F5_B10_mem_end.txt
pkill -INT -f "logcat -v epoch -s TimemoreBle" ; sleep 1
echo "== mem start"; cat $W/04_device_b_F5_B10_mem_start.txt; echo "== mem end"; cat $W/04_device_b_F5_B10_mem_end.txt
python3 $W/b10_judge.py $W/04_device_b_F5_B1_B10.log
$A shell dumpsys battery | grep -m1 level
$A exec-out screencap -p > $W/04_device_shots/F5_B10_end.png 2>/dev/null
