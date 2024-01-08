<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <div class="ui-modal-backdrop" :id="id">
        <div class="ui-modal-container">
            <div class="ui-modal-container-background"></div>
            <div class="ui-modal-header-container clearfix">
                <div class="ui-modal-header"><slot name="header"></slot></div>
            </div>
            <div class="ui-modal-content-container">
                <div class="ui-modal-content"><slot></slot></div>
            </div>
            <div class="ui-modal-footer-container">
                <div class="ui-modal-footer"><slot name="footer"></slot></div>
            </div>
        </div>
    </div>
</template>

<script>
//var $ = require('jquery');

export default {
  name: 'modal',
  props: {
    id: { type: String, required: true },
    width: { type: String, default: '90%' },
    height: { type: String, default: '90%' },
  },
  mounted() {
    document.addEventListener('keyup', this.escapeKeyListener, true);
  },
  beforeUnmount() {
    document.removeEventListener('keyup', this.escapeKeyListener, true);
  },
  methods: {
    escapeKeyListener(event) {
      if (event.key === "Escape") {
        this.$emit('close-modal-request');
      }
    },
  },
}
</script>

<style>
.ui-modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 9999;
  display: flex;
  justify-content: center;
  align-items: center;
}
.ui-modal-container {
  position: absolute;
  /*top: 50%;
  transform: translate(0, -50%);
  */
  width: v-bind(width);
  height: v-bind(height);
  background-color: white;
  opacity: 1;
  border: 3px outset grey;
  border-radius: 10px;
  z-index: 10000;
  overflow: hidden;
}
.ui-modal-container-background {
  position: absolute;
  top: 0;
  left: 0;
  right:0;
  bottom: 0;
  /*background-image: url(/assets/img/backgrounds/computer-room-4.jpg);
  background-repeat: repeat;
  background-position: center center;*/
  opacity: .3;
  z-index: -1;
}

.ui-modal-header-container {
  position: relative;
  height: 40px;
  padding: 5px 10px;
  background-color: rgba(255,255,255,.2);
  border: 1px solid lightgrey;
  /*box-shadow: 1px 5px 5px rgba(0,0,0,.4);*/
  overflow: hidden;
}
.ui-modal-header {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: transparent;
  display: flex;
  justify-content: center;
  align-items: center;
}

.ui-modal-content-container {
  width: 100%;
  height: calc(100% - 80px);
  background-color: transparent;
  padding: 5px 10px;
  overflow: hidden;
}
.ui-modal-content {
  width: 100%;
  height: 100%;
  background-color: transparent;
  overflow: auto;
}

.ui-modal-footer-container {
  position: absolute;
  bottom: 0;
  width: 100%;
  height: 40px;
  padding: 5px 10px;
  background-color: rgba(0,0,0,.1);
  /*border-top: 1px solid grey;*/
  /*box-shadow: 5px -5px 5px rgba(0,0,0,.4);*/
  overflow: hidden;
}
.ui-modal-footer {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: transparent;
  display: flex;
  justify-content: center;
  align-items: center;
}

.clearfix::after {
  content: "";
  clear: both;
  display: table;
}
</style>