#!/bin/bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless 
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at 
# https://www.mozilla.org/en-US/MPL/2.0/
#

BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )
EMS_CONFIG_DIR=.

#JAVA_OPTS=-Djavax.net.ssl.trustStore=./broker-truststore.p12\ -Djavax.net.ssl.trustStorePassword=melodic\ -Djavax.net.ssl.trustStoreType=pkcs12
# -Djavax.net.debug=all
# -Djavax.net.debug=ssl,handshake,record

JAVACMD=java
if [ -f ${BASEDIR}/jre/bin/java ]; then
  JAVA_HOME=$( cd ${BASEDIR}/jre* && pwd )
  JAVACMD=${JAVA_HOME}/bin/java
fi

${JAVACMD} $JAVA_OPTS -jar ${BASEDIR}/jars/broker-client/broker-client-jar-with-dependencies.jar $*
