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
                    <div class="col-5">Client</div>
                    <div class="col-5">Command</div>
                    <div class="col-2">Actions</div>
                </div>
            </th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(cmd,c_id) of commands" :key="c_id">
            <td class="align-middle">
                <div class="row">
                    <div class="col-5">
                        <ClientsList :id="c_id+'-client'" :selected="cmd.client" :clients="clients" />
                    </div>
                    <div class="col-5">
                        <CommandInput :id="c_id+'-command'" :value="cmd.command" />
                    </div>
                    <div class="col-1">
                        <button :id="c_id+'-btn'" class="btn btn-success btn-xs align-middle" @click="sendCommand(c_id)">
                            <small>
                                <i class="fas fa-paper-plane"/>
                            </small>&nbsp;&nbsp;Send
                        </button>
                    </div>
                    <div class="col-1 text-center">
                        <span v-if="cmd.exec && cmd.exec.running" @click="cancelSend(cmd)" >
                            <small style="color: darkred;"><i class="fas fa-times-circle"/></small>
                        </span>
                        <span v-if="cmd.exec && cmd.exec.duration" @click="cmd.exec = undefined">
                            <small><i class="far fa-trash-alt"/></small>
                        </span>
                        <div v-if="cmd.exec && cmd.exec.running" class="spinner-border spinner-border-sm text-primary" role="status">
                            <span class="sr-only">Running...</span>
                        </div>
                    </div>
                </div>
                <div class="row" v-if="cmd.exec">
                    <div class="col-5">
                        <div style="font-size:smaller;">
                            Command: <span style="color: grey; white-space: nowrap;">{{cmd.exec.command}}</span>
                            <br/>
                            Client: <span style="color: grey;">{{cmd.exec.client}}</span>
                        </div>
                        <div style="font-size:smaller;" v-if="cmd.exec.duration">
                            Duration: <span style="color: grey;">{{cmd.exec.duration}}ms</span>
                        </div>
                    </div>
                    <div class="col-7">
                        <div :class="['text-'+cmd.exec.status]">
                            <small>
                                <b>{{cmd.exec.result}}</b>
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
import CommandInput from './command-input.vue';

export default {
    name: 'Client Commands widget',
    props: {
        commands: Object,
        clients: Object,
    },
    components: {
        ClientsList, CommandInput
    },
    methods: {
        sendCommand(cid) {
            let client = $('#'+cid+'-client').val();
            let command = $('#'+cid+'-command').val();
            if (client.trim()==='' || command.trim()==='') {
                alert('You must write a command');
                $('#'+cid+'-command').focus();
                return;
            }

            if (client[0]=='#') client = client.substring(1);
            let url = '/client/command/'+client+'/'+command;
            //console.log('sendCommand: ', 'cmd-id='+cid, 'url='+url);

            const _this_cmd = this.commands[cid];
            _this_cmd.exec = {
                command: command,
                client: client,
                startTm: new Date().getTime(),
                running: true
            };
            $('#'+cid+'-client').prop('disabled', true);
            $('#'+cid+'-command').prop('disabled', true);
            $('#'+cid+'-btn').prop('disabled', true);

            $.get(url).then(
                // success handler
                function(data, textStatus, jqXHR) {
                    textStatus; jqXHR;
                    //console.log('sendCommand: OK: ', jqXHR.readyState, jqXHR.responseText, jqXHR.status, jqXHR.statusText);
                    //console.log('sendCommand: OK: ', data, textStatus, jqXHR);

                    _this_cmd.exec.result = 'Server response: '+data;
                    _this_cmd.exec.status = (data==='OK' ? 'success' : 'warning');
                    //console.log('sendCommand: OK: ', _this_cmd.exec);
                },
                // fail handler
                function(jqXHR, textStatus, errorThrown) {
                    console.warn('sendCommand: NOT_OK: url: ', url);
                    console.warn('sendCommand: NOT_OK: ', jqXHR.readyState, jqXHR.responseText, jqXHR.status, jqXHR.statusText);
                    console.warn('sendCommand: NOT_OK: ', jqXHR, 'textStatus=', textStatus, 'errorThrown=', errorThrown);

                    _this_cmd.exec.result = (jqXHR.readyState!==4)
                            ? 'Network error: Is server running?'
                            : 'Server error: ' + jqXHR.status+' '+jqXHR.responseText;
                    _this_cmd.exec.status = 'danger';
                    console.warn('sendCommand: NOT_OK: ', _this_cmd.exec);
                }
            )
            .always(function() {
                $('#'+cid+'-client').prop('disabled', false);
                $('#'+cid+'-command').prop('disabled', false);
                $('#'+cid+'-btn').prop('disabled', false);

                _this_cmd.exec.running = false;
                _this_cmd.exec.endTm = new Date().getTime();
                _this_cmd.exec.duration = _this_cmd.exec.endTm-_this_cmd.exec.startTm;
                //console.log('sendCommand: ALWAYS: ', _this_cmd.exec);
            });
            console.log('sendCommand: command sent: ', url);
        },
        cancelSend(cmd) {
            cmd;
            alert('Cancel is not implemented');
        },
    },
}
</script>