<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
    See: https://github.com/aterrien/jQuery-Knob
         https://hackthestuff.com/article/how-to-use-jquery-knob-in-your-website
-->

<template>
    <div :class="classWrapper">
        <input :id="id??kid" type="text" :data-skin="skin" :class="classInput" :value="value">
        <div v-if="label" :class="classLabel">{{label}}</div>
    </div>
</template>

<script>
var $ = require('jquery');
require('jquery-knob');

export default {
    name: 'knob',
    props: {
        id: String,
        value: Number,
        label: String,
        options: Object,
        skin: String,
        classWrapper: { type: String, default: 'knob-wrapper' },
        classInput: { type: String, default: 'knob' },
        classLabel: { type: String, default: 'knob-label' },
    },
    data() {
        return {
            kid: 'knob_'+(Math.floor(Math.random()*1000000000))
        }
    },
    mounted() {
        let _id = this.id ?? this.kid;
        let _conf = {
            readOnly: true,
            draw: function () {
                // "tron" case
                if (this.$.data('skin') == 'tron') {
                    var a = this.angle(this.cv)             // Angle
                            , sa = this.startAngle          // Previous start angle
                            , sat = this.startAngle         // Start angle
                            , ea                            // Previous end angle
                            , eat = sat + a                 // End angle
                            , r = true;

                    this.g.lineWidth = this.lineWidth;

                    this.o.cursor
                    && (sat = eat - 0.3)
                    && (eat = eat + 0.3);

                    if (this.o.displayPrevious) {
                        ea = this.startAngle + this.angle(this.value);
                        this.o.cursor
                        && (sa = ea - 0.3)
                        && (ea = ea + 0.3);
                        this.g.beginPath();
                        this.g.strokeStyle = this.previousColor;
                        this.g.arc(this.xy, this.xy, this.radius - this.lineWidth, sa, ea, false);
                        this.g.stroke();
                    }

                    this.g.beginPath();
                    this.g.strokeStyle = r ? this.o.fgColor : this.fgColor;
                    this.g.arc(this.xy, this.xy, this.radius - this.lineWidth, sat, eat, false);
                    this.g.stroke();

                    this.g.lineWidth = 2;
                    this.g.beginPath();
                    this.g.strokeStyle = this.o.fgColor;
                    this.g.arc(this.xy, this.xy, this.radius - this.lineWidth + 1 + this.lineWidth * 2 / 3, 0, 2 * Math.PI, false);
                    this.g.stroke();

                    return false;
                }
            }
        };
        _conf = Object.assign(_conf, this.options);
        $("#"+_id).knob(_conf);
    }
}
</script>

<style>
.knob {
}
.knob-label {
}
.knob-wrapper {
    text-align: center;
}
</style>