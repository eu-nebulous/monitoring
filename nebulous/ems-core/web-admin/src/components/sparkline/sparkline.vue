<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
    See: https://omnipotent.net/jquery.sparkline/#s-about
         https://www.npmjs.com/package/jquery-sparkline
    Help: https://omnipotent.net/jquery.sparkline/2.1.2/jquery.sparkline.js
-->

<template>
    <span :id="id??sid" :class="classes"></span>
</template>

<script>
var $ = require('jquery');
require('jquery-sparkline');

export default {
    name: 'sparkline',
    props: {
        id: String,
        type: String,
        classes: String,
        width: String,
        height: String,
        values: Object,
        options: Object
    },
    data() {
        return {
            sid: 'sparkline_'+(Math.floor(Math.random()*1000000000))
        }
    },
    mounted() {
        this.updateSparkline(this.values);
    },
    methods: {
        updateSparkline(_values) {
            let _id = this.id ?? this.sid;
            if (this.type!=='composite') {
                this.createSparkline(_id, _values, this, this.options, false);
            } else {
                var c;
                var p = false;
                for (c in _values) {
                    this.createSparkline(_id, _values[c], this, this.options[c], p);
                    p = true;
                }
            }
        },
        createSparkline(_id, _values, _props, _options, _composite) {
            let _conf = {
                type: _props.type,
                width: _props.width,
                height: _props.height,
            };
            if (_composite)
                _conf['composite'] = true;
            _conf = Object.assign(_conf, _options);
            $("#"+_id).sparkline(_values, _conf);
        }
    },
    watch: {
        values: {
            immediate: true,
            handler(newVal) {
                this.updateSparkline(newVal);
            }
        }
    }
}
</script>