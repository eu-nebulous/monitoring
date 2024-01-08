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
            <!-- Default box -->
            <Card bodyClasses="table-responsive p-0"
                  header="Send commands to EMS clients"
                  icon="fas fa-th-list"
                  :hasRefresh="true" :hasCollapse="true" :hasMaximize="true" :hasRemove="false"
                  runRefresh="alert('Do refresh.\nHeader: '+this.header)"
            >
                <ClientCommands :clients="clients" :commands="commands"/>
            </Card>
        </div>
        <!-- /.col-md-6 -->

        <div class="col-6">
            <!-- Default box -->
            <Card bodyClasses="table-responsive p-0"
                  header="Publish events to EMS clients"
                  icon="fas fa-th-list"
                  :hasRefresh="true" :hasCollapse="true" :hasMaximize="true" :hasRemove="false"
                  runRefresh="alert('Do refresh.\nHeader: '+this.header)"
            >
                <ClientEvents :clients="clients" :destinations="destinationsPerGrouping"
                              :groupings="groupings" :numRows="7"/>
            </Card>
        </div>
        <!-- /.col-md-6 -->
    </div>

    <div class="row">
        <div class="col-12">
            <!-- Default box -->
            <Card bodyClasses="table-responsive p-0"
                  header="Call EMS REST API"
                  icon="fas fa-th-list"
                  :hasRefresh="true" :hasCollapse="true" :hasMaximize="true" :hasRemove="false"
            >
                <div style="padding: 5px 15px;">
                    <RestCall rootId="rest_call" :sseRef="sseRef" />
                </div>
            </Card>
        </div>
        <!-- /.col-md-12 -->
    </div>

    <div class="row">
        <div class="col-6">
            <!-- Default box -->
            <Card bodyClasses="table-responsive p-0"
                  header="&nbsp;CDO Repository"
                  icon="fas fa-project-diagram"
                  :hasRefresh="true" :hasCollapse="true" :hasMaximize="true" :hasRemove="false"
            >
                <div style="padding: 5px 15px;">
                    <CdoMgnt restCallRootId="rest_call" />
                </div>
            </Card>
        </div>
        <!-- /.col-md-6 -->

        <div class="col-6">
            <!-- Default box -->
            <Card bodyClasses="table-responsive p-0"
                  header="&nbsp;File Manager"
                  icon="fas fa-folder-open"
                  :hasRefresh="true" :hasCollapse="true" :hasMaximize="true" :hasRemove="false"
            >
                <div style="padding: 5px 15px;">
                    <FileExplorer />
                </div>
            </Card>
        </div>
        <!-- /.col-md-6 -->
    </div>

</template>

<script>
import Card from '@/components/card/card.vue';
import ClientCommands from './widgets/client-commands.vue';
import ClientEvents from './widgets/client-events.vue';
import RestCall from './widgets/rest-call.vue';
import CdoMgnt from './widgets/cdo-mgnt.vue';
import FileExplorer from './widgets/fileexplorer.vue';

export default {
    name: 'Admin Dashboard - Commands section',
    components: {
        Card, ClientCommands, ClientEvents, RestCall, CdoMgnt, FileExplorer
    },
    props: {
        modelValue: Object,
        sseRef: String
    },
    data() {
        return {
            commands: {
                'cmd-01': { client: '*', command: 'GET-ID' },
                'cmd-02': { client: '*', command: 'GET-ACTIVE-GROUPING' },
                'cmd-03': { client: '*', command: 'GET-GROUPING-CONFIG PER_INSTANCE' },
                'cmd-04': { client: '*', command: 'CLUSTER-TEST START' },
                'cmd-05': { client: '*', command: 'CLUSTER-TEST STOP' },
                'cmd-06': { client: '*', command: 'CLUSTER-EXEC broker list' },
                'cmd-07': { client: '*', command: '' },
            },
        };
    },

    computed: {
        clients() {
            if (!this.modelValue || !this.modelValue['baguette-server'] || !this.modelValue['baguette-server']['active-clients-map']) return;
            let activeClients = this.modelValue['baguette-server']['active-clients-map'];
            Object.values(activeClients).forEach(val => val.name = val['node-hostname']+` (${val['ip-address']})`);
            return activeClients;
        },
        groupings() {
            return (this.modelValue.translator && this.modelValue.translator.groupings)
                    ? this.modelValue.translator.groupings
                    : [ ];
        },
        destinationsPerGrouping() {
            return (this.modelValue.translator && this.modelValue.translator['destinations-per-grouping'])
                    ? this.modelValue.translator['destinations-per-grouping']
                    : { };
        },
    },
}
</script>

<style scoped>
</style>
