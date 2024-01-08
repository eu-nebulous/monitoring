#!/usr/bin/env bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

INSTALL_LOG=/opt/baguette-install.log
echo "START: `date -Iseconds`" >> $INSTALL_LOG

# Command line arguments: <server cert. file> <server url> <server api-key>
SERVER_CERT=$1
BASE_URL=$2
APIKEY=$3

if [ -z "$SERVER_CERT" ]; then
  SERVER_CERT=""
elif [ "$SERVER_CERT" = "-" ]; then
  SERVER_CERT="--no-check-certificate"
else
  SERVER_CERT="--ca-certificate=${SERVER_CERT}"
fi

# Create installation directories
BIN_DIRECTORY=/opt/baguette-client/bin
CONF_DIRECTORY=/opt/baguette-client/conf
LOGS_DIRECTORY=/opt/baguette-client/logs

mkdir -p $BIN_DIRECTORY/
mkdir -p $CONF_DIRECTORY/
mkdir -p $LOGS_DIRECTORY/

echo ""
echo "** EMS Baguette Client **"
echo "** Copyright ICCS-NTUA (C) 2016-2019, http://imu.iccs.gr **"
echo ""
date -Iseconds

# Common variables
DOWNLOAD_URL=$BASE_URL/baguette-client.tgz
DOWNLOAD_URL_MD5=$BASE_URL/baguette-client.tgz.md5
INSTALL_PACKAGE=/opt/baguette-client/baguette-client.tgz
INSTALL_PACKAGE_MD5=/opt/baguette-client/baguette-client.tgz.md5
INSTALL_DIR=/opt/
STARTUP_SCRIPT=$BIN_DIRECTORY/baguette-client
SERVICE_NAME=baguette-client
CLIENT_CONF_FILE=$CONF_DIRECTORY/baguette-client.properties
CLIENT_ID_FILE=$CONF_DIRECTORY/id.txt

# Check if already installed
if [ -f /opt/baguette-client/conf/ok.txt ]; then
  echo "Already installed. Exiting..."
  date -Iseconds
  echo "END: Already installed: `date -Iseconds`" >> $INSTALL_LOG
  exit 0
fi

# Create installation directory
echo ""
echo "Create installation directory..."
date -Iseconds
mkdir -p $INSTALL_DIR/baguette-client
if [ $? != 0 ]; then
  echo "Failed to create installation directory ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: mkdir: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

# Download installation package
echo ""
echo "Download installation package..."
date -Iseconds
wget $SERVER_CERT $DOWNLOAD_URL -O $INSTALL_PACKAGE
if [ $? != 0 ]; then
  echo "Failed to download installation package ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: download: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi
date -Iseconds
echo "Download installation package...ok"

# Download installation package MD5 checksum
echo ""
echo "Download installation package MD5 checksum..."
date -Iseconds
wget $SERVER_CERT $DOWNLOAD_URL_MD5 -O $INSTALL_PACKAGE_MD5
if [ $? != 0 ]; then
  echo "Failed to download installation package ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: download MD5: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi
date -Iseconds
echo "Download installation package MD5 checksum...ok"

# Check MD5 checksum
PACKAGE_MD5=`cat $INSTALL_PACKAGE_MD5`
PACKAGE_CHECKSUM=`md5sum $INSTALL_PACKAGE |cut -d " " -f 1`
echo ""
echo "Checksum MD5:  $PACKAGE_MD5"
echo "Checksum calc: $PACKAGE_CHECKSUM"
if [ $PACKAGE_CHECKSUM == $PACKAGE_MD5 ]; then
  echo "Checksum: ok"
else
  echo "Checksum: wrong"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: wrong MD5: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

# Extract installation package
echo ""
echo "Extracting installation package..."
date -Iseconds
#unzip -o $INSTALL_PACKAGE -d $INSTALL_DIR
tar -xvzf $INSTALL_PACKAGE -C $INSTALL_DIR
if [ $? != 0 ]; then
  echo "Failed to extract installation package contents ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: extract: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi
date -Iseconds

# Make scripts executable
echo ""
echo "Make scripts executable..."
date -Iseconds
chmod u=rx,og-rwx $INSTALL_DIR/baguette-client/bin/*
if [ $? != 0 ]; then
  echo "Failed to copy service script to /etc/init.d/ directory ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: chmod: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

# Register as a service
echo ""
echo "Register as a service..."
date -Iseconds
cp -f $STARTUP_SCRIPT /etc/init.d/
if [ $? != 0 ]; then
  echo "Failed to copy service script to /etc/init.d/ directory ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: cp init.d: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

update-rc.d $SERVICE_NAME defaults
if [ $? != 0 ]; then
  echo "Failed to register service script to /etc/init.d/ directory ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: update-rc.d: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

# Add Id, Credentials and Client configuration files
echo "Add Id, Credentials and Client configuration files"
date -Iseconds
touch $CLIENT_ID_FILE $CLIENT_CONF_FILE
if [ $? != 0 ]; then
  echo "Failed to 'touch' configuration files ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: touch: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

chmod u=rw,og-rwx $CLIENT_ID_FILE $CLIENT_CONF_FILE
if [ $? != 0 ]; then
  echo "Failed to change permissions of configuration files ($?)"
  echo "Aborting installation..."
  date -Iseconds
  echo "ABORT: chmod 2: `date -Iseconds`" >> $INSTALL_LOG
  exit 1
fi

# Write successful installation file
echo "Write successful installation file"
date -Iseconds
sudo touch $CONF_DIRECTORY/ok.txt

echo "END: OK: `date -Iseconds`" >> $INSTALL_LOG

# Launch Baguette Client
echo "Launch Baguette Client"
date -Iseconds
sudo service baguette-client start

echo "RUN: `date -Iseconds`" >> $INSTALL_LOG

# Success
echo ""
echo "Success - Baguette client successfully installed on system"
date -Iseconds
echo ""
exit 0
