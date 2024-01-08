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
BASEDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd ${BASEDIR}

if [[ -z $EMS_CONFIG_DIR ]]; then EMS_CONFIG_DIR=${BASEDIR}/config; export EMS_CONFIG_DIR; fi

# Get IP addresses
echo Resolving Public IP addresses...
#PUBLIC_IP=`curl http://ifconfig.me 2> /dev/null`
#PUBLIC_IP=`curl http://www.icanhazip.com 2> /dev/null`
#PUBLIC_IP=`curl http://ipecho.net/plain 2> /dev/null`
#PUBLIC_IP=`curl http://bot.whatismyipaddress.com 2> /dev/null`
PUBLIC_IP=`curl https://diagnostic.opendns.com/myip 2> /dev/null`
#PUBLIC_IP=`curl http://checkip.amazonaws.com 2> /dev/null`

# or get IP address with 'hostname'
if [[ "${PUBLIC_IP}" == "" ]]; then
    PUBLIC_IP=`hostname --all-ip-addresses`
    echo "PUBLIC_IP (hostname -I): $PUBLIC_IP"
fi

# or set IP address manually
if [[ "${PUBLIC_IP}" == "" ]]; then
    PUBLIC_IP=1.2.3.4
    echo "PUBLIC_IP (manually): $PUBLIC_IP"
fi

# or use loopback
if [[ "${PUBLIC_IP}" == "" ]]; then
    PUBLIC_IP=127.0.0.1
    echo "PUBLIC_IP (loopback): $PUBLIC_IP"
fi
PUBLIC_IP=`echo ${PUBLIC_IP} | sed 's/ *$//g'`
echo PUBLIC_IP=${PUBLIC_IP}


# Get cached IP address from previous run (if any)
CACHED_IP_FILE=${EMS_CONFIG_DIR}/MY_IP
touch ${CACHED_IP_FILE}
CACHED_IP=`cat ${CACHED_IP_FILE}`
#echo "Cached IP address=${CACHED_IP}"

# Check if "Force update flag is set in command-line" (i.e. -U flag)
if [[ "$1" == "-U" ]]; then
    CACHED_IP="----"
fi

# Check if current and cached IP addresses match
if [[ "${PUBLIC_IP}" == "${CACHED_IP}" ]]; then
    echo "Current and Cached IP addresses are identical: ${PUBLIC_IP}"
    echo "Exit without changing keystores"
    exit 0
fi
# ...else store new IP address
echo ${PUBLIC_IP} > ${CACHED_IP_FILE}


# Prepare keystore base directory and truststore file
KEYSTORE_BASE_DIR=${EMS_CONFIG_DIR}/certs
TRUSTSTORE_DIR=${EMS_CONFIG_DIR}/common
TRUSTSTORE_FILE=${TRUSTSTORE_DIR}/melodic-truststore.p12

mkdir -p ${KEYSTORE_BASE_DIR}
mkdir -p ${TRUSTSTORE_DIR}
rm -f ${TRUSTSTORE_FILE} &> /dev/null

# Keystore initialization settings
KEY_GEN_ALG=RSA
KEY_SIZE=2048
START_DATE=-1d
VALIDITY=3650
DN_FMT="CN=%s,OU=Information Management Unit (IMU),O=Institute of Communication and Computer Systems (ICCS),L=Athens,ST=Attika,C=GR"
if [[ "${PUBLIC_IP}" != "" ]]; then
  PUBLIC_IP_FOR_SAN=${PUBLIC_IP// /,ip:}
  PUBLIC_IP_FOR_SAN="ip:${PUBLIC_IP_FOR_SAN}"
fi
if [[ "${EXTRA_IPS_FOR_SAN}" != "" ]]; then
  EXTRA_IPS_FOR_SAN=",${EXTRA_IPS_FOR_SAN}"
  EXTRA_IPS_FOR_SAN=`echo ${EXTRA_IPS_FOR_SAN} | sed -e 's/,/,ip:/g'`
  EXTRA_IPS_FOR_SAN=`echo ${EXTRA_IPS_FOR_SAN} | sed -e 's/[ \t]//g'`
fi
EXT_SAN_FMT="SAN=dns:%s,dns:localhost,ip:127.0.0.1,${PUBLIC_IP_FOR_SAN}${EXTRA_IPS_FOR_SAN}"

KEYSTORE_TYPE=PKCS12
KEYSTORE_PASS=melodic

# Definition of 'create_keystore_for' function for the:
# Creation of key pair and certificate for component
function create_keystore_for() {
    local COMPONENT=$1
    local KEYSTORE_DIR=${KEYSTORE_BASE_DIR}/${COMPONENT}
    local KEYSTORE_FILE=${KEYSTORE_DIR}/keystore.p12
    local CERT_FILE=${KEYSTORE_DIR}/${COMPONENT}.crt
    local KEY_ALIAS=${COMPONENT}
    local DN=`printf "${DN_FMT}" "${KEY_ALIAS}" `
    local EXT_SAN=`printf "${EXT_SAN_FMT}" "${KEY_ALIAS}" `

    echo "$COMPONENT:"
    mkdir -p ${KEYSTORE_DIR}

    echo "  Generating key pair and certificate for ${COMPONENT}..."
    rm -f ${KEYSTORE_FILE} &> /dev/null
    keytool -genkey -keyalg ${KEY_GEN_ALG} -keysize ${KEY_SIZE} \
            -alias ${KEY_ALIAS} \
            -startdate ${START_DATE} -validity ${VALIDITY} \
            -dname "${DN}" -ext "${EXT_SAN}" \
            -keystore ${KEYSTORE_FILE} \
            -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS}

    echo "  Exporting certificate of ${COMPONENT}..."
    rm -rf ${CERT_FILE} &> /dev/null
    keytool -export \
            -alias ${KEY_ALIAS} \
            -file ${CERT_FILE} \
            -keystore ${KEYSTORE_FILE} \
            -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS}

    echo "  Importing ${COMPONENT} certificate to truststore..."
    keytool -import -noprompt \
            -alias ${KEY_ALIAS} \
            -file ${CERT_FILE} \
            -keystore ${TRUSTSTORE_FILE} \
            -storetype ${KEYSTORE_TYPE} -storepass ${KEYSTORE_PASS}

    echo ""
}

# Creation of key pairs, certificates of all components and population of common truststore
create_keystore_for "cdoserver"
create_keystore_for "mule"
create_keystore_for "adapter"
create_keystore_for "generator"
create_keystore_for "cpsolver"
create_keystore_for "camunda"
create_keystore_for "memcache"
create_keystore_for "ldap"
create_keystore_for "metasolver"
create_keystore_for "jwtserver"
create_keystore_for "authdb"
create_keystore_for "authserver"
create_keystore_for "ems"
create_keystore_for "gui-backend"
create_keystore_for "gui-frontend"
#create_keystore_for "cloudiator"

echo Key stores, certificate and Melodic common truststores are ready.
cd $PREVWORKDIR
