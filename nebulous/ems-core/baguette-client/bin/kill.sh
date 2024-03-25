#!/bin/bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

# Get Baguette client home directory
BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )

# Update path
#PATH=$PATH:/path/to/jre/bin/

# Kill Baguette client
#PID=`jps | grep BaguetteClient | cut -d " " -f 1`
#PID=`ps -ef |grep java |grep BaguetteClient | cut -c 10-20`
PID=`ps -ef |grep java |grep BaguetteClient | tr -s ' ' | cut -d ' ' -f 2`
if [ "$PID" != "" ]
then
	echo "Killing baguette client (pid: $PID)"
	kill -9 $PID
else
	echo "Baguette client is not running"
fi
