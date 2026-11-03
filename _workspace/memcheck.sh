#!/bin/bash
# Memory check after forced GC (am dumpheap triggers a GC), then remove the temp heap dump.
A=/Users/bbqman/Library/Android/sdk/platform-tools/adb
P=com.timemoredot.app
W=/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace
echo "-- before GC"
$A shell dumpsys meminfo $P | grep -E "^ +(Dalvik Heap|Native Heap) |TOTAL PSS" | head -3
$A shell am dumpheap $P /data/local/tmp/pl.hprof
sleep 6
echo "-- after GC"
$A shell dumpsys meminfo $P | grep -E "^ +(Dalvik Heap|Native Heap) |TOTAL PSS" | head -3 | tee $W/04_device_b_F5_B10_mem_afterGC.txt
$A shell rm /data/local/tmp/pl.hprof
$A shell ls /data/local/tmp/pl.hprof 2>&1 | tail -1
