<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <div style="margin: 10px;">
        <EasyDataTable
                :headers="headers"
                :items="items"
                alternating
                border-cell
                :filter-options="filterOptions"
                buttons-pagination
                :hide-rows-per-page="true"
                sort-by="counter"
                sort-type="desc"
                multi-sort
                :rows-per-page="5"
                :table-height="height"
        >
            <template #header-destination="header">
                <div class="filter-column">
                    <i class="fas fa-filter" @click.stop="showDestinationFilter=!showDestinationFilter" />
                    {{ header.text }}
                    <div class="filter-menu filter-destination-menu" v-if="showDestinationFilter">
                        <select
                                class="destination-selector"
                                v-model="destinationCriteria"
                                name="destination"
                        >
                            <option value="*">
                                (all)
                            </option>
                            <option v-for="d in destinations" :key="d" :value="d">
                                {{d}}
                            </option>
                        </select>
                    </div>
                </div>
            </template>

            <template #item-payload="{ payload }">
                <table>
                    <tr v-for="(value, key) of payload" :key="key">
                        <td align="right" style="padding: 0px 10px; font-weight: bold;">{{key}}:</td>
                        <td><em>{{ key!=='timestamp' ? value : new Date(value).toISOString().replace('T',' ') }}</em></td>
                    </tr>
                </table>
            </template>
            <template #item-properties="{ properties }">
                <table>
                    <tr v-for="(value, key) of properties" :key="key">
                        <td align="right" style="padding: 0px 10px; font-weight: bold;">{{key}}:</td>
                        <td><em>{{value}}</em></td>
                    </tr>
                </table>
            </template>
            <template #item-timestamp="{ timestamp }"> {{new Date(timestamp).toISOString().replace('T',' ')}} </template>
        </EasyDataTable>
    </div>
</template>

<script>
//const $ = require('jquery');

import EasyDataTable from 'vue3-easy-data-table';
import 'vue3-easy-data-table/dist/style.css';

const HEADERS = [
                  { text: "Count", value: "counter", sortable: true, width: 20 },
                  { text: "Topic", value: "destination", sortable: true },
                  { text: "Payload", value: "payload", sortable: true, width: 300 },
                  { text: "Properties", value: "properties", sortable: true },
                  { text: "Timestamp", value: "timestamp", sortable: true, width: 50 },
                ];

export default {
    name: 'Latest Events widget',
    props: {
        emsData: Object,
        mode: { type: String, default: 'metric' },
        height: Number,
    },
    data() {
        return {
            destinationCriteria: '*'
        };
    },
    components: {
        EasyDataTable
    },
    computed: {
        headers: function() {
            return HEADERS;
        },
        items: function() {
            if (this.mode=='metric') {
                if (!this.emsData || !this.emsData['broker-cep'] || !this.emsData['broker-cep']['latest-events']) return [];
                return [...this.emsData['broker-cep']['latest-events']];
            } else
            if (this.mode=='bus') {
                if (!this.emsData || !this.emsData['control'] || !this.emsData['control']['latest-bus-events']) return [];
                let map = [...this.emsData['control']['latest-bus-events']];
                map.forEach(item => {
                    if (item.properties && item.properties.sender)
                        item.properties.sender = item.properties.sender.split('$')[0];
                });
                return map;
            }
            return [];
        },
        destinations: function() {
            return [ ...new Set(this.items.map(i => i.destination)) ].sort();
        },
        filterOptions: function() {
            return [ {
                field: 'destination',
                comparison: this.destinationCriteria=='*' ? '!=' : '=',
                criteria: this.destinationCriteria,
            } ];
        }
    },
    methods: {
    },
}
</script>