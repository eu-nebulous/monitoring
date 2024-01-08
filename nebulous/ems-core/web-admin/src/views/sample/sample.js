/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

/*
    vue-world-map:
        https://www.npmjs.com/package/vue-world-map
        https://github.com/Ghrehh/vue-world-map
    jquery-ui (for 'sortable' and 'resizable' widgets)
        https://www.npmjs.com/package/jquery-ui
        https://jqueryui.com/
    Geolocation:
        https://stackoverflow.com/questions/391979/how-to-get-clients-ip-address-using-javascript
        https://stackoverflow.com/questions/4937517/ip-to-location-using-javascript
        https://ipapi.co/
 */

import SmallBox from '@/components/smallbox/smallbox.vue'
import InfoBox from '@/components/infobox/infobox.vue'
import Card from '@/components/card/card.vue'

import VueGauge from 'vue-gauge';   // See https://www.npmjs.com/package/vue-gauge
import ChartJs from '@/components/chartjs/chartjs.vue';
import WorldMap from '@/components/worldmap/WorldMap.vue';
import Knob from '@/components/knob/knob.vue';
import Sparkline from '@/components/sparkline/sparkline.vue';
import JVectorMap from '@/components/jvectormap/jvectormap.vue';
//import JQVMap from '@/components/jqvmap/jqvmap.vue';     // Not working; use jvectormap

import StatusLed from '@/components/status-led/status-led.vue';
import SevenSegment from '@/components/7seg/7seg.vue';

var $ = require('jquery');
require('jquery-ui/ui/widgets/resizable.js');
require('jquery-ui/ui/widgets/sortable.js');
require('jquery-ui/themes/base/resizable.css');
require('jquery-ui/themes/base/sortable.css');

const EMS_DATA_PREFIX = '_ems';

export default {
  name: 'Sample Dashboard',
  props: {
    modelValue: Object
  },
  emits: ['update:modelValue'],

  components: {
    SmallBox, InfoBox, Card,
    VueGauge, ChartJs, WorldMap,
    Knob, Sparkline, JVectorMap, //JQVMap,
    StatusLed, SevenSegment,
  },
  mounted() {
    // Make widgets sortable (i.e. movable)
    $('section.content div.row').sortable({
        placeholder: 'sort-highlight',
        connectWith: 'section.content div.row',
        handle: '.card-header, .nav-tabs',
        forcePlaceholderSize: true,
        zIndex: 999999
    });
    $('.connectedSortable .card-header').css('cursor', 'move');

    // Make card widgets resizable (not working well with maps)
    $('.card').resizable({
        handles: 'all',
    });
  },

  data() {
    return {
      ems: { },
      clientsData: { },
      clients: [
        {id:'#00000', name:'vm-0000-xxxxx-323', address:'147.102.17.76', loc:'US-west', status:'Up', stats:{cpu:.85,mem:.38,events:345,uptime:12421432}},
        {id:'#00001', name:'vm-1111-yyyyy-323', address:'8.8.8.8', loc:null, status:'Down', stats:'todo'},
        {id:'#00002', name:'vm-2222-zzzzz-323', address:'79.166.188.138', loc:'Germany', stats:'todo'},
      ],
      geolocationCache: {
        //"147.102.17.76": {
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
        }
      },
      clientsPerCountry: {
          US: 1,
          CA: 7,
          GB: 14,
      },
      knobs: {
        k1:30, k2:70, k3:-80, k4:40, k5:90, k6:50,
        k7:30, k8:30, k9:30, k10:30,
        k11:80, k12:60, k13:10, k14:100,
      }
    };
  },


  watch: {
    modelValue: {
        immediate: true,
        handler(newVal) {
            if (newVal.data) {
                if (newVal.data.ems) this.ems = newVal.data.ems;
                if (newVal.data.clientsData) this.clientsData = newVal.data.clientsData;
            }
        },
    },
    ems: function(newVal) {
        // Flatten EMS server data
        let _flattened_data = {};
        this.flattenData(newVal, EMS_DATA_PREFIX, _flattened_data);
        //console.log('Dashboard: FLATTENED_DATA: ', _flattened_data);
        Object.assign(newVal, _flattened_data);

        // Convert bytes to MB and timestamps to W3C format
        this.prepareData(newVal);
        //console.log('Dashboard: FINAL_DATA: ', newVal);
    },
    clientsData: function(newVal) {
        console.log('>>>>> clientsData: newVal: ', newVal);

        this.clients = [];
        for (let k in newVal['client-metrics']) {
            if (k.startsWith('#')) {
                let v = newVal['client-metrics'][k];
                //console.log(k, '=>', v);
                let info = v['client-info'];
                let c = {
                    id: k,
                    name: '-',
                    address: info['ip-address'],
                    loc: '-',
                    status: 'Up',
                    stats: { cpu:.85, mem:.38, events:v['count-total-events'], uptime:12421432}
                };
                this.clients.push(c);
            }
        }

        this.addGeolocationInfo(this.clients, this.geolocationCache);
    }
  },

  methods: {
    flattenData(data, prefix, _flattened_data) {
        if (data==null) return;
        prefix = prefix.trim();
        if (typeof(data)==='object') {
            for (const [key, value] of Object.entries(data)) {
                var new_prefix = (prefix!=='')
                        ? prefix+'-'+key.replace('_','-')
                        : key.replace('_','-');
                this.flattenData(value, new_prefix, _flattened_data);
            }
        } else {
            _flattened_data[prefix] = ''+data;
        }
    },
    prepareData(data) {
        this.toIsoFormat2(data, '_ems-system-info-jvm-uptime', 'sec', 'time');
        this.toMB(data, '_ems-system-info-jvm-memory-free');
        this.toMB(data, '_ems-system-info-jvm-memory-max');
        this.toMB(data, '_ems-system-info-jvm-memory-total');
    },
    toIsoFormat(data, inUnit, outPart) {
        let mult = inUnit==='s' || inUnit==='sec' ? 1000 : 1;
        let start = 0;
        let len = 100;
        if (outPart==='time') { start = 11; len = 8; }
        if (outPart==='time+frac') { start = 11; }
        if (outPart==='frac' || outPart==='fraction') { start = 19; len = 4; }
        if (outPart==='date') { start = 0; len = 10; }
        if (outPart==='datetime') { start = 0; len = 19; }
        if (outPart==='tz' || outPart==='timezone') { start = 23; len = 1; }
        return new Date(data * mult).toISOString().substr(start, len);
    },
    toIsoFormat2(data, _key, inUnit, outPart) {
        if (data[_key])
                data[_key] = this.toIsoFormat(data[_key], inUnit, outPart);
        //console.log(data[_key]);
    },
    toMB(data, _key) {
        if (data[_key])
            data[_key] = Math.round(data[_key] / 1024 / 1024).toString();
    },
    addGeolocationInfo(dataArray, cache) {
        dataArray.forEach(c => {
            this.updateGeolocationInfoByIpAddress(c.address, c, cache);
        });
    },
    updateGeolocationInfoByIpAddress(ipAddress, obj, cache) {
        // get cached info (if available)
        if (cache[ipAddress]) {
            this.updateGeolocationInfo(obj, cache[ipAddress]);
            return;
        }

        // call geolocation service
        $.getJSON("https://ipapi.co/"+ipAddress+"/json")
        .done(function(json) {
            console.log('AJAX: done: ', json);
            // cache geolocation info
            cache[ipAddress] = json;

            this.updateGeolocationInfo(obj, json);
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

        obj.geo = json;
        obj.lat = json.latitude;
        obj.lon = json.longitude;
        obj.country = json.country_name;

        if (emitEvent) {
            //XXX: TODO: Add emit update event in 'updateGeolocationInfoByIpAddress'
            //this.$emit('update:clientsValue', dataArray);
        }
    },
  },
}
