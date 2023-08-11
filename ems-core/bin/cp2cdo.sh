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
if [[ -z $EMS_CONFIG_DIR ]]; then EMS_CONFIG_DIR=${BASEDIR}/config-files; export EMS_CONFIG_DIR; fi
if [[ -z $PAASAGE_CONFIG_DIR ]]; then PAASAGE_CONFIG_DIR=${BASEDIR}/config-files; export PAASAGE_CONFIG_DIR; fi

# Copy dependencies if missing
if [[ -f ${BASEDIR}/control-service/pom.xml ]]; then
    if [[ ! -d ${BASEDIR}/control-service/target/dependency ]]; then
        cd ${BASEDIR}/control-service
        mvn dependency:copy-dependencies
        cd ${BASEDIR}
    fi
fi

java -classpath "control-service/target/classes;control-service/target/dependency/*" gr.iccs.imu.ems.control.util.CpModelHelper $*
# Usage: cp2cdo <file> <cdo-resource>

cd ${PREVWORKDIR}
