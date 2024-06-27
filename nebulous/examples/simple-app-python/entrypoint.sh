#!/usr/bin/env bash
#
# Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

echo Argument: $1

case $1 in
  client)
    # Start simple-app to send messages to broker
    python simple-app.py
    ;;
  prometheus-sh)
    # Start prometheus endpoint using bash script
    ./simple-prometheus-exporter.sh
    ;;
  prometheus-py | *)
    # Start prometheus endpoint using python programme
    python simple-prometheus-exporter.py
esac