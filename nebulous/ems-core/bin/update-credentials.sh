#!/usr/bin/env bash
#
# Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
# Esper library is used, in which case it is subject to the terms of General Public License v2.0.
# If a copy of the MPL was not distributed with this file, you can obtain one at
# https://www.mozilla.org/en-US/MPL/2.0/
#

echo "Updating broker client credentials..."

# Generate new username/password pair
NEWUSERNAME=user-`< /dev/urandom tr -cd "[:alnum:]" | head -c 32`
NEWPASSWORD=`< /dev/urandom tr -cd "[:alnum:]" | head -c 32`
NEWCREDENTIALS="$NEWUSERNAME\/$NEWPASSWORD"
echo "-New username: $NEWUSERNAME"
echo "-New password: $NEWPASSWORD"
echo "-BCEP credentials: $NEWCREDENTIALS"

# Update all files passed as arguments
for file in "$@"
do
    printf " * Updating file %s..." $file
    # Updating the brokerclient style properties...
    sed -i "s/^\s*brokerclient.broker-username\s*[=:].*/brokerclient.broker-username=$NEWUSERNAME/" $file
    sed -i "s/^\s*brokerclient.broker-password\s*[=:].*/brokerclient.broker-password=$NEWPASSWORD/" $file
    # Updating the brokercep style properties...
    sed -i "s/^\s*brokercep.additional-broker-credentials\s*[=:].*/brokercep.additional-broker-credentials=$NEWCREDENTIALS/" $file
    echo "ok"
done

echo "done"
