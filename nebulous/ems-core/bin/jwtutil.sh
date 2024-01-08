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
if [[ -z $PAASAGE_CONFIG_DIR ]]; then PAASAGE_CONFIG_DIR=$BASEDIR/config-files; export PAASAGE_CONFIG_DIR; fi
if [[ -z $JARS_DIR ]]; then JARS_DIR=$BASEDIR/control-service/target; export JARS_DIR; fi

if [[ -z EMS_SECRETS_FILE ]]; then EMS_SECRETS_FILE=$EMS_CONFIG_DIR/secrets.properties; export EMS_SECRETS_FILE; fi
if [[ -z EMS_CONFIG_LOCATION ]]; then EMS_CONFIG_LOCATION=optional:file:$EMS_CONFIG_DIR/ems-server.yml,optional:file:$EMS_CONFIG_DIR/ems-server.properties,optional:file:$EMS_CONFIG_DIR/ems.yml,optional:file:$EMS_CONFIG_DIR/ems.properties,optional:file:$EMS_SECRETS_FILE; export EMS_CONFIG_LOCATION; fi

# Read JASYPT password (decrypts encrypted configuration settings)
#JASYPT_PASSWORD=password
if [[ -z "$JASYPT_PASSWORD" ]]; then
    printf "Configuration Password: "
    read -s JASYPT_PASSWORD
fi

java -Djasypt.encryptor.password=$JASYPT_PASSWORD  -cp ${JARS_DIR}/control-service.jar -Dloader.main=gr.iccs.imu.ems.control.util.jwt.JwtTokenUtil -Dlogging.level.ROOT=WARN -Dlogging.level.gr.iccs.imu.ems.util=ERROR "-Dspring.config.location=$EMS_CONFIG_LOCATION" org.springframework.boot.loader.launch.PropertiesLauncher $*
exitcode=$?

cd $PREVWORKDIR
exit $exitcode