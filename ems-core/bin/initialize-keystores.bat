@echo off
::
:: Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
::
:: This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
:: Esper library is used, in which case it is subject to the terms of General Public License v2.0.
:: If a copy of the MPL was not distributed with this file, you can obtain one at
:: https://www.mozilla.org/en-US/MPL/2.0/
::

setlocal
set PWD=%cd%
cd %~dp0..
set BASEDIR=%cd%
IF NOT DEFINED EMS_CONFIG_DIR set EMS_CONFIG_DIR=%BASEDIR%\config-files
IF NOT DEFINED PAASAGE_CONFIG_DIR set PAASAGE_CONFIG_DIR=%BASEDIR%\config-files

:: Get IP addresses
set UTIL_FILE=util-4.0.0-SNAPSHOT-jar-with-dependencies.jar
set UTIL_PATH_0=util\target\%UTIL_FILE%
set UTIL_PATH_1=jars\util\%UTIL_FILE%
set UTIL_PATH_2=..\util\target\%UTIL_FILE%
set UTIL_PATH_3=.\%UTIL_FILE%
if exist %UTIL_PATH_0% (
    set UTIL_JAR=%UTIL_PATH_0%
) else (
	if exist %UTIL_PATH_1% (
		set UTIL_JAR=%UTIL_PATH_1%
	) else (
		if exist %UTIL_PATH_2% (
			set UTIL_JAR=%UTIL_PATH_2%
		) else (
			if exist %UTIL_PATH_3% (
				set UTIL_JAR=%UTIL_PATH_3%
			) else (
				echo ERROR: Couldn't find 'util-4.0.0-SNAPSHOT-jar-with-dependencies.jar'
				echo ERROR: Skipping keystore initialization
				goto the_end
			)
		)
	)
)
::echo UTIL_JAR location: %UTIL_JAR%

echo Resolving Public and Default IP addresses...
for /f %%i in ('java -jar %UTIL_JAR% -nolog public')  do set {PUBLIC_IP}=%%i
for /f %%i in ('java -jar %UTIL_JAR% -nolog default') do set {DEFAULT_IP}=%%i

IF "%{PUBLIC_IP}%" == "null" set {PUBLIC_IP}=127.0.0.1
IF "%{DEFAULT_IP}%" == "null" set {DEFAULT_IP}=127.0.0.1

echo PUBLIC_IP=%{PUBLIC_IP}%
echo DEFAULT_IP=%{DEFAULT_IP}%

:: Keystore initialization settings
set KEY_GEN_ALG=RSA
set KEY_SIZE=2048
set KEY_ALIAS=ems
set START_DATE=-1d
set VALIDITY=3650
set DN=CN=ems,OU=Information Management Unit (IMU),O=Institute of Communication and Computer Systems (ICCS),L=Athens,ST=Attika,C=GR
set EXT_SAN=SAN=dns:localhost,ip:127.0.0.1,ip:%{DEFAULT_IP}%,ip:%{PUBLIC_IP}%
set KEYSTORE=%EMS_CONFIG_DIR%\broker-keystore.p12
set TRUSTSTORE=%EMS_CONFIG_DIR%\broker-truststore.p12
set CERTIFICATE=%EMS_CONFIG_DIR%\broker.crt
set KEYSTORE_TYPE=PKCS12
set KEYSTORE_PASS=melodic

:: Keystores initialization
echo Generating key pair and certificate...
keytool -delete -alias %KEY_ALIAS% -keystore %KEYSTORE% -storetype %KEYSTORE_TYPE% -storepass %KEYSTORE_PASS% > nul 2>&1
keytool -genkey -keyalg %KEY_GEN_ALG% -keysize %KEY_SIZE% -alias %KEY_ALIAS% -startdate %START_DATE% -validity %VALIDITY% -dname "%DN%" -ext "%EXT_SAN%" -keystore %KEYSTORE% -storetype %KEYSTORE_TYPE% -storepass %KEYSTORE_PASS%

echo Exporting certificate to file...
del /Q %CERTIFICATE% > nul 2>&1
keytool -export -alias %KEY_ALIAS% -file %CERTIFICATE% -keystore %KEYSTORE% -storetype %KEYSTORE_TYPE% -storepass %KEYSTORE_PASS%

echo Importing certificate to trust store...
keytool -delete -alias %KEY_ALIAS% -keystore %TRUSTSTORE% -storetype %KEYSTORE_TYPE% -storepass %KEYSTORE_PASS% > nul 2>&1
keytool -import -noprompt -file %CERTIFICATE% -alias %KEY_ALIAS% -keystore %TRUSTSTORE% -storetype %KEYSTORE_TYPE% -storepass %KEYSTORE_PASS%

echo Key store, trust stores and certificate are ready.
:the_end
cd %PWD%
endlocal
