#!/usr/bin/env bash

arch=$(uname -m)
bits=$(getconf LONG_BIT)
echo "Architecture: $arch,  Bits: $bits"

[[ "$bits" == "32" ]] && [[ "${arch,,}" = arm* ]] && TARGET="ARM-32"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = arm* ]] && TARGET="ARM-64"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = aarch64 ]] && TARGET="ARM-64"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = amd* ]] && TARGET="x86-64"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = x86* ]] && TARGET="x86-64"

DOWNLOAD_URL="https://download.bell-sw.com/java/21.0.3+10"
[[ "$TARGET" == "ARM-32" ]] && PACKAGE="bellsoft-jre21.0.3+10-linux-arm32-vfp-hflt-full.tar.gz"
[[ "$TARGET" == "ARM-64" ]] && PACKAGE="bellsoft-jre21.0.3+10-linux-aarch64-full.tar.gz"
[[ "$TARGET" == "x86-64" ]] && PACKAGE="bellsoft-jre21.0.3+10-linux-amd64-full.tar.gz"

JRE_PACKAGE=/tmp/jre.tar.gz

if [[ "$TARGET" == "" ]]; then
  echo "Target device not supported"
  exit 1
else
  echo "Target device: $TARGET"
  if command -v curl &> /dev/null ; then
    echo "Downloading with curl from: $DOWNLOAD_URL/$PACKAGE to $JRE_PACKAGE"
    curl -k $DOWNLOAD_URL/$PACKAGE -o $JRE_PACKAGE
  elif command -v wget &> /dev/null ; then
    echo "Downloading with wget from: $DOWNLOAD_URL/$PACKAGE to $JRE_PACKAGE"
    wget --no-verbose --no-check-certificate $DOWNLOAD_URL/$PACKAGE -O $JRE_PACKAGE
  else
    echo "Neither curl or wget are available to download JRE"
  fi
  ls -l
fi
