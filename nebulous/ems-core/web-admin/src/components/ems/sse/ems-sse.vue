<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
  <div class="ems-sse">
    <div v-if="showDebugForm" class="row" style="background-color:lightyellow;">
      <div class="input-group col-1">
        <div class="input-group-prepend"><span class="input-group-text"><b>EMS-SSE:</b></span></div>
      </div>
      <div class="input-group col-1">
        <div class="form-check form-check-inline">
          <input type="radio" id="started" :value="true" v-model="isStarted" aria-label="started" aria-describedby="ems-sse-field0" /><label for="started">Started</label>
          &nbsp;&nbsp;&nbsp;
          <input type="radio" id="stopped" :value="false" v-model="isStarted" aria-label="stopped" aria-describedby="ems-sse-field0" /><label for="stopped">Stopped</label>
        </div>
      </div>
      <div class="input-group col-2">
        <div class="input-group-prepend"><span class="input-group-text" id="ems-sse-field1">Errors:</span></div>
        <input v-model="errors" type="text" class="form-control" placeholder="errors" aria-label="errors" aria-describedby="ems-sse-field1" />
      </div>
      <div class="input-group col-2">
        <div class="input-group-prepend"><span class="input-group-text" id="ems-sse-field2">Retries:</span></div>
        <input v-model="retries" type="text" class="form-control" placeholder="retries" aria-label="retries" aria-describedby="ems-sse-field2" />
      </div>
      <div class="input-group col-2">
        <button class="btn btn-primary btn-sm" @click="resetSseInfo" >Reset</button>
      </div>
    </div>
  </div>
</template>

<script>

const DEFAULT_EVENT_INTERVAL = 30;
const DEFAULT_EVENT_CLIENTS = '*';

export default {
  name: 'ems-sse',
  props: {
    interval: Number,
    clients: String,
    autoStart: { type: Boolean, default: true },
    modelValue: Object,
    showDebugForm: { type: Boolean, default: false },
    missingEventsWarnLimit: { type: Number },
    missingEventsErrorLimit: { type: Number },
    missingEventsWarnPeriod: { type: Number, default: 30*1000 },
    missingEventsErrorPeriod: { type: Number, default: 60*1000 },
    retryNum: { type: Number, default: 5 },
    retryDelay: { type: Number, default: 30*1000 },
    retryBackoffFactor: { type: Number, default: 1 },
  },

  emits: ['update:modelValue'],

  data() {
    return {
      liveMetricsEventSource: null,
      current_interval: DEFAULT_EVENT_INTERVAL,
      current_clients: DEFAULT_EVENT_CLIENTS,
      isStarting: false,
      isStarted: false,
      prevUpdate: null,
      lastUpdate: null,
      updateCount: 0,
      lastActualInterval: null,
      sumActualIntervals: null,
      avgActualInterval: null,
      updateCountStart: null,
      isRefreshing: false,
      errors: 0,
      retries: 0,
      sseStatus: null,
    };
  },

  mounted() {
    this.setCurrentInterval(this.interval);
    this.setCurrentClients(this.clients);
    if (!this.autoStart) return;
    this.startSse();
  },

  beforeUnmount() {
    this.stopSse();
  },

  methods: {
    getCurrentInterval() { return this.current_interval; },
    getCurrentClients() { return this.current_clients; },
    setCurrentInterval(i) {
      if (!i || isNaN(i)) return;
      if (i==this.current_interval || i<1) return;
      this.current_interval = parseInt(i);
      this.restartSse(true);
    },
    setCurrentClients(c) {
      if (!c || typeof c !== 'string') return;
      c = c.trim();
      if (c==this.current_clients || c==='') return;
      this.current_clients = c;
      this.restartSse(true);
    },

    isSseStarting() { return this.isStarting; },

    isSseStarted() { return this.isStarted; },

    restartSse(restartIfRunning) {
      if (restartIfRunning || restartIfRunning===undefined || this.isSseStarted()) {
        this.stopSse();
        this.startSse();
      }
    },

    startSse(i) {
      //console.log('EMS-SSE: startSse: Point A: ', this.isStarted, this.isStarting);
      if (this.isStarted || this.isStarting) return;
      console.log('EMS-SSE: startSse');

      this.isStarting = true;
      this.$emit('update:modelValue', { sseInfo: this.getSseInfo() });

      if (i) this.setCurrentInterval(i);
      //console.log('EMS-SSE: startSse: Point B: ', this.isStarted, this.isStarting);
      this.liveMetricsEventSource = new EventSource("/info/all-metrics/stream/" + this.current_clients + "?interval=" + this.current_interval);
      this.startMissingEventMonitor();

      this.liveMetricsEventSource.onopen = (e) => {
          console.log('EMS: SSE: live-metrics: open: ', e);
          this.isStarting = false;
          this.isStarted = true;
          let _tmp1 = this.updateCount;
          this.resetSseInfo();
          this.updateCount = this.updateCountStart = _tmp1;
          this.$emit('update:modelValue', { sseInfo: this.getSseInfo() });

          this.startMissingEventMonitor();
          this.stopRetry();
      }
      this.liveMetricsEventSource.onerror = (e) => {
          console.log('EMS: SSE: live-metrics: error: ', e);
          this.isStarting = false;
          this.isStarted = false;
          this.errors++;
          this.$emit('update:modelValue', { sseInfo: this.getSseInfo() });

          this.stopMissingEventMonitor();
          this.setNewRetry();
      }
      this.liveMetricsEventSource.onmessage = (e) => {
          console.log('EMS: SSE: live-metrics: data: ', e.data);
      }

      //this.liveMetricsEventSource.addEventListener("error", this.liveMetricsEventSource.onerror);

      this.liveMetricsEventSource.addEventListener("ems-metrics-event", (e) => {
          //console.log('EMS-SSE: ems-metrics-event: DATA: ', e.data);
          this.processData(e.data);
      });
    },

    stopSse() {
      this.stopMissingEventMonitor();
      if (!this.isStarted) return;
      console.log('EMS-SSE: stopSse');

      this.liveMetricsEventSource.close();
      this.liveMetricsEventSource = null;
      this.isStarted = false;
      this.$emit('update:modelValue', { data: {}, sseInfo: this.getSseInfo() });
    },

    startMissingEventMonitor() {
      if (this.missingEventMonitorInterval!=null) return;
      this.missingEventMonitorInterval = setInterval(this.runMissingEventMonitor, this.current_interval*1000);
      this.lastUpdateTimestamp = new Date().getTime();
      console.log('EMS-SSE: startMissingEventMonitor: timer started');
    },
    stopMissingEventMonitor() {
      if (this.missingEventMonitorInterval==null) return;
      clearInterval(this.missingEventMonitorInterval);
      this.missingEventMonitorInterval = null;
      console.log('EMS-SSE: stopMissingEventMonitor: timer stopped');
    },
    runMissingEventMonitor() {
      if (!this.current_interval || this.current_interval<1) {
        console.warn('EMS-SSE: runMissingEventMonitor: INVALID current_interval: ', this.current_interval);
        return;
      }

      let diff = new Date().getTime() - this.lastUpdateTimestamp;
      let howmany = diff / this.current_interval / 1000;
      //console.log('EMS-SSE: runMissingEventMonitor: RUNNING... ', howmany, diff);

      if (this.missingEventsErrorLimit && diff > this.missingEventsErrorLimit * this.current_interval * 1000
        || this.missingEventsErrorPeriod && diff > this.missingEventsErrorPeriod)
      {
        console.error('EMS-SSE: runMissingEventMonitor: MISSING EVENTS: ', howmany, diff);
        try {
          this.stopSse();
        } catch (e) {
          console.warn('EMS-SSE: runMissingEventMonitor: Exception while closing SSE connection: ', e);
        }
        this.errors++;
        this.setStatus('error');
        this.setNewRetry();
        this.setStatus('error');
      } else
      if (this.missingEventsWarnLimit && diff > this.missingEventsWarnLimit * this.current_interval * 1000
        || this.missingEventsWarnPeriod && diff > this.missingEventsWarnPeriod)
      {
        console.warn('EMS-SSE: runMissingEventMonitor: DELAYED EVENTS: ', howmany, diff);
        this.setStatus('warn');
      } else
      {
        this.setStatus('ok');
      }
    },

    setStatus(status) {
      if (!status || status.trim()==='') return;
      if (this.isRetrying && status!=='retry') status = 'error';
      status = status.trim().toLowerCase();
      if (this.sseStatus==status) return;
      this.sseStatus = status;
      this.$emit('update:modelValue', { sseInfo: this.getSseInfo() });
    },

    startRetry() {
      if (this.retryTimeout!=null) return;
      this.retries = 0;
      this.isRetrying = true;
      this.setStatus('error');
      this.retryTimeout = setTimeout(this.runRetry, this.retryDelay);
      console.log('EMS-SSE: startRetry: Will retry in '+this.retryDelay+'ms');
    },
    stopRetry() {
      if (this.retryTimeout!=null) {
        clearTimeout(this.retryTimeout);
        this.retryTimeout = null;
        console.log('EMS-SSE: stopRetry: Stopped retrying');
      } else
        console.log('EMS-SSE: stopRetry: Retrying not active');
      this.isRetrying = false;
    },
    runRetry() {
      if (this.retryNum>0 && this.retries+1 > this.retryNum) {
        console.error('EMS-SSE: runRetry: Give up retrying to connect to EMS server after '+this.retries+' attempts');
        return;
      }

      this.retries++;
      console.log('EMS-SSE: runRetry: RETRYING to connect to EMS server... Retry: '+this.retries+' of '+(this.retryNum<0?'∞':this.retryNum));
      this.restartSse();
      this.startMissingEventMonitor();
      this.setStatus('retry');
    },
    setNewRetry() {
      this.stopMissingEventMonitor();
      this.setStatus('error');
      if (this.retries==0) {
        this.startRetry();
        return;
      }

      if (this.retryNum>0 && this.retries+1 > this.retryNum) {
        console.error('EMS-SSE: runRetry: Give up retrying to connect to EMS server after '+this.retries+' attempts');
        return;
      }
      let delay = this.retryDelay;
      for (let i=1; i<=this.retries; i++) delay *= this.retryBackoffFactor;
      this.retryTimeout = setTimeout(this.runRetry, delay);
      console.log('EMS-SSE: runRetry: Will retry again in '+delay+'ms');
    },

    processData(dataStr, updateStats) {
      if (updateStats === undefined) updateStats = true;
      this.lastUpdateTimestamp = new Date().getTime();

      // Parse data string into JSON object
      var data = JSON.parse(dataStr);
      //console.log('EMS-SSE: processData: JSON: ', data);

      let sseInfo;
      if (updateStats) {
        this.prevUpdate = this.lastUpdate;
        this.lastUpdate = new Date().getTime();
        this.lastActualInterval = this.lastUpdate - this.prevUpdate;
        this.updateCount++;
        if (this.prevUpdate!=null && this.updateCount>this.updateCountStart+1) {
          this.sumActualIntervals += this.lastActualInterval;
          this.avgActualInterval = this.sumActualIntervals / (this.updateCount - this.updateCountStart - 1);
        }
        sseInfo = this.getSseInfo();
      } else {
        let prevUpdate = this.lastUpdate;
        let lastUpdate = new Date().getTime();
        let lastActualInterval = lastUpdate - prevUpdate;
        this.updateCount++;
        this.updateCountStart++;
        sseInfo = this.getSseInfo();
        sseInfo.updatedOn = lastUpdate;
        sseInfo.lastActualInterval = lastActualInterval;
      }
      this.errors = 0;

      // Send model and sse-info update event
      this.$emit('update:modelValue', { data: data, sseInfo: sseInfo });
      //console.log('EMS-SSE: processData: MODEL_UPDATE: ok');
    },

    refresh() {
      let xhr = new XMLHttpRequest();
      xhr.open("GET", "/info/all-metrics/get/" + this.current_clients, true);

      xhr.onload = (e) => {
        if (xhr.readyState === 4) {
          if (xhr.status === 200) {
            //console.log('EMS-SSE: refresh(): DATA: ', xhr.responseText);
            this.isRefreshing = false;
            this.processData(xhr.responseText, false);
            return;
          }
        }
        xhr.onerror(e);
      };

      xhr.onabort = xhr.onerror = xhr.ontimeout = (e) => {
        console.error('EMS-SSE: refresh(): ERROR: ', e);
        this.isRefreshing = false;
        this.errors++;
        this.$emit('update:modelValue', { sseInfo: this.getSseInfo() });
      };

      xhr.send();
      this.isRefreshing = true;
      this.$emit('update:modelValue', { sseInfo: this.getSseInfo() });
    },

    getSseInfo() {
      let _sseInfo = {};
      _sseInfo.interval = this.current_interval;
      _sseInfo.starting = this.isStarting;
      _sseInfo.started = this.isStarted;
      _sseInfo.updatedOn = this.lastUpdate;
      _sseInfo.updateCount = this.updateCount;
      _sseInfo.lastActualInterval = this.lastActualInterval,
      _sseInfo.avgActualInterval = this.avgActualInterval,
      _sseInfo.serverAddress = location.host;
      _sseInfo.refreshing = this.isRefreshing;
      _sseInfo.errors = this.errors;
      _sseInfo.retries = this.retries;
      _sseInfo.status = this.sseStatus;
      return _sseInfo;
    },

    resetSseInfo() {
      this.prevUpdate = null;
      this.lastUpdate = null;
      this.updateCount = 0;
      this.updateCountStart = null;
      this.lastActualInterval = null;
      this.sumActualIntervals = null;
      this.avgActualInterval = null;
      this.updateCountStart = null;
      this.errors = 0;
      this.retries = 0;
    },
  }
}
</script>
