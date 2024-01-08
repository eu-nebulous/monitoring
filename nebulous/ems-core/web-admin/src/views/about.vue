<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <!-- Main content -->
    <section class="content">
      <div class="container-fluid" style="padding-left: 20px; padding-right: 20px;">
        <!-- Row #1 -->
        <div class="row">
          <div class="col-lg-12 col-12">
            <h5>EMS-SSE data:</h5>
            <p style="text-align: justify;">
                <vue-json-pretty :path="'res'" :data="modelValue" :showLength="true"></vue-json-pretty>
            </p>
            <hr/>
            <h5 align="center">The team:</h5>
            <p align="center">
              <img src="@/assets/img/party.gif" />
            </p>
          </div>
        </div>
      </div>
    </section>
</template>

<script>
import VueJsonPretty from 'vue-json-pretty';
import 'vue-json-pretty/lib/styles.css';

export default {
  props: {
    modelValue: Object,
  },
  components: { VueJsonPretty },
}
</script>