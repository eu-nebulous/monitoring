#!/usr/bin/env bash

arch=$(uname -m)
bits=$(getconf LONG_BIT)
echo "Architecture: $arch,  Bits: $bits"

[[ "$bits" == "32" ]] && [[ "${arch,,}" = arm* ]] && TARGET="ARM-32"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = arm* ]] && TARGET="ARM-64"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = amd* ]] && TARGET="x86-64"
[[ "$bits" == "64" ]] && [[ "${arch,,}" = x86* ]] && TARGET="x86-64"

DOWNLOAD_URL="https://download.bell-sw.com/java/21.0.2+14"
[[ "$TARGET" == "ARM-32" ]] && PACKAGE="bellsoft-jre21.0.2+14-linux-arm32-vfp-hflt-full.tar.gz"
[[ "$TARGET" == "ARM-64" ]] && PACKAGE="bellsoft-jre21.0.2+14-linux-aarch64-full.tar.gz"
[[ "$TARGET" == "x86-64" ]] && PACKAGE="bellsoft-jre21.0.2+14-linux-amd64-full.tar.gz"

if [[ "$TARGET" == "" ]]; then
  echo "Target device not supported"
  exit 1
else
  echo "Target device: $TARGET"
  curl -k $DOWNLOAD_URL/$PACKAGE -o /tmp/jre.tar.gz
  ls -l
fi
