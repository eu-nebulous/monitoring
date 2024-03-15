#!/usr/bin/env bash
#
# Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

EMS_CLIENT_K8S_CONFIG_MAP_NAME=ems-client-configmap
EMS_CLIENT_K8S_CONFIG_MAP_FILE=ems-client-configmap.json
K8S_OUTPUT_DIR=$EMS_CONFIG_DIR/k8s
K8S_OUTPUT_FILE=$K8S_OUTPUT_DIR/cfgmap_output.json

# Check if  baguette server connection info file exists
[ ! -f $EMS_CLIENT_K8S_CONFIG_MAP_FILE ] && exit 1

# Read baguette server connection info file into a variable
BSCI=$( tr -d "\r\n" < $EMS_CLIENT_K8S_CONFIG_MAP_FILE )
#echo $BSCI

# Write baguette server connection info into ems-client-configmap
mkdir -p $K8S_OUTPUT_DIR/
echo "/* Date: $(date) */" > $K8S_OUTPUT_FILE
sec=/var/run/secrets/kubernetes.io/serviceaccount
curl -sS \
     -H "Authorization: Bearer $(cat $sec/token)" \
     -H "Content-Type: application/json-patch+json" \
     --cacert $sec/ca.crt \
     --request PATCH \
     --data "[{\"op\": \"replace\", \"path\": \"/data\", \"value\": $BSCI}]" \
     https://$KUBERNETES_SERVICE_HOST/api/v1/namespaces/$(cat $sec/namespace)/configmaps/$EMS_CLIENT_K8S_CONFIG_MAP_NAME \
  &>> $K8S_OUTPUT_FILE
