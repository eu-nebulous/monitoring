#
# Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

FROM python:3.9.18-slim

WORKDIR /app
RUN apt-get update && \
    apt-get install -y netcat-traditional && \
    apt-get install -y vim iputils-ping curl telnet && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/
RUN pip install -r requirements.txt

COPY simple-app.py /app/
COPY simple-prometheus-exporter.py /app/
COPY simple-prometheus-exporter.sh /app/
COPY entrypoint.sh /app/
RUN chmod + /app/*.sh

EXPOSE 8080
EXPOSE 9000

ENTRYPOINT [ "./entrypoint.sh" ]
#CMD ["client"]