<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <div class="input-group input-group-sm">
        <input :id="id" :data-row="row" type="text" :value="initValue" class="form-control"
               placeholder="Write a command" required/>
    </div>
</template>

<script>
export default {
    name: 'Command input widget',
    props: {
        id: String,
        row: Number,
        value: String,
    },
    data() {
        return {
            initValue: this.value,
            currValue: '',
        };
    },
    beforeUpdate() {
        this.currValue = this.$el.firstChild.value;
    },
    updated() {
        this.$el.firstChild.value = this.currValue;
        if (this.$el.firstChild.value==='') this.$el.firstChild.value = this.value;
    },
}
</script>