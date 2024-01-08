<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
  See: https://canvas-gauges.com/documentation/user-guide/
  and: https://codesandbox.io/s/7llvu?file=/src/components/WorldMap.vue
-->

<template>
  <div class="worldmap">
    <vue-world-map
      id="worldmap"
      v-bind:countryData="countryData"
      v-bind:defaultCountryFillColor="map.defaultCountryFillColor"
      v-bind:highColor="map.highColor"
      v-bind:countryStrokeColor="map.countryStrokeColor"
      v-bind:lowColor="map.lowColor"
    ></vue-world-map>
  </div>
</template>

<script>
import VueWorldMap from "vue-world-map";
import Countries from "./countries.js";

export default {
  components: {
    VueWorldMap,
  },
  props: {
    countryData: Object
  },

  data() {
    return {
      map: {
        defaultCountryFillColor: "white",
        highColor: "red",
        lowColor: "rgb(255,220,220)",
        countryStrokeColor: "grey",
      },

      countries: Countries,
      selected: null,
    };
  },
};
</script>

<style scoped>
#worldmap .vue-world-map {
}
</style>

<style>
.worldmap .vue-world-map {
    background-color: skyblue;
}
</style>