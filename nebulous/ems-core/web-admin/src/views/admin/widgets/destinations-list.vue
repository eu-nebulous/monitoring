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
        <select :id="id" :data-row="row" :value="initValue" class="form-control form-control-sm">
            <option v-for="dest in destinations" :key="dest.value" :value="dest.value">{{dest.text}}</option>
        </select>
    </div>
</template>

<script>
export default {
    name: 'Topics List widget',
    props: {
        id: String,
        row: Number,
        selected: String,
        topics: Object,
        destinations: Array,
    },
    data() {
        return {
            initValue: this.selected,
            currValue: '',
        };
    },
    beforeUpdate() {
        this.currValue = this.$el.firstChild.value;
    },
    updated() {
        this.$el.firstChild.value = this.currValue;
        if (this.$el.firstChild.value==='') this.$el.firstChild.value = this.selected;
    },
}
</script>