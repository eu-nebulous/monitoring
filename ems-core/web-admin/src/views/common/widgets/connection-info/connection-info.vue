<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
      <!-- Connection Info Indicator -->
      <li id="connection-info-indicator-wrapper" class="nav-item dropdown">
        <a class="nav-link" data-toggle="dropdown" href="#" style="padding-right: 5px;">
          <div class="container" :style="['color: #CCCCCC; text-align: center; line-height: .9em; font-size: 70%; padding: 0 5px 5px 5px; border-bottom: 3px solid '+(status_color)+' !important; border-radius: 8px;']">
            <img :src="status_icon" :class="status_icon_class" style="width: 18px !important; height: 18px !important;"/>
            <span v-if="getValue('retries',0)>0" class="badge badge-danger navbar-badge" style="top: unset; bottom: 9px;">{{getValue('retries',0)}}</span>
            <span id="connection-info-indicator-details" class="col text-nowrap" style="display: none;">
              &nbsp;<b>Updated on:</b><br/>{{localDate()}}<br/>{{localTime()}}
            </span>
          </div>
        </a>
        <div class="dropdown-menu dropdown-menu-lg dropdown-menu-right">
          <span class="dropdown-header" style="padding: 0px;">
            <div class="container" :style="['background: '+status_color2+'; padding: 3px 0 3px 0;']">
              <span class="col-12" style="color: black;"><i class="fas fa-plug"></i> Connection {{status_text}}</span>
            </div>
            <div class="container">
              <a v-if="getValue('started',false)" href="#" class="col-6 btn-sm btn-light" style="background-color: transparent;" v-on:click="stopConn();"><i class="fas fa-pause"></i> Pause</a>
              <a v-else                    href="#" class="col-6 btn-sm btn-light" style="background-color: transparent;" v-on:click="startConn();"><i class="fas fa-play"></i> Resume</a>
              <a                           href="#" class="col-6 btn-sm btn-light" style="background-color: transparent;" v-on:click="refreshNow();"><i class="fas fa-sync-alt"></i> Refresh</a>
            </div>
          </span>
          <div class="dropdown-divider"></div>
          <div class="dropdown-item text-sm" style="padding: .5rem;">
            <i class="fas fa-plus-square"></i> &nbsp;Updates count:
            <span class="float-right text-muted">{{getValue('updateCount','-')}}</span>
            <br/>
            <i class="far fa-hourglass"></i> &nbsp;Updates interval:
            <span class="float-right text-muted">{{getValue('interval','-')}}s / {{roundValue(getValue('avgActualInterval','-')/1000,1,'-')}}s</span>
            <br/>
            <i class="far fa-clock"></i> Last updated:
            <span class="float-right text-muted">{{localDateTime()}}</span>
            <br/>
            <div class="dropdown-divider" style="margin: 5px 0 5px 0;"></div>
            <i class="fas fa-code-branch"></i> &nbsp;&nbsp;Server address:
            <span class="float-right text-muted">{{getValue('serverAddress','-')}}</span>
            <br/>
            <span :style="['color: '+status_color+' !important;']"><i class="fas fa-battery-half"></i> Status:</span>
            <span class="float-right text-muted" :style="['color: '+status_color+' !important;']">{{status_text}}</span>
            <br/>
            <span :style="['color: '+(getValue('errors',0)>0 ? 'red !important' : '')+';']"><i class="fas fa-exclamation-triangle"></i> Errors:</span>
            <span class="float-right text-muted" :style="['color: '+(getValue('errors',0)>0 ? 'red !important' : '')+';']">{{getValue('errors',0)}}</span>
            <br/>
            <span :style="['color: '+(getValue('retries',0)>0 ? 'red !important' : '')+';']"><i class="fas fa-redo"></i> &nbsp;Retries:</span>
            <span class="float-right text-muted" :style="['color: '+(getValue('retries',0)>0 ? 'red !important' : '')+';']">{{getValue('retries',0)}}</span>
            <br/>
          </div>
          <div class="dropdown-divider"></div>
          <div href="#" class="dropdown-item dropdown-footer container" style="padding: 0px; background-color: #f8f9fa;">
            <div class="col btn-sm">
              <span style="font-size:.8rem;"><i class="fas fa-database text-muted"></i> Cached Data</span><br/>
              <a href="#" class="col btn-sm btn-light" v-on:click="showCachedData()" data-toggle="tooltip" data-placement="top" title="View"><i class="fas fa-eye"></i></a>
              <a href="#" class="col btn-sm btn-light" v-on:click="downloadCachedData()" data-toggle="tooltip" data-placement="top" title="Download"><i class="fas fa-download"></i></a>
              <a href="#" class="col btn-sm btn-light" v-on:click="copyCachedData()" data-toggle="tooltip" data-placement="top" title="Copy to clipboard"><i class="fas fa-copy"></i></a>
            </div>
            <div class="col btn-sm">
              <a href="#" class="col btn btn-light" v-on:click="gotoConfig()"><i class="fas fa-cog"></i> Configuration</a>
            </div>
          </div>
        </div>
      </li>

      <div id="connection-info-widget-settings-form" style="display: none;">
        <form action="javascript:return false;">
          <h6 class="control-sidebar-heading">Connection Info widget</h6>
          <hr style="background-color:white; margin-top:0;" />
          <div class="form-group">
            <!-- SSE interval form input -->
            <div class="input-group input-group-sm mb-3">
              <div class="input-group-prepend">
                <span class="input-group-text" id="connection-info-widget-settings-interval">Interval:</span>
              </div>
              <input @change="updateSseSetting" method="setCurrentInterval" type="number" required="required" min="1" step="1" size="5" class="form-control form-control-sm" aria-label="Interval" aria-describedby="connection-info-widget-settings-interval" />
            </div>
            <!-- SSE clients form input -->
            <div class="input-group input-group-sm mb-3">
              <div class="input-group-prepend">
                <span class="input-group-text" id="connection-info-widget-settings-clients">Clients:</span>
              </div>
              <input @change="updateSseSetting" method="setCurrentClients"  type="text" required="required" pattern="[^ \t\r\n]+" size="5" class="form-control form-control-sm" aria-label="Clients" aria-describedby="connection-info-widget-settings-clients" />
            </div>
            <!-- SSE clear stats button -->
            <div class="input-group input-group-sm" style="width:100%; display:flex; justify-content:right; margin: 0;">
              <button class="btn btn-outline-warning btn-xs float-right" @click="clearSseStats">Clear Statistics</button>
            </div>
          </div>
          <!-- /.form-group -->
        </form>
      </div>

      <teleport to="body" :disabled="!showModal" v-if="showModal">
        <Modal id="connection-info-widget-modal" width="80%" @close-modal-request="showModal=false">
          <template v-slot:header>
            <div style="width:100%; display: flex; justify-content: center;">
              <b>Current Data</b>
              <a href="#" @click="showModal=!showModal" style="position:absolute; right:0;">
                <span style="color: grey; font-weight: normal;"><i class="fas fa-times"></i></span>
              </a>
            </div>
          </template>
          <div id="connection-info-widget-modal-content">
            <vue-json-pretty :path="'res'" :data="data" :showLength="true"></vue-json-pretty>
          </div>
          <template v-slot:footer>
            <div style="width:50%; text-align:right; padding: 0 20px;">
              <button @click="downloadCachedData" class="btn btn-outline-dark btn-sm"><i class="fas fa-download"></i> Download as file</button>
              &nbsp;&nbsp;&nbsp;
              <button @click="copyCachedData" class="btn btn-outline-dark btn-sm"><i class="fas fa-copy"></i> Copy to clipboard</button>
            </div>
            <div style="width:50%; text-align:right; padding: 0 20px;">
              <button @click="showModal=!showModal" class="btn btn-dark btn-sm">Close</button>
            </div>
          </template>
        </Modal>
      </teleport>
</template>

<script>
var $ = require('jquery');
//var Vue = require('vue');

import iconPlugGreen  from "./img/plug-green.png";
import iconPlugBlue   from "./img/plug-blue.png";
import iconPlugOrange from "./img/plug-orange.png";
import iconPlugRed    from "./img/plug-red.png";
import iconWaveBlue   from "./img/wave-blue.gif";
import iconWaveRed    from "./img/wave-red.gif";

import Modal from '@/components/modal/modal.vue';
import VueJsonPretty from 'vue-json-pretty';
import 'vue-json-pretty/lib/styles.css';

export default {
  name: 'connection-info',
  props: {
    modelValue: Object,
    data: Object,
    sseRef: String,
    settingsTarget: String,
  },
  components: {
    Modal, VueJsonPretty
  },

  data() {
    return {
      connection_info: {
        updatedOn: 0,
        updateCount: '-',
        serverAddress: '-',
        open: false,
        errors: 0,
        retries: 0
      },
      settings: {
        interval: 0,
        clients: null
      },
      showModal: false,
    };
  },

  computed: {
    status_text: function() {
      return this.statusSwitch('Open','Close','Open+Error','Error','Starting','Retrying');
    },
    status_icon: function() {
      return this.statusSwitch(iconPlugGreen,iconPlugBlue,iconPlugOrange,iconPlugRed,iconWaveBlue,iconWaveRed);
    },
    status_icon_class: function() {
      return this.statusSwitch('','','plug-icon-blink','plug-icon-blink-faster','plug-icon-blink');
    },
    status_color: function() {
      return this.statusSwitch('#008000','#0000ff','#fca604','#ff0000','#0000ff','#ff0000');
    },
    status_color2: function() {
      return this.statusSwitch('lightgreen','lightblue','orange','pink');
    },
  },

  mounted() {
    // Added hover animation
    $('#connection-info-indicator-wrapper').hover(
      ()=>$('#connection-info-indicator-details').delay(500).show(1000),
      ()=>$('#connection-info-indicator-details').delay(500).hide(1000)
    );

    // Move widget settings to the specified target position (e.g. control-sidebar)
    if (this.settingsTarget && this.settingsTarget.trim()!=='') {
      let form = $("#connection-info-widget-settings-form");
      form.detach().appendTo(this.settingsTarget.trim());
      form.show();
      $(form).find('input[type="number"]').val( this.getSse('getCurrentInterval') );
      $(form).find('input[type="text"]').val( this.getSse('getCurrentClients') );
    }
  },

  methods: {
    getValue(field, defVal) {
      return (this.modelValue && this.modelValue[field]) ? this.modelValue[field] : defVal;
    },
    roundValue(val,prec,defVal) {
      const factor = Math.pow(10,prec);
      let result = Math.round(val*factor) / factor;
      return isNaN(result) ? defVal : result;
    },

    statusSwitch(pOpen,pClose,pWarn,pError,pStarting,pRetry) {
      if (!this.modelValue) return pClose;
      if (!pStarting) pStarting = pClose;
      if (!pRetry) pStarting = pError;

      if (this.modelValue.started) {
        let status = this.modelValue.status;
        if (!status) return (this.modelValue.starting ? pStarting : pClose);
        switch (status) {
          case 'ok': return pOpen;
          case 'warn': return pWarn;
          case 'error': return pError;
          case 'retry': return pRetry;
        }
      } else {
        let status = this.modelValue.status;
        if (!status) return (this.modelValue.starting ? pStarting : pClose);
        switch (status) {
          case 'ok': return pClose;
          case 'warn': return pWarn;
          case 'error': return pError;
          case 'retry': return pRetry;
        }
      }

      /*return this.modelValue.started
          ? (this.modelValue.errors==0 ? pOpen : pWarn)
          : (this.modelValue.errors==0 ? (this.modelValue.starting ? pStarting : pClose) : pError);*/
    },

    localDateTime() {
      const tm = this.getValue('updatedOn',0);
      if (tm==0) return '-';
      const n = new Date(tm);
      const d = new Date(n.getTime() - n.getTimezoneOffset()*60*1000);
      return d.toISOString().replace('T',' ').slice(0,19);
    },
    localDate() { const dt = this.localDateTime(); return dt=='-' ? '-' : dt.slice(0,10); },
    localTime() { const dt = this.localDateTime(); return dt=='-' ? '-' : dt.slice(11,19); },

    getSse(funcName, ...args) {
      if (this.sseRef && this.sseRef.trim()!=='') {
        let sse = this.$root.$refs[this.sseRef.trim()];
        if (sse) {
          if (!funcName || funcName.trim()==='')
            return sse;
          funcName = funcName.trim();
          if (sse[funcName] && typeof sse[funcName]==='function') {
            return sse[funcName](...args);
          } else {
            console.warn('Function not found in SSE or is not a function: '+funcName);
          }
        } else {
          console.warn('Not found SSE with ref: '+this.sseRef);
        }
      } else {
        console.warn('Missing SSE ref');
      }
    },
    startConn() {
      this.getSse('startSse');
    },
    stopConn() {
      this.getSse('stopSse');
    },
    toggleConn() {
      let sse = this.getSse();
      if (sse.isSseStarted() || sse.isSseStarting()) sse.stopSse();
      else sse.startSse();
    },
    refreshNow() {
      this.getSse('refresh');
    },

    showCachedData() {
      this.showModal = true;
      /*
      Vue.nextTick(function () {
        $('#connection-info-widget-modal-content').html('<pre>'+JSON.stringify(this.data, null, 4)+'</pre>');
      }.bind(this));*/
    },
    downloadCachedData() {
      this.downloadToFile(JSON.stringify(this.data, null, 4), 'cached-data.json', 'application/json');
    },
    copyCachedData() {
      this.copyToClipboard(JSON.stringify(this.data, null, 4));
    },

    /*
      Source: https://stackoverflow.com/questions/13405129/javascript-create-and-save-file
      Retrieved: 2021-09-07
    */
    downloadToFile(data, filename, type) {
        var file = new Blob([data], {type: type});
        if (window.navigator.msSaveOrOpenBlob) // IE10+
            window.navigator.msSaveOrOpenBlob(file, filename);
        else { // Others
            var a = document.createElement("a");
            var url = URL.createObjectURL(file);
            a.href = url;
            a.download = filename;
            //document.body.appendChild(a);
            a.click();
            setTimeout(function() {
                //document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            }, 0);
        }
    },

    /*
      Source: https://stackoverflow.com/questions/400212/how-do-i-copy-to-the-clipboard-in-javascript
      Retrieved: 2021-09-07
    */
    copyToClipboard(data) {
      navigator.clipboard.writeText(data).then(
        function() {
          //console.log('Copying to clipboard was successful!');
        },
        function(err) {
          console.error('Could not copy text to clipboard: ', err);
          alert('Could not copy text to clipboard: ' + err);
        }
      );
    },

    gotoConfig() {
      // open control side bar
      if ($(".control-sidebar").css('display')=='none') {
        $("a[data-widget='control-sidebar']")[0].click();
      }

      // go to connection-info settings tab
      if (this.settingsTarget && this.settingsTarget.trim()!=='') {
        let tabPane = $(this.settingsTarget).closest('[role="tabpanel"]');
        let tabId = tabPane.attr('aria-labelledby');
        $('#'+tabId)[0].click();

        // if needed, scroll down to connection-info settings section
        var aForm = $("#connection-info-widget-settings-form");
        this.scrollToSettings(aForm);
      }
    },

    scrollToSettings(aForm) {
      if (aForm.is(':visible'))
        $('html,body').animate({scrollTop: aForm.offset().top},'slow');
      else
        setTimeout(()=>this.scrollToSettings(aForm), 100);
    },

    updateSseSetting(e) {
      let method = $(e.target).attr('method');
      let newVal = $(e.target).val();
      this.getSse(method, newVal);
    },
    clearSseStats() {
      this.getSse('resetSseInfo');
    },
  },
}
</script>

<style>

/* -------------------- Blink Plug Icon -------------------- */

.plug-icon-blink-slower {
    -webkit-animation: blinkPlugIcon normal 3s infinite ease-in-out;
    -moz-animation: blinkPlugIcon normal 3s infinite ease-in-out;
    -ms-animation: blinkPlugIcon normal 3s infinite ease-in-out;
    -o-animation: blinkPlugIcon normal 3s infinite ease-in-out;
    animation: blinkPlugIcon normal 3s infinite ease-in-out;
}
.plug-icon-blink-slow {
    -webkit-animation: blinkPlugIcon normal 2s infinite ease-in-out;
    -moz-animation: blinkPlugIcon normal 2s infinite ease-in-out;
    -ms-animation: blinkPlugIcon normal 2s infinite ease-in-out;
    -o-animation: blinkPlugIcon normal 2s infinite ease-in-out;
    animation: blinkPlugIcon normal 2s infinite ease-in-out;
}
.plug-icon-blink {
    -webkit-animation: blinkPlugIcon normal 1s infinite ease-in-out;
    -moz-animation: blinkPlugIcon normal 1s infinite ease-in-out;
    -ms-animation: blinkPlugIcon normal 1s infinite ease-in-out;
    -o-animation: blinkPlugIcon normal 1s infinite ease-in-out;
    animation: blinkPlugIcon normal 1s infinite ease-in-out;
}
.plug-icon-blink-fast {
    -webkit-animation: blinkPlugIcon normal .5s infinite ease-in-out;
    -moz-animation: blinkPlugIcon normal .5s infinite ease-in-out;
    -ms-animation: blinkPlugIcon normal .5s infinite ease-in-out;
    -o-animation: blinkPlugIcon normal .5s infinite ease-in-out;
    animation: blinkPlugIcon normal .5s infinite ease-in-out;
}
.plug-icon-blink-faster {
    -webkit-animation: blinkPlugIcon normal .3s infinite ease-in-out;
    -moz-animation: blinkPlugIcon normal .3s infinite ease-in-out;
    -ms-animation: blinkPlugIcon normal .3s infinite ease-in-out;
    -o-animation: blinkPlugIcon normal .3s infinite ease-in-out;
    animation: blinkPlugIcon normal .3s infinite ease-in-out;
}

@-webkit-keyframes blinkPlugIcon {
    from { opacity: 1; }
    50% { opacity: 0; }
    to { opacity: 1; }
}
@-moz-keyframes blinkPlugIcon {
    from { opacity: 1; }
    50% { opacity: 0; }
    to { opacity: 1; }
}
@-ms-keyframes blinkPlugIcon {
    from { opacity: 1; }
    50% { opacity: 0; }
    to { opacity: 1; }
}
@-o-keyframes blinkPlugIcon {
    from { opacity: 1; }
    50% { opacity: 0; }
    to { opacity: 1; }
}
@keyframes blinkPlugIcon {
    from { opacity: 1; }
    50% { opacity: 0; }
    to { opacity: 1; }
}

</style>