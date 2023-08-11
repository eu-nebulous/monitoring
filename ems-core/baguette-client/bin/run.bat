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
set PWD=%~dp0
cd %PWD%..
set BASEDIR=%cd%
IF NOT DEFINED EMS_CONFIG_DIR set EMS_CONFIG_DIR=%BASEDIR%\conf
IF NOT DEFINED PAASAGE_CONFIG_DIR set PAASAGE_CONFIG_DIR=%BASEDIR%\conf
IF NOT DEFINED EMS_CONFIG_LOCATION set EMS_CONFIG_LOCATION=optional:file:%EMS_CONFIG_DIR%\ems-client.yml,optional:file:%EMS_CONFIG_DIR%\ems-client.properties,optional:file:%EMS_CONFIG_DIR%\baguette-client.yml,optional:file:%EMS_CONFIG_DIR%\baguette-client.properties
IF NOT DEFINED JASYPT_PASSWORD set JASYPT_PASSWORD=password
set JAVA_HOME=%BASEDIR%/jre

:: Update path
set PATH=%JAVA_HOME%\bin;%PATH%

:: Copy dependencies if missing
if exist pom.xml (
    if not exist %BASEDIR%\target\dependency cmd /C "mvn dependency:copy-dependencies"
)

:: Run Baguette Client
set JAVA_OPTS= -Djavax.net.ssl.trustStore=%EMS_CONFIG_DIR%\client-broker-truststore.p12 ^
 -Djavax.net.ssl.trustStorePassword=melodic ^
 -Djavax.net.ssl.trustStoreType=pkcs12 ^
 -Djasypt.encryptor.password=%JASYPT_PASSWORD% ^
 --add-opens=java.base/java.lang=ALL-UNNAMED  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
::set JAVA_OPTS=-Djavax.net.debug=all %JAVA_OPTS%
::set JAVA_OPTS=-Dlogging.level.gr.iccs.imu.ems=TRACE %JAVA_OPTS%

echo EMS_CONFIG_DIR=%EMS_CONFIG_DIR%
echo EMS_CONFIG_LOCATION=%EMS_CONFIG_LOCATION%
echo Starting baguette client...
java %JAVA_OPTS% -classpath "%EMS_CONFIG_DIR%;%BASEDIR%\jars\*;%BASEDIR%\target\classes;%BASEDIR%\target\dependency\*" gr.iccs.imu.ems.baguette.client.BaguetteClient "--spring.config.location=%EMS_CONFIG_LOCATION%" "--logging.config=file:%EMS_CONFIG_DIR%\logback-spring.xml"  %*

cd %PWD%
endlocal