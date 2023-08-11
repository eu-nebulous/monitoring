<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <SevenSegmentDisplay :useOffShade="true">
        {{time}}
    </SevenSegmentDisplay>
</template>

<script>
import SevenSegmentDisplay from '@/components/7seg/7seg.vue';

export default {
    name: 'Digital clock',
    props: {
        hasSeconds: Boolean
    },
    components: { SevenSegmentDisplay },
    data() {
        return {
            time: '',
        };
    },
    mounted() {
        this.interval = setInterval(() => {
            let dt = new Date();
            let sep = dt.getMilliseconds()<500 ? ':' : ' ';
            this.time =
                    new String(100+dt.getHours()).substr(1) + sep +
                    new String(100+dt.getMinutes()).substr(1);
            if (this.hasSeconds)
                this.time +=
                    sep + new String(100+dt.getSeconds()).substr(1);
        }, 500);
    },
    beforeUnmount() {
        clearInterval(this.interval);
    }
}
</script>