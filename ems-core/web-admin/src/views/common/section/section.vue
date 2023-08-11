<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <section class="section-container">
        <div class="section-background" :style="['background-color: '+background]"></div>
        <div class="section-foreground">
            <div class="section-header-wrapper row align-items-right h-100">
                <div class="col-2"><h3>{{title}}</h3></div>
                <div class="col-9">
                    <slot name="header"></slot>
                </div>
                <div class="col-1 text-right h3">
                    <i v-on:click="toggleOpen"
                       :class="['fas fa-angle-double-'+(open?'up':'down') ]"
                       style="cursor: pointer;"/>
                </div>
            </div>
            <div v-if="open" class="section-content-wrapper">
                <slot></slot>
            </div>
        </div>
    </section>
</template>

<script>
export default {
  name: 'Section',
  props: {
    title: String,
    background: { type:String, default:'rgba(221,221,221,.75)' },
    collapsed: { type:Boolean, default:false },
  },
  data() {
    return {
      open: !this.collapsed,
    }
  },
  methods: {
    toggleOpen() {
        this.open = !this.open;
        this.$emit( this.open ? 'expanded' : 'collapsed');
    }
  }
}
</script>

<style scoped>
  .section-container {
    position: relative;
    margin-bottom: 25px;
    padding: 10px 15px;
    /*opacity: 1;*/
    backdrop-filter: blur(3px) saturate(180%);
    -webkit-backdrop-filter: blur(3px) saturate(100%);
    /*transform: translate(20px, 20px);*/
  }
  .section-background {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 0;
    opacity: 1;
    -webkit-backdrop-filter: grayscale() blur(10px); /* Safari 9+ */
    backdrop-filter: grayscale() blur(10px); /* Chrome and Opera */
    box-shadow: 6px 6px 12px #555555, 0 0 10px #777777;
    border-top: 8px inset rgba(240,240,240,.3);
    border-bottom: 8px outset rgba(128,128,128,.3);
  }
  .section-foreground {
  }
  .section-header-wrapper {
    color: v-bind(background);
    filter: brightness(80%);
    text-shadow: 0px 1px 0px rgba(255,255,255,.3), 0px -1px 0px rgba(0,0,0,.7);
    margin-top: 10px;
    margin-bottom: 5px;
  }
  .section-content-wrapper {
  }
</style>