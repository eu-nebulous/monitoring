#!/bin/bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

# Change directory to Baguette client home
PREVWORKDIR=`pwd`
BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )
cd ${BASEDIR}
EMS_CONFIG_DIR=${BASEDIR}/conf
PAASAGE_CONFIG_DIR=${BASEDIR}/conf
EMS_CONFIG_LOCATION=optional:file:$EMS_CONFIG_DIR/ems-client.yml,optional:file:$EMS_CONFIG_DIR/ems-client.properties,optional:file:$EMS_CONFIG_DIR/baguette-client.yml,optional:file:$EMS_CONFIG_DIR/baguette-client.properties
LOG_FILE=${BASEDIR}/logs/output.txt
TEE_FILE=${BASEDIR}/logs/tee.txt
JASYPT_PASSWORD=password
JAVA_HOME=${BASEDIR}/jre
export EMS_CONFIG_DIR PAASAGE_CONFIG_DIR LOG_FILE JASYPT_PASSWORD JAVA_HOME

# Update path
PATH=${JAVA_HOME}/bin:$PATH

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

# Run Baguette client
JAVA_OPTS=-Djavax.net.ssl.trustStore=${EMS_CONFIG_DIR}/client-broker-truststore.p12
JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStorePassword=melodic -Djavax.net.ssl.trustStoreType=pkcs12"
JAVA_OPTS="${JAVA_OPTS} -Djasypt.encryptor.password=$JASYPT_PASSWORD"
#JAVA_OPTS="-Djavax.net.debug=all ${JAVA_OPTS}"
#JAVA_OPTS="-Dlogging.level.gr.iccs.imu.ems=TRACE ${JAVA_OPTS}"
JAVA_OPTS="${JAVA_OPTS} --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED"

echo "Starting baguette client..."
echo "EMS_CONFIG_DIR=${EMS_CONFIG_DIR}"
echo "EMS_CONFIG_LOCATION=${EMS_CONFIG_LOCATION}"
echo "LOG_FILE=${LOG_FILE}"

echo "Starting baguette client..." &>> ${LOG_FILE}
echo "EMS_CONFIG_DIR=${EMS_CONFIG_DIR}" &>> ${LOG_FILE}
echo "EMS_CONFIG_LOCATION=${EMS_CONFIG_LOCATION}" &>> ${LOG_FILE}
echo "LOG_FILE=${LOG_FILE}" &>> ${LOG_FILE}

if [ "$1" == "--i" ]; then
  echo "Baguette client running in Interactive mode"
  java ${JAVA_OPTS} -classpath "conf:jars/*:target/classes:target/dependency/*" gr.iccs.imu.ems.baguette.client.BaguetteClient "--spring.config.location=${EMS_CONFIG_LOCATION}" "--logging.config=file:${EMS_CONFIG_DIR}/logback-spring.xml" $* $* 2>&1 | tee ${TEE_FILE}
else
  java ${JAVA_OPTS} -classpath "conf:jars/*:target/classes:target/dependency/*" gr.iccs.imu.ems.baguette.client.BaguetteClient "--spring.config.location=${EMS_CONFIG_LOCATION}" "--logging.config=file:${EMS_CONFIG_DIR}/logback-spring.xml" $* &>> ${LOG_FILE} &
  PID=`jps | grep BaguetteClient | cut -d " " -f 1`
  PID=`ps -ef |grep java |grep BaguetteClient | cut -c 10-14`
  echo "Baguette client PID: $PID"
fi

cd $PREVWORKDIR