#!/usr/bin/env bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

PREVWORKDIR=`pwd`
BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )
cd ${BASEDIR}
if [[ -z $EMS_CONFIG_DIR ]]; then EMS_CONFIG_DIR=$BASEDIR/config-files; export EMS_CONFIG_DIR; fi
if [[ -z $PAASAGE_CONFIG_DIR ]]; then PAASAGE_CONFIG_DIR=$BASEDIR/config-files; export PAASAGE_CONFIG_DIR; fi

# Get IP addresses
UTIL_FILE=util-4.0.0-SNAPSHOT-jar-with-dependencies.jar
UTIL_PATH_0=util/target/${UTIL_FILE}
UTIL_PATH_1=jars/util/${UTIL_FILE}
UTIL_PATH_2=../util/target/${UTIL_FILE}
UTIL_PATH_3=./${UTIL_FILE}
if [ -f ${UTIL_PATH_0} ]; then
	UTIL_JAR=${UTIL_PATH_0}
elif [ -f ${UTIL_PATH_1} ]; then
	UTIL_JAR=${UTIL_PATH_1}
elif [ -f ${UTIL_PATH_2} ]; then
	UTIL_JAR=${UTIL_PATH_2}
elif [ -f ${UTIL_PATH_3} ]; then
	UTIL_JAR=${UTIL_PATH_3}
else
	echo "ERROR: Couldn't find 'util-4.0.0-SNAPSHOT-jar-with-dependencies.jar'"
	echo "ERROR: Skipping keystore initialization"
	cd ${PREVWORKDIR}
	exit 1
fi
#echo UTIL_JAR location: ${UTIL_JAR}

echo Resolving Public and Default IP addresses...
PUBLIC_IP=`java -jar ${UTIL_JAR} -nolog public`
DEFAULT_IP=`java -jar ${UTIL_JAR} -nolog default`

if [[ "${PUBLIC_IP}" == "" || "${PUBLIC_IP}" == "null" ]]; then
    PUBLIC_IP=127.0.0.1
fi
if [[ "${DEFAULT_IP}" == "" || "${DEFAULT_IP}" == "null" ]]; then
    DEFAULT_IP=127.0.0.1
fi

echo PUBLIC_IP=${PUBLIC_IP}
echo DEFAULT_IP=${DEFAULT_IP}

# Keystore initialization settings
KEY_GEN_ALG=RSA
KEY_SIZE=2048
KEY_ALIAS=ems
START_DATE=-1d
VALIDITY=3650
DN="CN=ems,OU=Information Management Unit (IMU),O=Institute of Communication and Computer Systems (ICCS),L=Athens,ST=Attika,C=GR"
EXT_SAN="SAN=dns:localhost,ip:127.0.0.1,ip:${DEFAULT_IP},ip:${PUBLIC_IP}"
KEYSTORE=${EMS_CONFIG_DIR}/broker-keystore.p12
TRUSTSTORE=${EMS_CONFIG_DIR}/broker-truststore.p12
CERTIFICATE=${EMS_CONFIG_DIR}/broker.crt
KEYSTORE_TYPE=PKCS12
KEYSTORE_PASS=melodic

# Keystores initialization
echo Generating key pair and certificate...
keytool -delete -alias ${KEY_ALIAS} -keystore ${KEYSTORE} -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS} &> /dev/null
keytool -genkey -keyalg ${KEY_GEN_ALG} -keysize ${KEY_SIZE} -alias ${KEY_ALIAS} -startdate ${START_DATE} -validity ${VALIDITY} -dname "${DN}" -ext "${EXT_SAN}" -keystore ${KEYSTORE} -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS}

echo Exporting certificate to file...
rm -rf ${CERTIFICATE} &> /dev/null
keytool -export -alias ${KEY_ALIAS} -file ${CERTIFICATE} -keystore ${KEYSTORE} -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS}

echo Importing certificate to trust store...
keytool -delete -alias ${KEY_ALIAS} -keystore ${TRUSTSTORE} -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS} &> /dev/null
keytool -import -noprompt -file ${CERTIFICATE} -alias ${KEY_ALIAS} -keystore ${TRUSTSTORE} -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS}

echo Key store, trust stores and certificate are ready.
cd $PREVWORKDIR
