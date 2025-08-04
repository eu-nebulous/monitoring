#!/usr/bin/env bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

# Current Time / Start Time / Uptime
curr_dt=`date '+%Y-%m-%d %H:%M:%S'`
up_dt=`uptime -s`
curr_dt_sec=`date -d "$curr_dt" +%s`
up_dt_sec=`date -d "$up_dt" +%s`
uptime_sec=$(( curr_dt_sec - up_dt_sec ))
echo CurrDateTime: $curr_dt_sec
echo UpDateTime: $up_dt_sec
echo Uptime: $uptime_sec

# Report instant CPU usage (%) -- Using top
echo CPU: `top -b -n1 | grep "Cpu(s)" | awk '{print $2 + $4}'`

# Report average CPU usage (%) for last 1/5/15 minutes -- Using /proc/loadavg
echo CPU1: $(echo "$(awk '{print $1}' /proc/loadavg) $(nproc)" | awk '{printf "%.2f", ($1 / $2) * 100}')
echo CPU5: $(echo "$(awk '{print $2}' /proc/loadavg) $(nproc)" | awk '{printf "%.2f", ($1 / $2) * 100}')
echo CPU15: $(echo "$(awk '{print $3}' /proc/loadavg) $(nproc)" | awk '{printf "%.2f", ($1 / $2) * 100}')
#load=$(awk '{print $1}' /proc/loadavg)
#cores=$(nproc)
#cpu_usage=$(echo "$load $cores" | awk '{printf "%.2f", ($1 / $2) * 100}')

# Report Memory usage (%)
FREE_DATA=`free -m | grep Mem`
CURRENT=`echo $FREE_DATA | cut -f3 -d' '`
TOTAL=`echo $FREE_DATA | cut -f2 -d' '`
echo RAM: $(echo "$CURRENT $TOTAL" | awk '{print 100 * $1 / $2}' )

# Report Disk usage (%) -- '/' partition only
#echo DISK: `df -lh | awk '{if ($6 == "/") { print $5 }}' | head -1 | cut -d'%' -f1`
echo DISK: `df -lh | awk '{if ($6 == "/") { print 100 * $3 / $2 }}'`

# Report Network RX/TX usage (B/s)
ARR=($(ls -1 /sys/class/net/ | grep -E '^(eth|ens)'))

function measure_ifs() {
    local SUMRX=0
    local SUMTX=0
    for IF in "${ARR[@]}"; do
        let SUMRX=$SUMRX+`cat /sys/class/net/${IF}/statistics/rx_bytes`
        let SUMTX=$SUMTX+`cat /sys/class/net/${IF}/statistics/tx_bytes`
    done
    echo $SUMRX $SUMTX
}

START=($(measure_ifs))
WAIT=1
sleep $WAIT
END=($(measure_ifs))

RX=$(( (END[0] - START[0]) / WAIT ))
TX=$(( (END[1] - START[1]) / WAIT ))
echo RX: $RX
echo TX: $TX
