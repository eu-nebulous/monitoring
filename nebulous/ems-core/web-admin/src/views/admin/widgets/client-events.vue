<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <table class="table table-striped table-sm">
        <thead>
        <tr>
            <th>
                <div class="row">
                    <div class="col-4">Client</div>
                    <div class="col-3">Topic/Queue</div>
                    <div class="col-1">Gen?</div>
                    <div class="col-2">Value</div>
                    <div class="col-2">Action</div>
                </div>
            </th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="n in numRows" :key="n">
            <td class="align-middle">
                <div class="row">
                    <div class="col-4">
                        <ClientsList :id="'evt-'+n+'-client'" :row="n" selected="*" :clients="clients" @change="updateDestinations" />
                    </div>
                    <div class="col-3">
                        <DestinationsList :id="'evt-'+n+'-destination'" :row="n" :destinations="clientDestinations[n]" />
                    </div>
                    <div class="col-3">
                        <div class="row">
                            <div class="form-check form-switch col-2">
                                <input :id="'evt-'+n+'-type'" :data-row="n" v-model="types[n]" type="checkbox" class="form-check-input" />
                            </div>
                            <div v-if="!types[n]" class="input-group input-group-sm col-10">
                                <input :id="'evt-'+n+'-value'" :data-row="n" type="number" value="0" size="5" class="form-control" placeholder="Write a value" />
                            </div>
                            <div v-else class="input-group input-group-sm col-10 text-right d-flex justify-content-end">
                                <div style="white-space: nowrap;">From: <input :id="'evt-'+n+'-from'" :data-row="n" type="number" value="0" size="5" class="form-input" placeholder="From value" /></div>
                                <div style="white-space: nowrap;">To: <input :id="'evt-'+n+'-to'" :data-row="n" type="number" value="100" size="5" class="form-input" placeholder="To value" /></div>
                                <div style="white-space: nowrap;">Period (sec): <input :id="'evt-'+n+'-interval'" :data-row="n" type="number" value="1" size="5" class="form-input" placeholder="Interval" /></div>
                            </div>
                        </div>
                    </div>
                    <div class="col-1">
                        <div v-if="!types[n]">
                            <button :id="'evt-'+n+'-btn-send'" :data-row="n" class="btn btn-success btn-xs align-middle" @click="sendEvent">
                                <small><i class="fas fa-paper-plane"/></small>&nbsp;&nbsp;Send
                            </button>
                        </div>
                        <div v-else class="row">
                            <button :id="'evt-'+n+'-btn-start'" :data-row="n" class="btn btn-success btn-xs align-middle" @click="startEventGenerator(n)">
                                <small><i class="fas fa-play"/></small>
                            </button>&nbsp;
                            <button :id="'evt-'+n+'-btn-stop'" :data-row="n" class="btn btn-secondary btn-xs align-middle disabled" @click="stopEventGenerator(n)">
                                <small><i class="fas fa-stop"/></small>
                            </button>
                        </div>
                    </div>
                    <div class="col-1 text-center">
                        <span v-if="commands[n] && commands[n].exec && commands[n].exec.running" @click="cancelSend(n)" >
                            <small style="color: darkred;"><i class="fas fa-times-circle"/></small>
                        </span>
                        <span v-if="commands[n] && commands[n].exec && commands[n].exec.duration && !commands[n].exec.generatorStarted" @click="commands[n].exec = undefined">
                            <small><i class="far fa-trash-alt"/></small>
                        </span>
                        <div v-if="commands[n] && commands[n].exec && commands[n].exec.running" class="spinner-border spinner-border-sm text-primary" role="status">
                            <span class="sr-only">Running...</span>
                        </div>
                    </div>
                </div>
                <div class="row" v-if="commands[n] && commands[n].exec">
                    <div class="col-5">
                        <div style="font-size: smaller;">
                            Destination: <span style="color: grey; white-space: nowrap;">{{commands[n].exec.destination}}</span>
                            <br/>
                            Client: <span style="color: grey;">{{commands[n].exec.client}}</span>
                            &nbsp;&nbsp;&nbsp;&nbsp;
                            <span v-if="!types[n]">Value: <span style="color: grey;">{{commands[n].exec.value}}</span></span>
                            <span v-else>Values: <span style="color: grey;">{{commands[n].exec.from}} &mdash; {{commands[n].exec.to}}</span></span>
                        </div>
                        <div style="font-size:smaller;" v-if="commands[n].exec.duration">
                            Duration: <span style="color: grey;">{{commands[n].exec.duration}}ms</span>
                        </div>
                    </div>
                    <div class="col-7">
                        <div :class="['text-'+commands[n].exec.status]">
                            <small>
                                <b>{{commands[n].exec.result}}</b>
                            </small>
                        </div>
                    </div>
                </div>
            </td>
        </tr>
        </tbody>
    </table>
</template>

<script>
const $ = require('jquery');
import ClientsList from './clients-list.vue';
import DestinationsList from './destinations-list.vue';

export default {
    name: 'Client Events widget',
    props: {
        numRows: Number,
        clients: Object,
        groupings: Array,
        destinations: Object,
    },
    components: {
        ClientsList, DestinationsList
    },
    computed: {
        allGrouping() { return this.groupings.length>0 ? this.groupings[this.groupings.length-1] : null },
        serverGrouping() { return this.groupings.length>0 ? this.groupings[0] : null },
    },
    data() {
        return {
            clientDestinations: { },
            commands: { },
            types: [ ],
        };
    },
    mounted() {
        this.types = new Array(this.numRows).fill(false);
    },
    watch: {
        destinations: {
            immediate: true,
            handler() {
                for (var i=1; i<=this.numRows; i++) {
                    let clientId = $('#evt-'+i+'-client').val();
                    this.updateDestinationsForClient(i, clientId);
                }
            }
        },
    },
    methods: {
        updateDestinations(e) {
            //console.log('CLIENT CHANGED:  ', e.target.id, e.target.value, e.target);
            let row = e.target.dataset.row;
            let clientId = e.target.value;
            this.updateDestinationsForClient(row, clientId);
        },
        updateDestinationsForClient(row, clientId) {
            let grouping = this.allGrouping;
            let recurse = true;
            if (clientId==='*') {
                grouping = this.allGrouping;
            } else
            if (clientId==='0') {
                grouping = this.serverGrouping;
                recurse = false;
            } else
            if (clientId) {
                let clientObj = this.clients.find((el) => { return el.id==clientId; });
                if (clientObj && clientObj.grouping)
                    grouping = clientObj.grouping;
            }

            let ii = this.groupings.findIndex(el=>el===grouping);
            if (recurse && ii>=0) {
                let _all_grouping_destinations = [];
                for (; ii<this.groupings.length; ii++) {
                    let _grp = this.groupings[ii];
                    let _tmp = this.prepareDestinationsList(_grp);
                    _all_grouping_destinations = _all_grouping_destinations.concat(_tmp);
                }
                this.clientDestinations[row] = _all_grouping_destinations;
            } else {
                this.clientDestinations[row] = this.prepareDestinationsList(grouping);
            }
        },
        prepareDestinationsList(_grp) {
            if (!_grp) return [];
            let _dest = this.destinations[_grp];
            if (!_dest) return [];
            let _result = [];
            let kk;
            for (kk=0; kk<_dest.length; kk++) {
                _result.push({ value: _dest[kk], text: _grp+' \u2192 '+_dest[kk] });
            }
            return _result;
        },
        sendEvent(e) {
            let row = e.target.dataset.row;
            this.sendEventWithRow(row);
        },
        sendEventWithRow(row) {
            let clientEl = $('#evt-'+row+'-client');
            let destinationEl = $('#evt-'+row+'-destination');
            let valueEl = $('#evt-'+row+'-value');
            let client = clientEl.val();
            let destination = destinationEl.val();
            let value = valueEl.val();

            if (!client || client.trim()==='' || !destination || destination.trim()==='' || !value || value.trim()==='') {
                alert('You must select an event destination and provide a value');
                if (!destination || destination.trim()==='') destinationEl.focus();
                else valueEl.focus();
                return;
            }

            if (client[0]=='#') client = client.substring(1);
            destination; value;
            let url = `/event/send/${client}/${destination}/${value}`;
            this.sendEventCommand(row, url, null, null, client, destination, value);
        },
        startEventGenerator(row) {
            let clientEl = $('#evt-'+row+'-client');
            let destinationEl = $('#evt-'+row+'-destination');
            let fromEl = $('#evt-'+row+'-from');
            let toEl = $('#evt-'+row+'-to');
            let intervalEl = $('#evt-'+row+'-interval');
            let client = clientEl.val();
            let destination = destinationEl.val();
            let from = fromEl.val();
            let to = toEl.val();
            let interval = intervalEl.val();

            if (!client || client.trim()==='' || !destination || destination.trim()==='') {
                alert('You must select an event destination');
                destinationEl.focus();
                return;
            }
            if (!from || from.trim()==='' || !to || to.trim()==='') {
                alert('You must enter the values range (from, to)');
                fromEl.focus();
                return;
            }
            if (from>to) {
                alert('From value must lower or equal to To value');
                fromEl.focus();
                return;
            }
            if (!interval || interval.trim()==='') {
                alert('You must enter Event generation period');
                intervalEl.focus();
                return;
            }
            if (interval<1) {
                alert('Event generation period must be at least 1');
                intervalEl.focus();
                return;
            }
            interval = Math.round(1000*interval);

            let on_success = function(_this) {
                $('#evt-'+row+'-client').prop('disabled', true);
                $('#evt-'+row+'-destination').prop('disabled', true);
                $('#evt-'+row+'-type').prop('disabled', true);
                $('#evt-'+row+'-value').prop('disabled', true);
                $('#evt-'+row+'-from').prop('disabled', true);
                $('#evt-'+row+'-to').prop('disabled', true);
                $('#evt-'+row+'-interval').prop('disabled', true);
                $('#evt-'+row+'-btn').prop('disabled', true);
                $('#evt-'+row+'-btn-start').prop('disabled', true);
                $('#evt-'+row+'-btn-stop').prop('disabled', false);
                _this.setEventGeneratorButtonsStates(row, false, true);
                _this.commands[row].exec.generatorStarted = true;
            };

            if (client[0]=='#') client = client.substring(1);
            let url = `/event/generate-start/${client}/${destination}/${interval}/${from}/${to}`;
            this.sendEventCommand(row, url, on_success, null, client, destination, 0, from, to, interval);
        },
        stopEventGenerator(row) {
            const _this_cmd = this.commands[row];
            if (!_this_cmd || !_this_cmd.exec) {
                this.setEventGeneratorButtonsStates(row, true, false);
            } else {
                let on_success = function(_this) {
                    $('#evt-'+row+'-client').prop('disabled', false);
                    $('#evt-'+row+'-destination').prop('disabled', false);
                    $('#evt-'+row+'-type').prop('disabled', false);
                    $('#evt-'+row+'-value').prop('disabled', false);
                    $('#evt-'+row+'-from').prop('disabled', false);
                    $('#evt-'+row+'-to').prop('disabled', false);
                    $('#evt-'+row+'-interval').prop('disabled', false);
                    $('#evt-'+row+'-btn').prop('disabled', false);
                    $('#evt-'+row+'-btn-start').prop('disabled', false);
                    $('#evt-'+row+'-btn-stop').prop('disabled', true);
                    _this.setEventGeneratorButtonsStates(row, true, false);
                    _this.commands[row].exec.generatorStarted = false;
                };
                let client = _this_cmd.exec.client;
                let destination = _this_cmd.exec.destination;
                let url = `/event/generate-stop/${client}/${destination}`;
                this.sendEventCommand(row, url, on_success, null, client, destination);
            }
        },
        setEventGeneratorButtonsStates(row, stateState, stopState) {
            let startBtn = $('#evt-'+row+'-btn-start');
            let stopBtn = $('#evt-'+row+'-btn-stop');
            startBtn.toggleClass('btn-success',stateState).toggleClass('btn-secondary',!stateState).toggleClass('disabled',!stateState);
            stopBtn.toggleClass('btn-danger',stopState).toggleClass('btn-secondary',!stopState).toggleClass('disabled',!stopState);
        },
        sendEventCommand(row, url, on_success, on_fail, client, destination, value, from, to, interval) {
            //console.log('sendEventCommand: ', 'row='+row, 'url='+url);

            if (!this.commands[row]) this.commands[row] = { };
            const _this_cmd = this.commands[row];
            _this_cmd.exec = {
                url: url,
                client: client,
                destination: destination,
                value: value,
                from: from,
                to: to,
                interval: interval,
                startTm: new Date().getTime(),
                running: true
            };
            $('#evt-'+row+'-client').prop('disabled', true);
            $('#evt-'+row+'-destination').prop('disabled', true);
            $('#evt-'+row+'-type').prop('disabled', true);
            $('#evt-'+row+'-value').prop('disabled', true);
            $('#evt-'+row+'-from').prop('disabled', true);
            $('#evt-'+row+'-to').prop('disabled', true);
            $('#evt-'+row+'-interval').prop('disabled', true);
            $('#evt-'+row+'-btn').prop('disabled', true);
            $('#evt-'+row+'-btn-start').prop('disabled', true);
            $('#evt-'+row+'-btn-stop').prop('disabled', true);

            let result = undefined;
            let _this = this;

            $.get(url).then(
                // success handler
                function(data, textStatus, jqXHR) {
                    textStatus; jqXHR;
                    //console.log('sendEventCommand: OK: ', jqXHR.readyState, jqXHR.responseText, jqXHR.status, jqXHR.statusText);
                    //console.log('sendEventCommand: OK: ', data, textStatus, jqXHR);

                    _this_cmd.exec.result = 'Server response: '+data;
                    _this_cmd.exec.status = (data==='OK' ? 'success' : 'warning');
                    //console.log('sendEventCommand: OK: ', _this_cmd.exec);

                    result = _this_cmd.exec.status;
                },
                // fail handler
                function(jqXHR, textStatus, errorThrown) {
                    console.warn('sendEventCommand: NOT_OK: url: ', url);
                    console.warn('sendEventCommand: NOT_OK: ', jqXHR.readyState, jqXHR.responseText, jqXHR.status, jqXHR.statusText);
                    console.warn('sendEventCommand: NOT_OK: ', jqXHR, 'textStatus=', textStatus, 'errorThrown=', errorThrown);

                    _this_cmd.exec.result = (jqXHR.readyState!==4)
                            ? 'Network error: Is server running?'
                            : 'Server error: ' + jqXHR.status+' '+jqXHR.responseText;
                    _this_cmd.exec.status = 'danger';
                    console.warn('sendEventCommand: NOT_OK: ', _this_cmd.exec);

                    result = 'fail';
                }
            )
            .always(function() {
                $('#evt-'+row+'-client').prop('disabled', false);
                $('#evt-'+row+'-destination').prop('disabled', false);
                $('#evt-'+row+'-type').prop('disabled', false);
                $('#evt-'+row+'-value').prop('disabled', false);
                $('#evt-'+row+'-from').prop('disabled', false);
                $('#evt-'+row+'-to').prop('disabled', false);
                $('#evt-'+row+'-interval').prop('disabled', false);
                $('#evt-'+row+'-btn').prop('disabled', false);
                $('#evt-'+row+'-btn-start').prop('disabled', false);
                $('#evt-'+row+'-btn-stop').prop('disabled', false);

                _this_cmd.exec.running = false;
                _this_cmd.exec.endTm = new Date().getTime();
                _this_cmd.exec.duration = _this_cmd.exec.endTm-_this_cmd.exec.startTm;
                //console.log('sendCommand: ALWAYS: ', _this_cmd.exec);

                if (result==='success' && on_success) on_success(_this);
                else if (on_fail) on_fail(_this);
            });
            console.log('sendEventCommand: event command sent: ', url);
            result;
        },
        cancelSend(row) {
            row;
            alert('Cancel is not implemented');
        },
    },
}
</script>