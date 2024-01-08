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
IF NOT DEFINED EMS_CONFIG_DIR set EMS_CONFIG_DIR=%BASEDIR%\config-files
IF NOT DEFINED PAASAGE_CONFIG_DIR set PAASAGE_CONFIG_DIR=%BASEDIR%\config-files
IF NOT DEFINED JARS_DIR set JARS_DIR=%BASEDIR%\control-service\target

if NOT DEFINED EMS_SECRETS_FILE set EMS_SECRETS_FILE=%EMS_CONFIG_DIR%\secrets.properties
if NOT DEFINED EMS_CONFIG_LOCATION set EMS_CONFIG_LOCATION=optional:file:%EMS_CONFIG_DIR%\ems-server.yml,optional:file:%EMS_CONFIG_DIR%\ems-server.properties,optional:file:%EMS_CONFIG_DIR%\ems.yml,optional:file:%EMS_CONFIG_DIR%\ems.properties,optional:file:%EMS_SECRETS_FILE%

:: Read JASYPT password (decrypts encrypted configuration settings)
::set JASYPT_PASSWORD=password
if "%JASYPT_PASSWORD%"=="" (
    set /p JASYPT_PASSWORD="Configuration Password: "
)

java -Djasypt.encryptor.password=%JASYPT_PASSWORD% -cp %JARS_DIR%\control-service.jar -Dloader.main=jwt.util.gr.iccs.imu.ems.control.JwtTokenUtil -Dlogging.level.ROOT=WARN -Dlogging.level.gr.iccs.imu.ems.util=ERROR "-Dspring.config.location=%EMS_CONFIG_LOCATION%" org.springframework.boot.loader.launch.PropertiesLauncher %*
set exitcode=%ERRORLEVEL%

cd %PWD%
endlocal && SET exitcode=%exitcode%
exit /B %exitcode%