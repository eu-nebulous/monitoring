#!/usr/bin/env bash
#
# Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

# ----- Setup -----
DELAY=5
PROCNAMES=( "${@:2}" )
if [ ${#PROCNAMES[@]} -eq 0 ]; then PROCNAMES=('kubelet' 'etcd'); fi
WHATTOKILL=('baguette' 'netdata')
EXIT_GRACE=5

PROPERTIES_FILE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd ../conf && pwd )/k8smon.properties"
if [ -f $PROPERTIES_FILE ] && [ -r $PROPERTIES_FILE ]; then
    source $PROPERTIES_FILE
    echo "Loaded properties from file: $PROPERTIES_FILE"
fi

# ----- Check if 'k8smon' is already running -----
current_pid=$$
#if [ $# -gt 0 ] && [ "$(echo "$1" | tr '[:upper:]' '[:lower:]')" = "stop" ]; then
if [ $# -gt 0 ] && [ "${1,,}" = "stop" ]; then
    for pp in `ps aux |grep 'k8smon' |grep -v 'grep' |sed 's/   */:/g'  |cut -d':' -f2 |grep -v $current_pid`; do
        kill $pp 2>/dev/null
    done
    exit 0
else
    if [ `ps aux |grep 'k8smon' |grep -v 'grep' |sed 's/   */:/g'  |cut -d':' -f2 |grep -v $current_pid |wc -l` -gt 1 ]; then
        echo "Another instance of K8S monitor is already running. Exiting."
        exit 1
    fi
fi

# ----- Logging -----
declare -A LOG_LEVEL
LOG_LEVEL[TRACE]="3 35"
LOG_LEVEL[DEBUG]="3 36"
LOG_LEVEL[INFO]="3 32"
LOG_LEVEL[WARN]="3 33"
LOG_LEVEL[ERROR]="4 31"
LOG_LEVEL[FATAL]="4 7"
log() {
    IFS=', ' read -r -a LEVEL_CFG <<< " ${LOG_LEVEL[$1]}"
    FD=${LEVEL_CFG[0]}
    COL=${LEVEL_CFG[1]}
    while IFS= read -r line; do
        stdbuf -o0  echo -e "\e[37m$(date '+%Y-%m-%d %H:%M:%S %Z')\e[0m \e[${COL}m[$(printf '%-5s' $1)]\e[0m $line" >&$FD
    done
}
# Save original file descriptors
exec 3>&1 4>&2
# Redirect stdout and stderr to the log function
exec 1> >( log INFO )  2> >( log WARN )
# ----- Alternative Logging -----
# ./k8smon.sh | xargs -L 1 echo `date +'[%Y-%m-%d %H:%M:%S]'` $1


# ----- Start monitoring -----
echo "Starting K8S process monitor..."
#echo "Parent PID: $1"

# ----- Main loop -----
while true; do
    sleep $DELAY
    #echo Running check...
    found=0
    # Loop over process names
    for procname in "${PROCNAMES[@]}"
    do
        #echo Checking for $procname
        if [ `ps -ef |grep $procname |grep -v grep |grep -v k8smon |grep -v $0 |wc -l` -gt 0 ]; then
            #echo Found $procname
            found=1
        fi
    done
    #echo FOUND_ANY=$found
    if [ $found -eq 1 ]; then
        # Stop and disable netdata
        if [ `ps -ef |grep -i netdata |grep -v grep |grep -v systemd |wc -l` -gt 0 ]; then
          echo "Stopping and disabling netdata" >&2
          command -v sudo &>/dev/null && command -v systemctl &>/dev/null && \
              sudo systemctl disable netdata && sudo systemctl stop netdata && \
              command -v pkill &>/dev/null && pkill netdata && sleep $EXIT_GRACE && pkill -9 netdata
        fi

        # Kill processes mentioned in 'WHATTOKILL' array
        for proctokill in "${WHATTOKILL[@]}"
        do
            # Check if 'proctokill' is running
            if [ `ps -ef |grep -i $proctokill |grep -v grep |grep -v k8smon |grep -v $0 |wc -l` -gt 0 ]; then
                echo "Killing $proctokill" >&2
                PID2KILL=`echo $(ps -ef |grep -i $proctokill |grep -v grep |grep -v k8smon |grep -v $0) | cut -d' ' -f2`
                kill $PID2KILL
                sleep $EXIT_GRACE
                kill -9 $PID2KILL
            fi
        done
    fi
done

# Restore original file descriptors (if needed)
#exec 1>&3 2>&4