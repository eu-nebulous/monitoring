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
set EMS_HOME=%BASEDIR%
IF NOT DEFINED EMS_CONFIG_DIR set EMS_CONFIG_DIR=%BASEDIR%\config-files
:: IF NOT DEFINED PAASAGE_CONFIG_DIR set PAASAGE_CONFIG_DIR=%BASEDIR%\config-files
IF NOT DEFINED JARS_DIR set JARS_DIR=%BASEDIR%\control-service\target
IF NOT DEFINED LOGS_DIR set LOGS_DIR=%BASEDIR%\logs
IF NOT DEFINED PUBLIC_DIR set PUBLIC_DIR=%BASEDIR%\public_resources

:: Read JASYPT password (decrypts encrypted configuration settings)
::set JASYPT_PASSWORD=password
if "%JASYPT_PASSWORD%"=="" (
    set /p JASYPT_PASSWORD="Configuration Password: "
)
:: Use this online service to encrypt/decrypt passwords:
:: https://www.devglan.com/online-tools/jasypt-online-encryption-decryption

:: Check EMS configuration
if "%EMS_SECRETS_FILE%"=="" (
    set EMS_SECRETS_FILE=%EMS_CONFIG_DIR%\secrets.properties
)
if "%EMS_CONFIG_LOCATION%"=="" (
    set EMS_CONFIG_LOCATION=optional:classpath:rule-templates.yml,optional:file:%EMS_CONFIG_DIR%\ems-server.yml,optional:file:%EMS_CONFIG_DIR%\ems-server.properties,optional:file:%EMS_CONFIG_DIR%\ems.yml,optional:file:%EMS_CONFIG_DIR%\ems.properties,optional:file:%EMS_SECRETS_FILE%
)

:: Check logger configuration
if "%LOG_CONFIG_FILE%"=="" (
    set LOG_CONFIG_FILE=%EMS_CONFIG_DIR%\logback-conf\logback-spring.xml
)
echo Using logback config.: %LOG_CONFIG_FILE%
if "%LOG_FILE%"=="" (
    set LOG_FILE=%LOGS_DIR%\ems.log
)

:: Set shell encoding to UTF-8 (in order to display banner correctly)
chcp 65001

:: Create default models directory
mkdir %BASEDIR%\models

:: Run EMS server
rem Uncomment next line to set JAVA runtime options
rem set JAVA_OPTS=-Djavax.net.debug=all

set JAVA_ADD_OPENS=--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util.regex=ALL-UNNAMED --add-opens java.base/sun.nio.cs=ALL-UNNAMED --add-opens java.base/java.nio.charset=ALL-UNNAMED

java -version
chcp
echo EMS_CONFIG_DIR=%EMS_CONFIG_DIR%
echo EMS_CONFIG_LOCATION=%EMS_CONFIG_LOCATION%
echo IP address:
ipconfig  | findstr "/C:IPv4 Address"
echo Starting EMS server...
IF NOT DEFINED RESTART_EXIT_CODE set RESTART_EXIT_CODE=99
:_restart_ems

rem Check if fat-jar exists
if exist "%JARS_DIR%\control-service.jar" (
    set "CP=-cp %JARS_DIR%\control-service.jar"
    set "ESPER_PATH=%JARS_DIR%\esper-7.1.0.jar,"
)

rem Use when Esper is packaged in control-service.jar
rem java %EMS_DEBUG_OPTS% %JAVA_OPTS% %JAVA_ADD_OPENS% -Djasypt.encryptor.password=%JASYPT_PASSWORD% -Djava.security.egd=file:/dev/urandom -jar %JARS_DIR%\control-service.jar -nolog "--spring.config.location=%EMS_CONFIG_LOCATION%" "--logging.config=file:%LOG_CONFIG_FILE%"

rem Use when Esper is NOT packaged in control-service.jar
java %EMS_DEBUG_OPTS% %JAVA_OPTS% %JAVA_ADD_OPENS% ^
    -Djasypt.encryptor.password=%JASYPT_PASSWORD% ^
    -Djava.security.egd=file:/dev/urandom ^
    -Dscan.packages=%SCAN_PACKAGES% ^
    %CP% ^
    "-Dloader.path=%ESPER_PATH%%EXTRA_LOADER_PATHS%" ^
    org.springframework.boot.loader.launch.PropertiesLauncher ^
    -nolog ^
    "--spring.config.location=%EMS_CONFIG_LOCATION%" ^
    "--logging.config=file:%LOG_CONFIG_FILE%" ^
    %*

if errorlevel %RESTART_EXIT_CODE% (
    echo Restarting EMS server...
    goto :_restart_ems
)
echo EMS server exited

rem e.g. --spring.config.location=%EMS_CONFIG_DIR%\
rem e.g. --spring.config.name=application.properties

cd %PWD%
endlocal