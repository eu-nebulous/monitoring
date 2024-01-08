<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
          <div class="row">
              <div class="col-6">
                  <Card bodyClasses="p-0"
                        header="Sample Vue-World-Map"
                        icon="fas fa-globe-americas"
                        footer="Footer"
                        :hasCollapse="true" :hasMaximize="true" :hasRemove="true"
                  >
                      <WorldMap :countryData="clientsPerCountry"></WorldMap>
                  </Card>
              </div>
              <!-- /.col-6 -->
              <div class="col-6">
                  <Card bodyClasses="p-0"
                        header="Sample JVectorMap"
                        icon="fas fa-map"
                        footer="Footer"
                        :hasCollapse="true" :hasMaximize="true" :hasRemove="true"
                  >
                      <JVectorMap height="425px"
                                  :options="{ backgroundColor:'navy' }"
                                  :markers="[
                                  { latLng: [41.90, 12.45], name: 'Vatican City' },
                                  { latLng: [43.73, 7.41], name: 'Monaco' },
                                  { latLng: [-0.52, 166.93], name: 'Nauru' },
                                  { latLng: [-8.51, 179.21], name: 'Tuvalu' },
                                  { latLng: [43.93, 12.46], name: 'San Marino' },
                                  { latLng: [47.14, 9.52], name: 'Liechtenstein' },
                                  { latLng: [7.11, 171.06], name: 'Marshall Islands' },
                                  { latLng: [17.3, -62.73], name: 'Saint Kitts and Nevis' },
                                  { latLng: [3.2, 73.22], name: 'Maldives' },
                                  { latLng: [35.88, 14.5], name: 'Malta' },
                                  { latLng: [12.05, -61.75], name: 'Grenada' },
                                  { latLng: [13.16, -61.23], name: 'Saint Vincent and the Grenadines' },
                                  { latLng: [13.16, -59.55], name: 'Barbados' },
                                  { latLng: [17.11, -61.85], name: 'Antigua and Barbuda' },
                                  { latLng: [-4.61, 55.45], name: 'Seychelles' },
                                  { latLng: [7.35, 134.46], name: 'Palau' },
                                  { latLng: [42.5, 1.51], name: 'Andorra' },
                                  { latLng: [14.01, -60.98], name: 'Saint Lucia' },
                                  { latLng: [6.91, 158.18], name: 'Federated States of Micronesia' },
                                  { latLng: [1.3, 103.8], name: 'Singapore' },
                                  { latLng: [1.46, 173.03], name: 'Kiribati' },
                                  { latLng: [-21.13, -175.2], name: 'Tonga' },
                                  { latLng: [15.3, -61.38], name: 'Dominica' },
                                  { latLng: [-20.2, 57.5], name: 'Mauritius' },
                                  { latLng: [26.02, 50.55], name: 'Bahrain' },
                                  { latLng: [0.33, 6.73], name: 'São Tomé and Príncipe' }
                                ]"
                      ></JVectorMap>
                  </Card>
              </div>
              <!-- /.col-6 -->
          </div>
</template>

<script>
var $ = require('jquery');
import Card from '@/components/card/card.vue';
import WorldMap from '@/components/worldmap/WorldMap.vue';
import JVectorMap from '@/components/jvectormap/jvectormap.vue';

import utils from '@/utils.js';

export default {
    name: 'Admin Dashboard - Geography',
    components: { Card, WorldMap, JVectorMap },
    props: {
        modelValue: Object
    },

    watch: {
        modelValue: {
            immediate: true,
            handler(newVal) {
                if (!newVal) return;
                if (!utils.valueExists(newVal, 'baguette-server.active-clients-map')) return;

                let activeClientsMap = newVal['baguette-server']['active-clients-map'];
                let clients = Object.values(activeClientsMap);
                this.addGeolocationInfo(clients, this.geolocationCache);
            }
        }
    },

    methods: {
        addGeolocationInfo(dataArray, cache) {
            dataArray.forEach(c => {
                this.updateGeolocationInfoByIpAddress(c['ip-address'], c, cache);
            });
        },
        updateGeolocationInfoByIpAddress(ipAddress, obj, cache) {
            // get cached info (if available)
            if (cache[ipAddress]) {
                this.updateGeolocationInfo(obj, cache[ipAddress]);
                return;
            }

            // call geolocation service
            const _updFunc = this.updateGeolocationInfo;
            $.getJSON("https://ipapi.co/"+ipAddress+"/json")
            .done(function(json) {
                console.log('AJAX: done: ', ipAddress, json);
                // cache geolocation info
                cache[ipAddress] = json;

                _updFunc(obj, json);
            })
            .fail(function(jqxhr, textStatus, error) {
                console.log('AJAX: fail: ', jqxhr, textStatus, error);
            })
            .always(function(/*jqxhr, textStatus*/) {
                //console.log('AJAX: always: ', jqxhr, textStatus);
            });
        },
        updateGeolocationInfo(obj, json) {
            // check if changed
            let emitEvent = true;
            /*if (obj.lat && obj.lon && obj.country) {
                emitEvent = ! (obj.lat==json.latitude && obj.lon==json.longitude && obj.country==json.country_name);
            }*/

            //console.log('updateGeolocationInfo: ', obj, json);
            obj.geo = json;
            obj.lat = json.latitude;
            obj.lon = json.longitude;
            obj.country = (json.org??'')+', '+(json.city??'')+', '+(json.region??'')+', '+(json.country_name??'-');

            if (emitEvent) {
                //XXX: TODO: Add emit update event in 'updateGeolocationInfoByIpAddress'
                //this.$emit('update:clientsValue', dataArray);
            }
        },
    },

    data() {
        return {
            geolocationCache: {
                /*//"147.102.17.76": {
                "172.18.0.3": {
                    //"ip": "147.102.17.76",
                    "ip": "172.18.0.3",
                    "version": "IPv4",
                    "city": "Athens",
                    "region": "Attica",
                    "region_code": "I",
                    "country": "GR",
                    "country_name": "Greece",
                    "country_code": "GR",
                    "country_code_iso3": "GRC",
                    "country_capital": "Athens",
                    "country_tld": ".gr",
                    "continent_code": "EU",
                    "in_eu": true,
                    "postal": null,
                    "latitude": 37.9842,
                    "longitude": 23.7353,
                    "timezone": "Europe/Athens",
                    "utc_offset": "+0300",
                    "country_calling_code": "+30",
                    "currency": "EUR",
                    "currency_name": "Euro",
                    "languages": "el-GR,en,fr",
                    "country_area": 131940,
                    "country_population": 10727668,
                    "asn": "AS3323",
                    "org": "National Technical University of Athens"
                },
                "8.8.8.8": {
                    "ip": "8.8.8.8",
                    "version": "IPv4",
                    "city": "Mountain View",
                    "region": "California",
                    "region_code": "CA",
                    "country": "US",
                    "country_name": "United States",
                    "country_code": "US",
                    "country_code_iso3": "USA",
                    "country_capital": "Washington",
                    "country_tld": ".us",
                    "continent_code": "NA",
                    "in_eu": false,
                    "postal": "Sign up to access",
                    "latitude": "Sign up to access",
                    "longitude": "Sign up to access",
                    "timezone": "America/Los_Angeles",
                    "utc_offset": "-0700",
                    "country_calling_code": "+1",
                    "currency": "USD",
                    "currency_name": "Dollar",
                    "languages": "en-US,es-US,haw,fr",
                    "country_area": 9629091,
                    "country_population": 327167434,
                    "message": "Please message us at ipapi.co/trial for full access",
                    "asn": "AS15169",
                    "org": "GOOGLE"
                },
                "79.166.188.138": {
                    "ip": "79.166.188.138",
                    "version": "IPv4",
                    "city": "Athens",
                    "region": "Attica",
                    "region_code": "I",
                    "country": "GR",
                    "country_name": "Greece",
                    "country_code": "GR",
                    "country_code_iso3": "GRC",
                    "country_capital": "Athens",
                    "country_tld": ".gr",
                    "continent_code": "EU",
                    "in_eu": true,
                    "postal": null,
                    "latitude": 37.9842,
                    "longitude": 23.7353,
                    "timezone": "Europe/Athens",
                    "utc_offset": "+0300",
                    "country_calling_code": "+30",
                    "currency": "EUR",
                    "currency_name": "Euro",
                    "languages": "el-GR,en,fr",
                    "country_area": 131940,
                    "country_population": 10727668,
                    "asn": "AS3329",
                    "org": "Vodafone-panafon Hellenic Telecommunications Company SA"
                }*/
            },
            clientsPerCountry: {
                US: 1,
                CA: 7,
                GB: 14,
            },
        };
    },
}
</script>

<style scoped></style>
