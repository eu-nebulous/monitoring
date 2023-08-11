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
        <div class="col-4">
            <div class="container">
                <div class="row">
                    <div class="col-12">
                        <div class="form-group row">
                            <label :for="'formType_'+uid"
                                   class="col-sm-3 col-form-label col-form-label-sm"
                            >Request type</label>
                            <select :id="'formType_'+uid"
                                    class="col-sm-9 form-control form-control-sm"
                                    :aria-describedby="'restEndpointHelp_'+uid"
                                    v-on:change="changeForm"
                            >
                                <optgroup v-for="optGroup in options" :label="optGroup.text" :key="optGroup.id" :disabled="optGroup.disabled">
                                    <option v-for="opt in optGroup.options" v-bind:value="opt.id" :key="opt.id"
                                            :disabled="opt.disabled || opt.text==='-'"
                                            :style="opt.text==='-' ? 'font-size: 1px; background-color: #F1F1F1;' : ''"
                                    >{{opt.text}}</option>
                                </optgroup>
                            </select>
                            <!--<small :id="'restEndpointHelp_'+uid" class="form-text text-muted">Select an EMS Rest API endpoint to call.</small>-->
                        </div>
                    </div>
                </div>
                <div class="row">
                    <div class="col-12">
                        <div class="form-group row">
                            <label :for="'restEndpoint_'+uid"
                                   class="col-sm-3 col-form-label col-form-label-sm"
                            >REST Endpoint</label>
                            <input :id="'restEndpoint_'+uid"
                                   v-model="formData.endpoint"
                                   class="col-sm-9 form-control form-control-sm"
                                   :aria-describedby="'restEndpointHelp_'+uid"
                                   readonly="readonly"
                            />
                        </div>
                    </div>
                </div>
                <!-- Variable form fields -->
                <div class="row" v-for="f of form[formSelected].fields" :key="f.name">
                    <div class="col-12">
                        <div class="form-group row">
                            <label :for="get_input_id(f)"
                                   class="col-sm-3 col-form-label col-form-label-sm"
                            >{{f.text}}</label>
                            <input :id="get_input_id(f)"
                                   :type="f.type"
                                   :value="get_form_data(f)"
                                   class="col-sm-8 form-control form-control-sm"
                                   :aria-describedby="f.name+'_'+uid"
                                   v-on:change="updateFieldAndData(f)"
                                   v-on:input="updateFieldAndData(f)"
                            />
                            <small class="col-sm-1"><a href="javascript:void(0)" v-on:click="resetFieldValue(f)" class="btn btn-xs btn-link" title="Reset field value to default"><i class="fas fa-sync" /></a></small>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- /.col-4 -->
        <div class="col-8">
            <div class="container">
                <!-- Request payload -->
                <div class="row">
                    <div class="col-12">
                        <div class="form-group row">
                            <label :for="'restRequestPayload_'+uid"
                                   class="col-form-label col-form-label-sm"
                            >Request Payload (JSON)
                                &nbsp;&nbsp;
                                <small><a href="javascript:void(0)" v-on:click="updatePayload" class="btn btn-xs btn-link" title="Refresh payload (will discard manual changes)"><i class="fas fa-sync" /></a></small>
                            </label>
                            <TextareaDnd :id="'restRequestPayload_'+uid"
                                         class="form-control form-control-sm"
                                         :aria-describedby="'restRequestHelp_'+uid"
                                         placeholder="Request body in JSON"
                                         rows="8"
                                         v-on:change="updateDataFromPayload()"
                                         v-on:input="updateDataFromPayload()"
                            />
                            <!--<small :id="'restEndpointHelp_'+uid" class="form-text text-muted">Provide the request body in JSON format.</small>-->
                        </div>
                    </div>
                </div>
                <div class="row">
                    <div class="col-12">
                        <button class="btn btn-xs btn-success float-right"
                                v-on:click="restCall"
                        >
                            <i class="fa fa-paper-plane" />&nbsp;Send
                        </button>
                        <span v-if="showRestCallResultClear"
                              :id="'restCallResultClear_'+uid"
                              class="btn btn-xs btn-danger float-right"
                              v-on:click="()=>{showRestCallResult = false; showRestCallResultClear = false;}"
                        >
                            <i class="fa fa-trash-alt" />&nbsp;Clear
                        </span>
                        <div v-if="showRestCallResult"
                              :id="'restCallResult_'+uid"
                              class="text-muted">
                            <b>Result:</b> <span :class="['badge', 'badge-pill', getStatusBadge(restCallResult)]">{{restCallResult}}</span><br/>
                            <b>Status:</b> <span :style="getStatusColor(restCallStatus)">{{restCallStatus}}</span><br/>
                            <b>Response:</b> &nbsp;&nbsp;&nbsp;<small>{{restCallMime}}</small><br/>
                            <div v-if="! isJson(restCallMime)">{{restCallResponse}}</div>
                            <div v-else style="border: 1px solid grey; overflow: auto; max-height: 500px; resize: vertical;">
                                <!--<pre>{{ JSON.stringify(JSON.parse(restCallResponse), null, 4) }}</pre>-->
                                <vue-json-pretty :data="JSON.parse(restCallResponse)" :showLength="true" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- /.col-8 -->
    </div>
</template>

<script>
const $ = require('jquery');
import TextareaDnd from './textarea-dnd.vue';
import VueJsonPretty from 'vue-json-pretty';

import { FORM_TYPE_OPTIONS, FORM_SPECS } from './rest-call-forms.js';


export default {
    name: 'Call EMS REST API widget',
    components: { TextareaDnd, VueJsonPretty },
    props: {
        rootId: String,
        sseRef: String
    },
    data() {
        return {
            uid: Math.round(Math.random()*10000000) + new Date().getTime(),
            showRestCallResult: false,
            showRestCallResultClear: false,
            restCallResult: '',
            restCallStatus: '',
            restCallMime: '',
            restCallResponse: '',

            options: FORM_TYPE_OPTIONS,
            formSelected: '',
            formData: {},
            form: FORM_SPECS,
        };
    },
    mounted: function() {
        this.changeForm({ target: { value: 'new-app-model' }});
        this.$root[this.rootId] = this;

        this.waitForEmsDataAndUpdateOptions();
    },
    computed: {
        username() {
            if (this.sseRef && this.sseRef.trim()!=='' && this.$root.$refs[this.sseRef]) {
                let emsSse = this.$root.$refs[this.sseRef];
                if (emsSse.modelValue && emsSse.modelValue.data && emsSse.modelValue.data.ems) {
                    let emsData = emsSse.modelValue.data.ems;
                    if (emsData['.authentication-username'])
                        return emsData['.authentication-username'];
                }
            }
            return null;
        }
    },
    methods: {
        waitForEmsDataAndUpdateOptions() {
            if (this.sseRef && this.sseRef.trim()!=='')
            if (this.$root.$refs[this.sseRef])
            if (this.$root.$refs[this.sseRef].modelValue)
            if (this.$root.$refs[this.sseRef].modelValue.data)
            if (this.$root.$refs[this.sseRef].modelValue.data.ems)
            if (this.$root.$refs[this.sseRef].modelValue.data.ems['.version']) {
                try {
                    let v = parseInt(this.$root.$refs[this.sseRef].modelValue.data.ems['.version']);
                    if (v>0) {
                        this.updateRestCallOptions();
                        return;
                    }
                } catch (e) {
                    console.error('rest-call: waitForEmsDataAndUpdateOptions: EXCEPTION: ', e);
                }
            }
            setTimeout(this.waitForEmsDataAndUpdateOptions, 1000);
        },
        updateRestCallOptions() {
            // If additional REST Call commands and forms are provided,
            // merge them with the default...
            if (this.sseRef && this.sseRef.trim()!=='' && this.$root.$refs[this.sseRef]) {
                let emsSse = this.$root.$refs[this.sseRef];
                if (emsSse.modelValue && emsSse.modelValue.data && emsSse.modelValue.data.ems) {
                    let emsData = emsSse.modelValue.data.ems;
                    let newForms = { ...emsData['.rest-call-forms'], ...FORM_SPECS };
                    let newCmds = [ ...emsData['.rest-call-commands'], ...FORM_TYPE_OPTIONS ];

                    // Sort command/option groups using priority
                    newCmds.sort((a,b) => parseInt(a.priority??'0') - parseInt(b.priority??'0'));
                    // Sort commands/options per groups using priority
                    newCmds.forEach(grp => grp.options.sort((a,b) => parseInt(a.priority??'0') - parseInt(b.priority??'0')) );

                    // Convert 'disabled' properties to boolean
                    newCmds.forEach(grp => grp.disabled = /^true$/i.test(grp.disabled));
                    newCmds.flatMap(grp => grp.options).forEach(opt => opt.disabled = /^true$/i.test(opt.disabled));

                    if ((newForms && newCmds) && (newForms!=null && newCmds!=null)) {
                        this.form = newForms;
                        this.options = newCmds;
                        return newCmds;
                    }
                }
            }
            // Default options and forms
            this.form = FORM_SPECS;
            this.options = FORM_TYPE_OPTIONS;
            return FORM_TYPE_OPTIONS;
        },

        switchToForm(form, data) {
            if (!confirm('Update REST call pane?')) return;

            if (data) {
                let m = new Map(Object.entries(data));
                let _this = this;
                m.forEach((value, key) => {
                    let _id = _this.get_input_id({ name: key });
                    _this.formData[_id] = value;
                });
            }

            this.changeForm({ target: { value: form }})
        },
        changeForm(e) {
            let selId = e.target.value;
            let opt = this.options.flatMap(grp=>grp.options).find((opt) => opt.id===selId);
            if (opt) {
                this.formSelected = opt.form;
                this.formData.endpoint = opt.url;
            }
            this.$nextTick(() => {
                $('#formType_'+this.uid).val(selId);
                $('#restRequestPayload_'+this.uid).val('');
                this.updatePayload();
            });
        },
        get_input_id(f) {
            let _id = f.name.replace(/\./gi,'_')+'_'+this.uid;
            return _id;
        },
        get_form_data(f) {
            let _id = this.get_input_id(f);
            let _val = this.formData[_id] ?? this.get_form_default(f);
            if (!_val) _val = '';
            this.formData[_id] = _val;
            return _val;
        },
        get_form_default(f) {
            return (typeof f.defaultValue==='function' ? f.defaultValue(this) : f.defaultValue);
        },
        create_UUID() {
            var dt = new Date().getTime();
            var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                var r = (dt + Math.random()*16)%16 | 0;
                dt = Math.floor(dt/16);
                return (c=='x' ? r :(r&0x3|0x8)).toString(16);
            });
            return uuid;
        },
        updateFieldAndData(f) {
            // Update form data
            if (f && f!=null) {
                let _id = this.get_input_id(f);
                this.formData[_id] = $('#'+_id).val();
            }

            // Update payload
            this.updatePayload();
        },
        resetFieldValue(f) {
            let _val = this.get_form_default(f);
            let _id = this.get_input_id(f);
            $('#'+_id).val(_val);
            this.updateFieldAndData(f);
        },
        updatePayload() {
            // Update endpoint (if it contains placeholders)
            this.updateEndpoint();

            // Update request payload
            let taPayload = $('#restRequestPayload_'+this.uid);
            let payload = this.getPayload(true);
            if (payload==null) {
                taPayload.val('');
            } else {
                taPayload.val(payload);
            }
        },
        getPayload(makePasswords) {
            // Get request payload
            let taPayload = $('#restRequestPayload_'+this.uid);
            let type = $('#formType_'+this.uid).val();
            let opt = this.options.flatMap(grp=>grp.options).find((opt) => opt.id===type);
            if (! this.needsRequestBody(opt.method)) {
                return null;
            }

            let fields = this.form[opt.form].fields;
            let s = taPayload.val();
            let obj = s.trim()==='' ? {} : JSON.parse(s);
            let _this = this;

            $.each(fields, function(i, f) {
                let v = _this.get_form_data(f);
                let parts = f.name.split('.');
                let _o = obj;
                while (parts.length>1) {
                    let _p = parts.shift();
                    if (!_o[_p]) _o[_p] = {};
                    _o = _o[_p];
                }
                _o[parts[0]] = v;
                if (makePasswords && f.type && f.type==='password') _o[parts[0]] = '********';
            });
            s = JSON.stringify(obj, null, 4);
            return s;
        },
        updateEndpoint() {
            let type = $('#formType_'+this.uid).val();
            let source = this.options.flatMap(grp=>grp.options).find((opt) => opt.id===type).url;
            let suffix = '_'+this.uid;
            $.each(this.formData, function (k, v) {
                let kk = k.endsWith(suffix) ? k.replace(suffix,'') : k;
                source = source.replace(new RegExp(`{${kk}}`, "g"), v);
            })
            this.formData.endpoint = source;
            return source;
        },
        updateDataFromPayload() {
            let taPayload = $('#restRequestPayload_'+this.uid);
            let type = $('#formType_'+this.uid).val();
            let opt = this.options.flatMap(grp=>grp.options).find((opt) => opt.id===type);
            let fields = this.form[opt.form].fields;
            let s = taPayload.val();
            let obj = s.trim()==='' ? {} : JSON.parse(s);
            let _this = this;

            // Update form data
            $.each(fields, function(i, f) {
                let parts = f.name.split('.');
                let _o = obj;
                while (parts.length>1) {
                    let _p = parts.shift();
                    if (_o[_p]) {
                        _o = _o[_p];
                    } else {
                        _o = null;
                        break;
                    }
                }
                if (_o && _o!=null) {
                    let v = _o[parts[0]];
                    let _id = _this.get_input_id(f);
                    _this.formData[_id] = v;
                    $('#'+_id).val(v);
                }
            });

            // Update endpoint
            this.updateEndpoint();
        },
        needsRequestBody(method) {
            return method.toUpperCase()==='POST' || method.toUpperCase()==='PUT';
        },
        restCall() {
            let _form = $('#formType_'+this.uid).val();
            if (!_form || _form==='') return;
            let _opt = this.options.flatMap(grp=>grp.options).find(opt => opt.id===_form);
            //console.log('##### ', _opt);

            this.showRestCallResult = false;
            this.showRestCallResultClear = false;

            let method = _opt.method;
            let url = $('#restEndpoint_'+this.uid).val();
            //console.log(method+'  '+url);
            let body = this.getPayload(false);
            if (! this.needsRequestBody(method))
                body = null;

            let _this = this;
            _this.showRestCallResult = true;
            this.$nextTick(() => {
                $('#restCallResult').html('<span style="color: grey;"><i class="fas fa-spinner fa-spin"></i> Contacting EMS server...</span>');
                $.ajax({
                    url: url.replace(/\/+$/, ''),
                    type: method,
                    contentType: 'application/json',
                    data: body,
                    complete: function(xhr,status) {
                        //console.log('Call REST API: ', url, ' => ', status, xhr.getResponseHeader('content-type'), xhr);
                        //$('#restCallResult_'+_this.uid).html(`<b>Result:</b> ${status} (${xhr.readyState})<br/><b>Status:</b> ${xhr.status} ${xhr.statusText}<br/><b>Response:</b> ${xhr.responseText}`);
                        _this.restCallResult = `${status} (${xhr.readyState})`;
                        _this.restCallStatus = `${xhr.status} ${xhr.statusText}`;
                        _this.restCallMime = xhr.getResponseHeader('content-type');
                        _this.restCallResponse = xhr.responseText;
                        _this.showRestCallResultClear = true;
                    }
                });
            });
        },
        isJson(mime) {
            if (!mime || mime.trim()==='') return false;
            let m = mime.split(';')[0]
            return m==='application/json' || m==='text/json';
        },
        getStatusBadge(status) {
            status = (''+status).split(' ')[0];
            if (status==='success') return 'badge-success';
            if (status==='notmodified' || status==='nocontent') return 'badge-warning';
            return 'badge-danger';
        },
        getStatusColor(status) {
            status = (''+status).split(' ')[0];
            if (status[0]==='2') return '';
            if (status[0]==='4') return 'color: red';
            if (status[0]==='5') return 'color: darkred';
            return 'color: orange';
        },
    }
}
</script>