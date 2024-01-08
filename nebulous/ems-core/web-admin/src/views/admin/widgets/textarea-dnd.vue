<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <textarea :id="id" :placeholder="placeholder" v-model="currentValue"
              ondragover="event.preventDefault()" @drop="onDrop"
    />
</template>

<script>
export default {
    name: 'Textarea with file drop',
    props: {
        id: String,
        value: { type: String, default: '', required: false },
        placeholder: String,
    },
    data() {
        return {
            currentValue: this.getDefaultValue()
        }
    },
    methods: {
        getDefaultValue() {
            let children = this.$slots.default && this.$slots.default();
            if (children && children.length>0) {
                let s = '';
                for (let i=0; i<children.length; i++) {
                    s += children[i].children;
                    s += ' ';
                }
                return s;
            }
            return this.value;
        },
        onDrop(e) {
            let target = e.target;

            let files = e.dataTransfer.files;
            if (files.length > 0) {
                let file = files[0];
                e.preventDefault();

                let reader = new FileReader();
                reader.onload = function(event) {
                    if (target.value.trim()==='' || confirm('Do you want to replace current content?'))
                        target.value = event.target.result;
                };
                reader.readAsText(file);

                return false;
            }
        }
    }
}
</script>