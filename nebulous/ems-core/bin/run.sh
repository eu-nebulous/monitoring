#!/usr/bin/env bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

# Change directory to EMS home
PREVWORKDIR=`pwd`
BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )
cd ${BASEDIR}
if [[ -z $EMS_CONFIG_DIR ]]; then EMS_CONFIG_DIR=$BASEDIR/config-files; export EMS_CONFIG_DIR; fi
#if [[ -z $PAASAGE_CONFIG_DIR ]]; then PAASAGE_CONFIG_DIR=$BASEDIR/config-files; export PAASAGE_CONFIG_DIR; fi
if [[ -z $JARS_DIR ]]; then JARS_DIR=$BASEDIR/control-service/target; export JARS_DIR; fi
if [[ -z $LOGS_DIR ]]; then LOGS_DIR=$BASEDIR/logs; export LOGS_DIR; fi
if [[ -z $PUBLIC_DIR ]]; then PUBLIC_DIR=$BASEDIR/public_resources; export PUBLIC_DIR; fi

# Read JASYPT password (decrypts encrypted configuration settings)
#JASYPT_PASSWORD=password
if [[ -z "$JASYPT_PASSWORD" ]]; then
    printf "Configuration Password: "
    read -s JASYPT_PASSWORD
fi
# Use this online service to encrypt/decrypt passwords:
# https://www.devglan.com/online-tools/jasypt-online-encryption-decryption

export JASYPT_PASSWORD

# Check EMS configuration
if [[ -z "$EMS_SECRETS_FILE" ]]; then
  EMS_SECRETS_FILE=$EMS_CONFIG_DIR/secrets.properties
fi
if [[ -z "$EMS_CONFIG_LOCATION" ]]; then
  EMS_CONFIG_LOCATION=optional:classpath:rule-templates.yml,optional:file:$EMS_CONFIG_DIR/ems-server.yml,optional:file:$EMS_CONFIG_DIR/ems-server.properties,optional:file:$EMS_CONFIG_DIR/ems.yml,optional:file:$EMS_CONFIG_DIR/ems.properties,optional:file:$EMS_SECRETS_FILE
fi

# Check logger configuration
if [[ -z "$LOG_CONFIG_FILE" ]]; then
    LOG_CONFIG_FILE=$EMS_CONFIG_DIR/logback-conf/logback-spring.xml
fi
if [[ -z "$LOG_FILE" ]]; then
    LOG_FILE=$LOGS_DIR/ems.log
    export LOG_FILE
fi

# Set shell encoding to UTF-8 (in order to display banner correctly)
export LANG=C.UTF-8

# Setup TERM & INT signal handler
trap 'echo "Signaling EMS to exit"; kill -TERM "${emsPid}"; wait "${emsPid}"; ' SIGTERM SIGINT

# Create default models directory
mkdir -p ${BASEDIR}/models

# Run EMS server
# Uncomment next line to set JAVA runtime options
#JAVA_OPTS=-Djavax.net.debug=all
#JAVA_OPTS=-agentlib:native-image-agent=config-output-dir=/mnt/ems/control-service/src/main/resources/META-INF/native-image
#JAVA_OPTS=-agentlib:native-image-agent=config-merge-dir=/mnt/ems/control-service/src/main/resources/META-INF/native-image
#export JAVA_OPTS

JAVA_ADD_OPENS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util.regex=ALL-UNNAMED --add-opens java.base/sun.nio.cs=ALL-UNNAMED --add-opens java.base/java.nio.charset=ALL-UNNAMED"

# Check if fat-jar exists
if [[ -f "${JARS_DIR}/control-service.jar" ]]; then
  CP="-cp ${JARS_DIR}/control-service.jar"
  ESPER_PATH="${JARS_DIR}/esper-7.1.0.jar,"
fi

# Download metric model
if [[ "${METRIC_MODEL_URL}" != "" ]]; then
  echo "Downloading metric model from URL: ${METRIC_MODEL_URL}"
  curl -o "${BASEDIR}/models/$(basename ${METRIC_MODEL_URL%\?*})" -m 60  ${METRIC_MODEL_URL}
fi

java -version
echo "LANG=$LANG"
#locale
echo "EMS_CONFIG_DIR=${EMS_CONFIG_DIR}"
echo "EMS_CONFIG_LOCATION=${EMS_CONFIG_LOCATION}"
echo "IP address=`hostname -I`"
echo "Starting EMS server..."
if [[ -z $RESTART_EXIT_CODE ]]; then RESTART_EXIT_CODE=99; export RESTART_EXIT_CODE; fi
retCode=$RESTART_EXIT_CODE
while :; do
  # Use when Esper is packaged in control-service.jar
  # java $EMS_DEBUG_OPTS $JAVA_OPTS $JAVA_ADD_OPENS -Djasypt.encryptor.password=$JASYPT_PASSWORD -Djava.security.egd=file:/dev/urandom -jar $JARS_DIR/control-service/target/control-service.jar "--spring.config.location=${EMS_CONFIG_LOCATION}" "--logging.config=file:$LOG_CONFIG_FILE"

  # Use when Esper is NOT packaged in control-service.jar
  java $EMS_DEBUG_OPTS $JAVA_OPTS  $JAVA_ADD_OPENS \
      -Djasypt.encryptor.password=$JASYPT_PASSWORD \
      -Djava.security.egd=file:/dev/urandom \
      -Dscan.packages=${SCAN_PACKAGES} \
      ${CP} \
      -Dloader.path=${ESPER_PATH}${EXTRA_LOADER_PATHS} \
      org.springframework.boot.loader.launch.PropertiesLauncher \
      "--spring.config.location=${EMS_CONFIG_LOCATION}" \
      "--logging.config=file:$LOG_CONFIG_FILE" \
      "${@}" &
  emsPid=$!
  echo "EMS Pid: $emsPid"
  wait $emsPid

  retCode=$?
  if [[ $retCode -eq $RESTART_EXIT_CODE ]]; then echo "Restarting EMS server..."; else break; fi
done
echo "EMS server exited"

# Extra parameters
# e.g. --spring.config.location=$EMS_CONFIG_DIR
# e.g. --spring.config.name=application.properties

cd $PREVWORKDIR
exit $retCode