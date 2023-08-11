<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
    See: https://www.keshikan.net/fonts-e.html
-->

<template>
    <div :id="id" :class="[useWrapper?'seven-seg-display-wrapper':'']" :style="[ colorBackground ? `background-color: ${colorBackground};` : '', useWrapper ? wrapperStyle : '']">
        <span v-if="useOffShade" :class="['seven-seg-display-background', 'D7MBI', classes]" :style="[ colorOff ? `opacity: 1; color: ${colorOff};` : '' ]"></span>
        <span :class="['seven-seg-display-front', 'D7MBI', classes]" :style="[ colorOn ? `color: ${colorOn};` : '' ]"><slot></slot></span>
    </div>
</template>

<script>
var $ = require('jquery');

export default {
    name: 'Seven segment display',
    props: {
        id: String,
        classes: String,
        useWrapper: Boolean,
        wrapperStyle: String,
        useOffShade: { type: Boolean, default: true },
        colorOn: String,
        colorOff: String,
        colorBackground: String,
    },
    mounted() {
        this.refreshDisplay();
    },
    updated() {
        this.refreshDisplay();
    },
    methods: {
        refreshDisplay() {
            try {
                let slotContent = this.$el.children[1].innerHTML;
                let newSlot = $(`<div>${slotContent}</div>`);
                let textNodes = newSlot.find(":not(iframe)").addBack().contents().filter(function() {
                    return this.nodeType === 3; //Node.TEXT_NODE
                });
                for (let i=0; i<textNodes.length; i++) {
                    textNodes[i].nodeValue = textNodes[i].nodeValue.replace(/[^:. ]/g,'8');
                }
                this.$el.children[0].innerHTML = newSlot.html();
                //console.log('FRONT ', this.$el.children[1].innerHTML);
                //console.log('BACK  ', this.$el.children[0].innerHTML);
            } catch (e) {
                console.warn('7seg: Exception caught: ', e);
            }
        }
    }
}
</script>

<style>
@font-face {
    font-family: "D7MBI";
    src: url("./DSEG7-Modern/DSEG7Modern-BoldItalic.woff2") format('woff2');
}

@font-face {
    font-family: "D14MBI";
    src: url("./DSEG14-Modern/DSEG14Modern-BoldItalic.woff2") format('woff2');
}

.D7MBI {
    font-family: "D7MBI";
}

.D14MBI {
    font-family: "D14MBI";
}

.seven-seg-display-wrapper {
    position: relative;
	border: 6px solid #000;
	border-radius: 9px;
	color: black;
	background-color: #fb7c00;
	box-shadow: 4px 4px 1px 0px rgba(0,0,0,0.1) inset;
}

.seven-seg-display-background {
	position: absolute;
	opacity: 0.15;
}

.seven-seg-display-front {
	position: absolute;
}

.red-display, .green-display, .blue-display, .yellow-display, .orange-display, .magenta-display, .white-display {
    background-color: rgb(36,30,30);
    border: 1px solid black;
    border-radius: 3px;
}

.red-display { color: rgb(255,44,19); }
.red-display > .seven-seg-display-background { opacity: 1; color: rgb(139,22,5); }

.green-display { color: rgb(36,221,34); }
.green-display > .seven-seg-display-background { opacity: 1; color: rgb(47,95,15); }

.blue-display { color: rgb(20,247,247); }
.blue-display > .seven-seg-display-background { opacity: 1; color: rgb(10,97,97); }

.yellow-display { color: rgb(255,232,52); }
.yellow-display > .seven-seg-display-background { opacity: 1; color: rgb(79,78,13); }

.orange-display { color: rgb(233,93,15); }
.orange-display > .seven-seg-display-background { opacity: 1; color: rgb(75,30,5); }

.magenta-display { color: rgb(222,20,245); }
.magenta-display > .seven-seg-display-background { opacity: 1; color: rgb(107,9,117); }

.white-display { color: rgb(255,255,255); }
.white-display > .seven-seg-display-background { opacity: 1; color: rgb(69,69,69); }
</style>