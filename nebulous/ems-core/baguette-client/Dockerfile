#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless 
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at 
# https://www.mozilla.org/en-US/MPL/2.0/
#

ARG RUN_IMAGE=eclipse-temurin
ARG RUN_IMAGE_TAG=21.0.1_12-jre

# -----------------   Run image   -----------------
FROM $RUN_IMAGE:$RUN_IMAGE_TAG

# Install required and optional packages
#RUN apt-get update \
#    && apt-get install -y vim iputils-ping \
#    && rm -rf /var/lib/apt/lists/*

# Add an EMS user
ARG EMS_USER=emsuser
ARG EMS_HOME=/opt/baguette-client
RUN mkdir -p ${EMS_HOME} && \
    addgroup ${EMS_USER} && \
    adduser --home ${EMS_HOME} --no-create-home --ingroup ${EMS_USER} --disabled-password ${EMS_USER} && \
    chown ${EMS_USER}:${EMS_USER} ${EMS_HOME}

USER ${EMS_USER}
WORKDIR ${EMS_HOME}

# Setup environment
ARG EMS_CONFIG_DIR=${EMS_HOME}/conf
ARG JAVA_HOME=/opt/java/openjdk
ARG PATH=$JAVA_HOME/bin:$PATH
ARG INSTALLATION_PACKAGE=baguette-client-installation-package.tgz

# Copy Baguette Client files
COPY --chown=${EMS_USER}:${EMS_USER} target/$INSTALLATION_PACKAGE /tmp
RUN tar zxvf /tmp/$INSTALLATION_PACKAGE -C /opt && rm -f /tmp/$INSTALLATION_PACKAGE
COPY --chown=${EMS_USER}:${EMS_USER} conf/* ${EMS_HOME}/conf/

EXPOSE 61610
EXPOSE 61616
EXPOSE 61617

ENTRYPOINT ["/bin/sh", "-c", "/opt/baguette-client/bin/run.sh  &&  tail -f /opt/baguette-client/logs/output.txt"]