#!/usr/bin/env bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

#Required utilities: grep,uniq,tr,cat,cut,uname. For commented commands, awk and wc.

BUSYBOX_PREFIX="${args[0]}"

#TMP_NUM_CPUS=$($BUSYBOX_PREFIX grep 'physical id' /proc/cpuinfo | $BUSYBOX_PREFIX sort | $BUSYBOX_PREFIX uniq | $BUSYBOX_PREFIX wc -l)
#TMP_NUM_CORES=$($BUSYBOX_PREFIX grep 'cpu cores' /proc/cpuinfo | $BUSYBOX_PREFIX sort | $BUSYBOX_PREFIX uniq | $BUSYBOX_PREFIX cut -d ' ' -f 3)
#TMP_NUM_PROCESSORS=$($BUSYBOX_PREFIX grep -c ^processor /proc/cpuinfo)
TMP_RAM_TOTAL_KB=$($BUSYBOX_PREFIX cat /proc/meminfo | $BUSYBOX_PREFIX grep MemTotal | $BUSYBOX_PREFIX tr -s ' ' | $BUSYBOX_PREFIX cut -d ' ' -f 2)
TMP_RAM_AVAILABLE_KB=$($BUSYBOX_PREFIX cat /proc/meminfo | $BUSYBOX_PREFIX grep MemAvailable | $BUSYBOX_PREFIX tr -s ' ' | $BUSYBOX_PREFIX cut -d ' ' -f 2)
TMP_RAM_FREE_KB=$($BUSYBOX_PREFIX cat /proc/meminfo | $BUSYBOX_PREFIX grep MemFree | $BUSYBOX_PREFIX tr -s ' ' | $BUSYBOX_PREFIX cut -d ' ' -f 2)
TMP_DISK_TOTAL_KB=$($BUSYBOX_PREFIX df -k | $BUSYBOX_PREFIX grep /$ | $BUSYBOX_PREFIX tr -s ' ' | $BUSYBOX_PREFIX cut -d ' ' -f 2)
TMP_DISK_FREE_KB=$($BUSYBOX_PREFIX df -k | $BUSYBOX_PREFIX grep /$ | $BUSYBOX_PREFIX tr -s ' ' | $BUSYBOX_PREFIX cut -d ' ' -f 4)
TMP_ARCHITECTURE=$($BUSYBOX_PREFIX uname -m) #x86_64 GNU/Linux indicates that you've a 64bit Linux kernel running. If you see i386/i486/i586/i686 it is a 32-bit architecture. armv7l, armv8 etc. signal a 32-bit arm version of the library while aarch64 indicates a 64-bit arm version of the library
TMP_KERNEL=$($BUSYBOX_PREFIX uname -s)
TMP_KERNEL_RELEASE=$($BUSYBOX_PREFIX uname -r)

#NUM_CORES_ALT=$BUSYBOX_PREFIX grep ^cpu\\scores /proc/cpuinfo | $BUSYBOX_PREFIX uniq |  $BUSYBOX_PREFIX awk '{print $4}'
#CAN_RUN_x64 = grep flags /proc/cpuinfo | grep " lm" | wc | tr -s ' ' | cut -d ' ' -f 2 #1 means that it can run x64, 0 that it can't, although that possibly also depends on the kernel installed

TMP_NUM_CPUS=$(lscpu -p | grep -v '#' | cut -d ',' -f 3 | sort -u | wc -l)
TMP_NUM_CORES=$(lscpu -p | grep -v '#' | cut -d ',' -f 2 | sort -u | wc -l)
TMP_NUM_PROCESSORS=$(lscpu -p | grep -v '#' | cut -d ',' -f 1 | sort -u | wc -l)
TMP_RAM_USED_KB=$(echo $TMP_RAM_TOTAL_KB $TMP_RAM_FREE_KB | awk '{print $1 - $2}')
TMP_RAM_UTILIZATION=$(echo $TMP_RAM_USED_KB $TMP_RAM_TOTAL_KB | awk '{print 100 * $1 / $2}')
TMP_DISK_USED_KB=$(echo $TMP_DISK_TOTAL_KB $TMP_DISK_FREE_KB | awk '{print $1 - $2}')
TMP_DISK_UTILIZATION=$(echo $TMP_DISK_USED_KB $TMP_DISK_TOTAL_KB | awk '{print 100 * $1 / $2}')


echo CPU_SOCKETS=$TMP_NUM_CPUS
echo CPU_CORES=$TMP_NUM_CORES
echo CPU_PROCESSORS=$TMP_NUM_PROCESSORS
echo RAM_TOTAL_KB=$TMP_RAM_TOTAL_KB
echo RAM_AVAILABLE_KB=$TMP_RAM_AVAILABLE_KB
echo RAM_FREE_KB=$TMP_RAM_FREE_KB
echo RAM_USED_KB=$TMP_RAM_USED_KB
echo RAM_UTILIZATION=$TMP_RAM_UTILIZATION
echo DISK_TOTAL_KB=$TMP_DISK_TOTAL_KB
echo DISK_FREE_KB=$TMP_DISK_FREE_KB
echo DISK_USED_KB=$TMP_DISK_USED_KB
echo DISK_UTILIZATION=$TMP_DISK_UTILIZATION
echo OS_ARCHITECTURE=$TMP_ARCHITECTURE
echo OS_KERNEL=$TMP_KERNEL
echo OS_KERNEL_RELEASE=$TMP_KERNEL_RELEASE
