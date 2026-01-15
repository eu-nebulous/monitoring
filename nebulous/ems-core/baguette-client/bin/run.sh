#!/bin/bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

# Spawning K8S monitor process
K8S_MONITOR_ENABLED=1
if [[ -n "$K8S_MONITOR_ENABLED" && -z "${KUBERNETES_SERVICE_HOST}" ]]; then
  COMMAND="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/k8smon.sh $$"
  LOG_FILE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd ../logs && pwd )/k8smon.log"
  nohup bash -c "$COMMAND" &>> $LOG_FILE &
  #nohup bash -c "$COMMAND" > >(tee -a /proc/$$/fd/1) 2> >(tee -a /proc/$$/fd/2 >&2) &
  PID=$!
  echo "Started K8S monitor process with PID $PID"
else
  echo "Running as a Kubernetes pod. K8S monitor will not start"
fi

# Change directory to Baguette client home
PREVWORKDIR=`pwd`
BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )
cd ${BASEDIR}

EMS_CONFIG_DIR=${BASEDIR}/conf
#PAASAGE_CONFIG_DIR=${BASEDIR}/conf
EMS_CONFIG_LOCATION=optional:file:$EMS_CONFIG_DIR/ems-client.yml,optional:file:$EMS_CONFIG_DIR/ems-client.properties,optional:file:$EMS_CONFIG_DIR/baguette-client.yml,optional:file:$EMS_CONFIG_DIR/baguette-client.properties
LOG_FILE=${BASEDIR}/logs/output.txt
#JASYPT_PASSWORD=password
JASYPT_PASSWORD=${EMS_CLIENT_JASYPT_PASSWORD}

[ -z "${JAVA_HOME}" ] && [ -d "${BASEDIR}/jre" ] && JAVA_HOME=${BASEDIR}/jre
#export EMS_CONFIG_DIR PAASAGE_CONFIG_DIR LOG_FILE JASYPT_PASSWORD JAVA_HOME
export EMS_CONFIG_DIR LOG_FILE JAVA_HOME

# Update path
PATH=${JAVA_HOME}/bin:$PATH

# Source external environment variables file
if [ "$EMS_EXTRA_ENV_VARS_FILE" != "" ]; then
  echo "Sourcing $EMS_EXTRA_ENV_VARS_FILE..."
  source $EMS_EXTRA_ENV_VARS_FILE
fi

# Check if baguette client is already running
#PID=`jps | grep BaguetteClient | cut -d " " -f 1`
PID=`ps -ef |grep java |grep BaguetteClient | cut -c 10-14`
if [ "$PID" != "" ]
then
  echo "Baguette client is already running (pid: $PID)"
  exit 0
fi

# Copy dependencies if missing
if [ -f pom.xml ]; then
  if [ ! -d ${BASEDIR}/target/dependency ]; then
    mvn dependency:copy-dependencies
  fi
fi

# Set JAVA_OPTS
#JAVA_OPTS=-Djavax.net.ssl.trustStore=${EMS_CONFIG_DIR}/client-broker-truststore.p12
#JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStorePassword=melodic -Djavax.net.ssl.trustStoreType=pkcs12"
#JAVA_OPTS="-Djavax.net.debug=all ${JAVA_OPTS}"
#JAVA_OPTS="-Dlogging.level.gr.iccs.imu.ems=TRACE ${JAVA_OPTS}"
#JAVA_OPTS="${JAVA_OPTS} -Xms1g -Xmx8g"
JAVA_OPTS="${JAVA_OPTS} -Djasypt.encryptor.password=$JASYPT_PASSWORD"
JAVA_OPTS="${JAVA_OPTS} --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED"

# Print settings
echo "" | tee -a ${LOG_FILE}
echo "---------------- $(date -Iseconds |sed -e 's/T/ /') ----------------" | tee -a ${LOG_FILE}
echo "EMS_CONFIG_DIR=${EMS_CONFIG_DIR}" | tee -a ${LOG_FILE}
echo "EMS_CONFIG_LOCATION=${EMS_CONFIG_LOCATION}" | tee -a ${LOG_FILE}
echo "LOG_FILE=${LOG_FILE}" | tee -a ${LOG_FILE}
echo "UNAME=$(uname -a)" | tee -a ${LOG_FILE}
echo "" | tee -a ${LOG_FILE}

# Run Baguette Client
if [ "$1" == "--i" ]; then
  echo "Baguette client running in Interactive mode" | tee -a ${LOG_FILE}
  java ${JAVA_OPTS} -classpath "conf:jars/*:target/classes:target/dependency/*" gr.iccs.imu.ems.baguette.client.BaguetteClient "--spring.config.location=${EMS_CONFIG_LOCATION}" "--logging.config=file:${EMS_CONFIG_DIR}/logback-spring.xml" "$@" 2>&1 | tee -a ${LOG_FILE}
else
  # Setup TERM & INT signal handler
  #trap "echo \"Signaled EMS client to exit\" | tee -a ${LOG_FILE}" SIGTERM SIGINT

  # Run Baguette Client
  echo "Starting Baguette client..." | tee -a ${LOG_FILE}
  exec java ${JAVA_OPTS} -classpath "conf:jars/*:target/classes:target/dependency/*" gr.iccs.imu.ems.baguette.client.BaguetteClient "--spring.config.location=${EMS_CONFIG_LOCATION}" "--logging.config=file:${EMS_CONFIG_DIR}/logback-spring.xml" "$@" 2>&1 | tee -a ${LOG_FILE}
  #PID=$!
  #echo "Baguette client PID: $PID" | tee -a ${LOG_FILE}

  #while kill -0 $PID 2>/dev/null; do
  #  echo "Process with PID $PID is still running..."
  #  sleep 1
  #done
  #echo "Process with PID $PID has ended."

  #if command -v jps
  #then
  #  PID=`jps | grep BaguetteClient | cut -d " " -f 1`
  #  PID=`ps -ef |grep java |grep BaguetteClient | cut -c 10-14`
  #  echo "Baguette client PID: $PID"
  #fi
fi

cd $PREVWORKDIR