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
        <div class="col-md-2 col-sm-6 col-12">
            <InfoBox
                    message="EMS Clients"
                    :value="getEmsClients()"
                    bg_class="bg-info" icon_classes="far fa-flag"
            />
        </div>
        <!-- /.col -->
        <div class="col-md-2 col-sm-6 col-12">
            <InfoBox
                    message="Edge nodes"
                    :value="getEdgeNodes()"
                    bg_class="bg-warning" icon_classes="far fa-lightbulb"
            />
        </div>
        <!-- /.col -->
        <div class="col-md-2 col-sm-6 col-12">
            <InfoBox
                    message="Ignored nodes"
                    :value="getIgnoredNodes()"
                    bg_class="bg-danger" icon_classes="fa fa-eye-slash"
            />
        </div>
        <!-- /.col -->
        <div class="col-md-2 col-sm-6 col-12">
            <InfoBox
                    message="Total Events"
                    :value="getTotalEvents()"
                    bg_class="bg-success" icon_classes="far fa-paper-plane"
            />
        </div>
        <!-- /.col -->
        <div class="col-md-2 col-sm-6 col-12">
            <InfoBox
                    :value="getUptime()"
                    message="Uptime"
                    bg_class="bg-aqua" icon_classes="ion ion-ios-timer-outline"
            />
        </div>
        <!-- /.col -->
        <div class="col-md-2 col-sm-6 col-12">
            <InfoBox
                    :value="getState()"
                    message="EMS State"
                    bg_class="bg-indigo" icon_classes="ion ion-ios-gear-outline"
            />
        </div>
        <!-- /.col -->
    </div>
    <!-- /.col -->


    <div class="row">
        <div class="col-3">
            <Card :header="`CPU usage in last ${dataWindow} seconds`" icon="fas fa-microchip">
                <div class="row align-items-center h-100">
                    <Sparkline type="composite" width="100%" height="80%" classes="col-10"
                               :values="usageDataAndLines('cpu')"
                               :options="optionsForDataAndLines('cpu')"></Sparkline>
                    <div class="col-2">
                        <h6>CPU:</h6>
                        <div class="badge badge-info"><h5>{{currentUsage('cpu',1)+'%'}}</h5></div>
                    </div>
                </div>
            </Card>
        </div>
        <!-- /.col-md-2 -->
        <div class="col-3">
            <Card :header="`&nbsp;Memory usage in last ${dataWindow} seconds`" icon="fas fa-memory">
                <div class="row align-items-center h-100">
                    <Sparkline type="composite" width="100%" height="80%" classes="col-10"
                               :values="usageDataAndLines('ram')"
                               :options="optionsForDataAndLines('ram')"></Sparkline>
                    <div class="col-2">
                        <h6>RAM:</h6>
                        <div class="badge badge-warning"><h5>{{currentUsage('ram',0)+'%'}}</h5></div>
                    </div>
                </div>
            </Card>
        </div>
        <!-- /.col-md-2 -->
        <div class="col-3">
            <Card :header="`Disk usage in last ${dataWindow} seconds`" icon="fas fa-compact-disc">
                <div class="row align-items-center h-100">
                    <Sparkline type="composite" width="100%" height="80%" classes="col-10"
                               :values="usageDataAndLines('disk')"
                               :options="optionsForDataAndLines('disk')"></Sparkline>
                    <div class="col-2">
                        <h6>Disk:</h6>
                        <div class="badge badge-primary"><h5>{{currentUsage('disk',0)+'%'}}</h5></div>
                    </div>
                </div>
            </Card>
        </div>
        <!-- /.col-md-2 -->
        <div class="col-3">
            <Card :header="`&nbsp;Network usage in last ${dataWindow} seconds`" icon="fas fa-network-wired">
                <div class="row align-items-center h-100">
                    <Sparkline type="composite" width="100%" height="80%" classes="col-9"
                               :values="Object.assign({ rx: usageData('rx','KB'), tx: usageData('tx','KB') }, this.defaultChartGridValues)"
                               :options="optionsForDataAndLines({
                                    'rx': { type: 'line', chartRangeMin: 0, chartRangeMax: 100, lineColor: 'red', fillColor: 'transparent',  },
                                    'tx': { type: 'line', chartRangeMin: 0, chartRangeMax: 100, lineColor: 'green', fillColor: 'transparent' }
                                })"></Sparkline>
                    <div class="col-3">
                        <h6>RX:</h6>
                        <div class="badge badge-danger"><h5>{{currentUsage('rx',1,'KB')}} <small>Kb/s</small></h5></div>
                        <h6>TX:</h6>
                        <div class="badge badge-success"><h5>{{currentUsage('tx',1,'KB')}} <small>Kb/s</small></h5></div>
                    </div>
                </div>
            </Card>
        </div>
        <!-- /.col-md-2 -->
    </div>
</template>

<script>
import InfoBox from '@/components/infobox/infobox.vue';
import Card from '@/components/card/card.vue';
import Sparkline from '@/components/sparkline/sparkline.vue';

import utils from '@/utils.js';

export default {
    name: 'Admin Dashboard - Overview',
    props: {
        modelValue: Object,
        timeseries: Object
    },
    components: { InfoBox, Card, Sparkline },
    methods: {
        getEmsClients() {
            let num = utils.getValue(this.modelValue, 'baguette-server.active-clients-list').length;
            return utils.toNum(num);
        },
        getEdgeNodes() {
            return utils.toNum(
                    Object.entries(utils.getValue(this.modelValue, 'baguette-server.passive-clients-map'))
                        .filter(([key,value]) => key && value && (value['node-state']??'')==='NOT_INSTALLED')
                        .length);
        },
        getIgnoredNodes() {
            // Object.fromEntries(Object.entries(obj))==obj
            return utils.toNum(
                    Object.entries(utils.getValue(this.modelValue, 'baguette-server.passive-clients-map'))
                        .filter(([key,value]) => key && value && (value['node-state']??'')==='IGNORE_NODE')
                        .length);
        },
        getTotalEvents() {
            let num = utils.getValue(this.modelValue, 'broker-cep.count-total-events');
            return utils.toNum(num);
        },
        getFreeMem() {
            let bytes = utils.getValue(this.modelValue, 'system-info.jmx-resource-metrics.jvm-memory-free');
            return utils.toGB(bytes);
        },
        getUptime() {
            let uptime = utils.getValue(this.modelValue, 'system-info.jmx-resource-metrics.jvm-uptime');
            let days = Math.floor(uptime/86400);
            let time = uptime - 86400 * days;
            days = (days>0) ? days+'d ' : '';
            return days + utils.toIsoFormat(time, 's', 'time');
        },
        getState() {
            let state = utils.getValue(this.modelValue, 'control.current-ems-state');
            let stateMessage = utils.getValue(this.modelValue, 'control.current-ems-state-message');
            return (state??''+' '+stateMessage??'').trim();
        },

        currentUsage(metric, precision, prefix) {
            if (!this.timeseries) return '--';
            let v = this.timeseries.getLast();
            if (!v || !(metric in v)) return '--';
            v = v[metric];
            if (prefix==='KB') v = parseFloat(utils.toKB(v));
            if (v>=10) precision = 0;
            //return v.toFixed(precision);
            return utils.toNum(v, precision);
        },
        usageData(metric, prefix) {
            if (!this.timeseries) return [ 0 ];
            var values = this.timeseries.getWindowData(this.dataWindow).map(data => (data && (metric in data)) ? data[metric] : 0);
            if (prefix==='KB') values = values.map(x => parseFloat(utils.toKB(x)));
            return values;
        },
        usageDataAndLines(metric, gridValues) {
            var result = {};
            if (Array.isArray(metric)) {
                for (let m in metric)
                    result[m] = this.usageData(m);
            } else
                result[metric] = this.usageData(metric);
            result = Object.assign(result, gridValues ?? this.defaultChartGridValues);
            return result;
        },
        optionsForDataAndLines(metricOptions, gridOptions) {
            var result = {};
            if (Array.isArray(metricOptions)) {
                for (let metric of metricOptions) {
                    result[metric] = this.defaultChartLineOptions;
                }
            } else
            if (typeof metricOptions==='object') {
                for (let [metric, options] of Object.entries(metricOptions)) {
                    result[metric] = options;
                }
            } else {
                result[metricOptions] = this.defaultChartLineOptions;
            }
            result = Object.assign(result, gridOptions ?? this.defaultChartGridOptions);
            return result;
        },
    },
    data() {
        var defaultChartGridLineOptions = {
            type: 'line',
            lineWidth: 1,
            chartRangeMin: 0,
            chartRangeMax: 100,
            lineColor: 'lightgrey',
            fillColor: 'transparent'
        };
        return {
            dataWindow: 60,
            defaultChartLineOptions: {
                type: 'line',
                chartRangeMin: 0,
                chartRangeMax: 100,
                normalRangeMin: 0,
                normalRangeMax: 75,
                normalRangeColor: '#DBF9DB'
            },
            defaultChartGridValues: {
                l0: [0, 0],
                l25: [25, 25],
                l50: [50, 50],
                l75: [75, 75],
                l100: [100, 100]
            },
            defaultChartGridOptions: {
                l0: defaultChartGridLineOptions,
                l25: defaultChartGridLineOptions,
                l50: defaultChartGridLineOptions,
                l75: defaultChartGridLineOptions,
                l100: defaultChartGridLineOptions
            }
        };
    },
}
</script>

<style scoped></style>
