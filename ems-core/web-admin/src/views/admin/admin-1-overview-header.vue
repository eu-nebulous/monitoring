<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <transition name="fade">
        <div v-if="showHeader" class="row align-items-right h-100">

            <!-- EMS server Uptime display -->
            <div class="col-1" style="padding-left: 0; min-width: 180px;">
                <span style="font-size:8pt; position:absolute; top:-10px; left:3px;">days</span>
                <span style="font-size:8pt; position:absolute; top:-10px; left:40%;">hh:mm:ss</span>
                <SevenSegment classes="D14MBI">
                    <span style="font-size: 18px;">{{getUptime()[0]}}</span>
                    <span style="font-size: 18px; opacity: 0;">-</span>
                    <span style="font-size: 24px;">{{getUptime()[1]}}</span>
                </SevenSegment>
            </div>

            <!-- EMS ready state lights -->
            <div class="col-1 text-center container" style="white-space: nowrap; padding: 0 5px; margin: 0 1px; min-width: 120px;">
                <div class="row">
                    <StatusLed :colorOn="stateLedColor" :on="stateLedOn" :blink="stateLedBlink" :width="24" :height="24" blinkPeriod="1s"></StatusLed>
                    <StatusLed :colorOn="stateLedColor" :on="stateLedOn" :blink="stateLedBlink" :width="24" :height="24" blinkPeriod="1s"></StatusLed>
                    <StatusLed :colorOn="stateLedColor" :on="stateLedOn" :blink="stateLedBlink" :width="24" :height="24" blinkPeriod="1s"></StatusLed>
                </div>
                <div class="row" style="margin-top: 5px; font-size: 8pt;">{{emsStateMessage}}</div>
            </div>

            <!-- EMS server last X-minutes CPU,Mem,Disk usage sparklines -->
            <div class="col-1 text-center section-header-group row">
                <div class="col-3 container">
                    <span class="row" style="font-size:70%;">CPU</span>
                    <div class="row">{{currentUsage('cpu',1)+'%'}}</div>
                </div>
                <div class="col-9">
                    <Sparkline type="line" width="100%" height="40px" :values="usageData('cpu')"></Sparkline>
                </div>
            </div>
            <div class="col-1 text-center section-header-group row">
                <div class="col-3 container">
                    <span class="row" style="font-size:70%;">Mem</span>
                    <div class="row">{{currentUsage('ram',0)+'%'}}</div>
                </div>
                <div class="col-9">
                    <Sparkline type="line" width="100%" height="40px" :values="usageData('ram')"
                               :options="{ chartRangeMin: 0, chartRangeMax: 100 }"
                    ></Sparkline>
                </div>
            </div>
            <div class="col-1 text-center section-header-group row">
                <div class="col-3 container">
                    <span class="row" style="font-size:70%;">Disk</span>
                    <div class="row" style="font-size:70%; white-space: nowrap;">{{currentUsage('disk',0)}} GB</div>
                </div>
                <div class="col-9">
                    <Sparkline type="line" width="100%" height="40px" :values="usageData('disk')"
                               :options="{ chartRangeMin: 0, chartRangeMax: 100 }"
                    ></Sparkline>
                </div>
            </div>

            <!--<div class="col-3">
                <div class="row align-items-center h-100 section-header-group">
                    <vue-gauge :refid="'gauge_h1'" :options="{'needleValue':85, 'chartWidth':60, 'arcDelimiters':[10,36,78], 'arcColors':['green','yellow','orange','red'], 'arcOverEffect':true, 'hasNeedle':true}"></vue-gauge>
                    <vue-gauge :refid="'gauge_h2'" :options="{'needleValue':85, 'chartWidth':60, 'arcDelimiters':[10,36,78], 'arcColors':['green','yellow','orange','red'], 'arcOverEffect':true, 'hasNeedle':true}"></vue-gauge>
                    <vue-gauge :refid="'gauge_h3'" :options="{'needleValue':85, 'chartWidth':60, 'arcDelimiters':[10,36,78], 'arcColors':['green','yellow','orange','red'], 'arcOverEffect':true, 'hasNeedle':true}"></vue-gauge>
                </div>
            </div>-->

            <!-- EMS server instant CPU,Mem,Disk usage lines -->
            <div class="col-2">
                <div class="row align-items-center h-100 section-header-group">
                    <div class="w-75">
                        <div class="progress-group" style="margin: 4px 0;">
                            <div class="progress progress-sm" style="height: 5px;" :title="'CPU: '+currentUsage('cpu',1)+'%'">
                                <div class="progress-bar bg-primary" :style="'width: '+currentUsage('cpu',0)+'%'"></div>
                            </div>
                        </div>
                        <!-- /.progress-group -->
                        <div class="progress-group" style="margin: 4px 0;">
                            <div class="progress progress-sm" style="height: 5px;" :title="'Memory: '+currentUsage('ram',0)+'%'">
                                <div class="progress-bar bg-danger" :style="'width: '+currentUsage('ram',0)+'%'"></div>
                            </div>
                        </div>
                        <!-- /.progress-group -->
                        <div class="progress-group" style="margin: 4px 0;">
                            <div class="progress progress-sm" style="height: 5px;" :title="'Disk: '+currentUsage('disk',1)+'GB'">
                                <div class="progress-bar bg-success" :style="'width: '+currentUsage('disk',0)+'%'"></div>
                            </div>
                        </div>
                        <!-- /.progress-group -->
                        <div class="progress-group" style="margin: 4px 0;">
                            <div class="progress progress-sm" style="height: 5px;" title="unused">
                                <div class="progress-bar bg-warning" style="width: 50%"></div>
                            </div>
                        </div>
                        <!-- /.progress-group -->
                    </div>
                </div>
            </div>

            <!-- Info boxes with Totals -->
            <div class="col-1 text-center container" style="white-space: nowrap; padding: 0 5px; margin: 0 1px;">
                <InfoBox
                        message="Nodes"
                        classes="section-header-info-box"
                        bg_class="bg-danger" icon_classes="far fa-lightbulb fa-xs"
                        :value="numOfNodes.toString()"
                />
            </div>
            <div class="col-1 text-center container" style="white-space: nowrap; padding: 0 5px; margin: 0 1px;">
                <InfoBox
                        message="Clients"
                        classes="section-header-info-box"
                        bg_class="bg-warning" icon_classes="far fa-flag fa-xs"
                        :value="numOfClients.toString()"
                />
            </div>
            <div class="col-1 text-center container" style="white-space: nowrap; padding: 0 5px; margin: 0 1px;">
                <InfoBox
                        message="Events"
                        classes="section-header-info-box"
                        bg_class="bg-success" icon_classes="far fa-paper-plane fa-xs"
                        :value="numOfEvents.toString()"
                />
            </div>
        </div>
    </transition>
</template>

<script>
import SevenSegment from '@/components/7seg/7seg.vue';
import StatusLed from '@/components/status-led/status-led.vue';
import Sparkline from '@/components/sparkline/sparkline.vue';
//import VueGauge from 'vue-gauge';
import InfoBox from '@/components/infobox/infobox.vue';

import utils from '@/utils.js';

export default {
    name: 'Header of Overview section',
    props: {
        modelValue: Object,
        sseInfo: Object,
        timeseries: Object,
        showHeader: Boolean
    },
    components: { SevenSegment, StatusLed, Sparkline, /*VueGauge,*/ InfoBox },
    data() {
        return {
            dataWindow: 60,
            emsState: 'IDLE',
            emsStateMessage: null,
            stateLedColor: '#D8D8D8',
            stateLedOn: false,
            stateLedBlink: false,
            numOfNodes: 0,
            numOfClients: 0,
            numOfEvents: 0,
        };
    },
    watch: {
        modelValue: {
            immediate: true,
            handler(newValue) {
                // Set status LEDs if SSE state is not OK or errors occurred
                // SSE state/errors overide Control state
                if (this.sseInfo.errors>0) {
                    this.stateLedColor = '#F00'; this.stateLedOn = true; this.stateLedBlink = true;
                    return;
                } else
                if (this.sseInfo.status!=='ok') {
                    this.stateLedColor = '#D8D8D8'; this.stateLedOn = false; this.stateLedBlink = false;
                    return;
                }

                // Set status LEDs based on Control state
                if (utils.valueExists(newValue, 'control.current-ems-state')) {
                    let state = utils.getValue(newValue, 'control.current-ems-state');
                    this.emsState = state;
                    this.emsStateMessage = utils.getValue(newValue, 'control.current-ems-state-message');
                    switch (state) {
                        case 'IDLE':
                            this.stateLedColor = '#F00'; this.stateLedOn = true; this.stateLedBlink = false; break;
                        case 'INITIALIZING':
                            this.stateLedColor = '#FFFF33'; this.stateLedOn = true; this.stateLedBlink = true; break;
                        case 'RECONFIGURING':
                            this.stateLedColor = '#0F0'; this.stateLedOn = true; this.stateLedBlink = true; break;
                        case 'READY':
                            this.stateLedColor = '#0F0'; this.stateLedOn = true; this.stateLedBlink = false; break;
                        case 'ERROR':
                            this.stateLedColor = '#F00'; this.stateLedOn = true; this.stateLedBlink = true; break;
                        default:
                            this.stateLedColor = '#D8D8D8'; this.stateLedOn = false; this.stateLedBlink = false;
                    }
                }

                // Update values of Section Header info boxes
                this.numOfNodes = newValue['baguette-server']['active-clients-list'].length + newValue['baguette-server']['passive-clients-list'].length;
                this.numOfClients = newValue['baguette-server']['active-clients-list'].length;
                this.numOfEvents = newValue['broker-cep']['count-total-events'];
            }
        },
        sseInfo(newVal) {
            if (newVal.errors>0) {
                this.stateLedColor = '#F00'; this.stateLedOn = true; this.stateLedBlink = true;
            } else
            if (newVal.status!=='ok') {
                this.stateLedColor = '#D8D8D8'; this.stateLedOn = false; this.stateLedBlink = false;
            }
        }
    },
    methods: {
        getUptime() {
            if (this.sseInfo && this.sseInfo.status==='error')
                return ['', 'Error'];
            let uptime = utils.getValue(this.modelValue, 'system-info.jmx-resource-metrics.jvm-uptime');
            let days = Math.floor(uptime/86400);
            let time = uptime - 86400 * days;
            return [ days, utils.toIsoFormat(time, 's', 'time') ];
        },
        currentUsage(metric, precision) {
            if (!this.timeseries) return '--';
            let v = this.timeseries.getLast();
            if (!v || !(metric in v)) return '--';
            return v[metric].toFixed(precision);
        },
        usageData(metric) {
            if (!this.timeseries) return [ 0 ];
            return this.timeseries.getWindowData(this.dataWindow).map(data => (data && (metric in data)) ? data[metric] : 0);
        },
    },
}
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
    transition: opacity .5s ease;
}

.fade-enter-from, .fade-leave-to {
    opacity: 0;
}

.section-header-group {
    border: 1px inset rgba(200,200,200,.5);
    /*opacity: .5;*/
    border-radius: 5px;
    padding: 0 5px;
    margin: 0 1px;
}

.section-header-info-box {
    margin: 0;
    padding: 0;
    min-height: 20px;
    height: 40px;
    width: 85px;
    font-size: 8pt;
    line-height: 1.0;
    background: rgba(255,255,255,.4);
}
</style>
