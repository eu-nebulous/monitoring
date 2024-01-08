<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <div class="container">
        <div class="row">

            <!-- CDO Repository Tree -->
            <div class="col-6 border rounded-lg">
                <div class="form-group row">
                    <div class="w-100 text-center text-nowrap bg-secondary text-white" style="margin-bottom: 0px;">
                        CDO repository&nbsp;&nbsp;
                        <button type="button" class="btn btn-tool text-white" data-toggle="tooltip" title="CDO Refresh" v-on:click="cdoRefresh()">
                            <i class="fas fa-sync" v-if="!cdoRefreshing"></i>
                            <div v-if="cdoRefreshing" class="spinner-border spinner-border-sm" role="status">
                                <span class="sr-only">Loading...</span>
                            </div>
                        </button>

                        <div class="float-right">
                            Filter:
                            <input id="cdo-repository-tree-filter" type="checkbox" checked data-toggle="toggle" v-on:change="cdoRefresh()" />
                            &nbsp;&nbsp;&nbsp;
                        </div>
                    </div>
                </div>
                <small v-if="cdoTreeData==null"><i>Press &nbsp;<small class="text-muted"><i class="fas fa-sync"></i></small>&nbsp; button above to load CDO repository contents</i></small>
                <div v-if="cdoTreeError!==''" class="alert alert-danger">{{cdoTreeError}}</div>
                <div class="form-group" id="cdo-repository-tree">
                    <div v-for="(type,path) of cdoTreeData" :key="path">
                        <a href="javascript:void(0)" class="link-danger text-danger" v-on:click="cdoDelete(path)">
                            <i class="fas fa-times-circle" />
                        </a>
                        &nbsp;
                        <span v-if="getCdoItemLinkByType(type)">
                            <a href="javascript:void(0)" class="link-primary" v-on:click="cdoExport(path)">{{path}}</a>
                            <a href="javascript:void(0);" v-on:click="gotoRestPane(type, path)" style="color: green; margin-left: 5px;"><small><i class="fas fa-forward" /></small></a>
                            <a href="javascript:void(0);" v-on:click="openInViewer(type, path, $event)" style="color: blue; margin-left: 5px;"><small><i class="fas fa-eye" /></small></a>
                        </span>
                        <span v-else>{{path}}</span>
                        <span class="float-right text-sm font-italic" style="color:grey;"><img :src="getCdoItemIconByType(type)" width="18" height="18" :title="getCdoItemTextByType(type)" /></span><br/>
                    </div>
                </div>
            </div>

            <!-- CDO Import Form -->
            <div class="col-6 border rounded">
                <div class="form-group row">
                    <p class="w-100 text-center text-nowrap bg-secondary text-white" style="margin-bottom: 0px;">Import a resource into CDO repository</p>
                </div>
                <form id="cdo-import-form" style="padding-left: 20px !important; padding-right: 20px !important;">
                    <div class="row">
                        <div v-if="cdoImportSuccess!==''" class="w-100 alert alert-success" role="alert">{{cdoImportSuccess}}</div>
                        <div v-if="cdoImportError!==''" class="w-100 alert alert-danger" role="alert">{{cdoImportError}}</div>
                    </div>
                    <div class="form-group row form-check form-check-inline">
                        <label for="cdo-import-resource-path" class="col-form-label col-form-label-sm">Resource:</label>
                        <input id="cdo-import-resource-path" class="form-control form-control-sm" type="text" name="cdo-import-resource-path" />
                        <small><i>Give the full path of the resource in CDO repository</i></small>
                    </div>
                    <div class="form-group row form-check form-check-inline">
                        <label for="cdo-import-operation-1" class="col-form-label col-form-label-sm">Import mode:</label>&nbsp;&nbsp;&nbsp;
                        <input id="cdo-import-operation-1" type="radio" name="cdo-import-operation" value="PUT" />&nbsp;
                        <label class="form-check-label col-form-label col-form-label-sm" for="cdo-import-operation-1">Create</label>&nbsp;&nbsp;&nbsp;
                        <input id="cdo-import-operation-2" type="radio" name="cdo-import-operation" value="POST" checked="checked" />&nbsp;
                        <label class="form-check-label col-form-label col-form-label-sm" for="cdo-import-operation-2">Create or Replace</label>
                    </div>
                    <div class="form-group row">
                        <label for="cdo-import-file" class="col-form-label col-form-label-sm">XMI file:</label>
                        <TextareaDnd id="cdo-import-text"
                                     name="cdo-import-text"
                                     class="form-control"
                                     placeholder="XMI content"
                        />
                        <small><i>Select an XMI file or paste the XMI content in the next area</i></small>
                        <input id="cdo-import-file" type="file" name="cdo-import-file"
                               class="form-control form-control-sm" accept=".xmi"
                               v-on:change="loadFile()"
                        />
                    </div>
                    <div class="form-group row">
                        <div class="col-sm-6 text-center">
                            <button type="button" class="btn btn-primary btn-sm" v-on:click="cdoImport()">Import</button>
                        </div>
                        <div class="col-sm-6 text-center">
                            <button type="reset" class="btn btn-warning btn-sm" v-on:click="clearForm()">Clear</button>
                        </div>
                    </div>
                </form>
            </div>

        </div>
    </div>

    <teleport to="body" :disabled="!showModal" v-if="showModal">
        <Modal id="cdo-viewer" width="80%" @close-modal-request="showModal=false">
            <template v-slot:header>
                <div style="width:100%; display: flex; justify-content: center;">
                    <b>[CDO] {{modalPath}}</b>
                    <a href="#" @click="showModal=!showModal" style="position:absolute; right:0;">
                        <span style="color: grey; font-weight: normal;"><i class="fas fa-times" /></span>
                    </a>
                </div>
            </template>

            <div style="width: 100%; height: 100%; box-sizing: border-box; overflow: hidden;">
                <!--<textarea v-model="modalText" :readonly="! modalTextEditable" style="width: 100%; height: 100%; box-sizing: border-box; white-space: pre; overflow: auto;" />-->
                <AceEditor
                        id="ace-editor-1"
                        v-model="modalText"
                        mode="xml"
                        theme="cobalt"
                        :readonly="! modalTextEditable"
                        style="width: 100%; height: 100%; box-sizing: border-box;"
                        :showModeList="false"
                        :showThemeList="false"
                        :showReadOnly="false"
                        :showWrap="true"
                />
            </div>

            <template v-slot:footer>
                <div style="width:100%; text-align: center;">
                    <button v-on:click="saveToFile(createFileName(modalPath), modalText)" class="btn btn-outline-dark btn-sm"><i class="fas fa-download" /> Download to file</button>
                    &nbsp;&nbsp;&nbsp;
                    <button v-on:click="copyToClipboard(modalText)" class="btn btn-outline-dark btn-sm"><i class="fas fa-copy" /> Copy to clipboard</button>
                    &nbsp;&nbsp;&nbsp;
                    <button v-if="modalTextEditable" v-on:click="saveToCdo(modalPath, modalText, $event)" class="btn btn-outline-dark btn-sm"><i class="fas fa-save" /> Save to CDO</button>
                </div>
                <div style="width:10%; text-align: right;">
                    &nbsp;&nbsp;&nbsp;
                    <button v-on:click="showModal=!showModal" class="btn btn-dark btn-sm">Close</button>
                </div>
            </template>
        </Modal>
    </teleport>

</template>

<script>
const $ = require('jquery');
import TextareaDnd from './textarea-dnd.vue';
import Modal from '@/components/modal/modal.vue';

import AceEditor from '@/components/ace-editor/ace-editor';

import iconCamelModel  from "./img/camel-model-32.png";
import iconCpModel  from "./img/cp-model-32.png";
import iconFolder  from "./img/folder-64.png";
import iconOther  from "./img/unknown-64.png";

export default {
    name: 'Manage CDO repository',
    components: { TextareaDnd, Modal, AceEditor },
    props: {
        restCallRootId: String,
    },
    data() {
        return {
            uid: Math.round(Math.random()*10000000) + new Date().getTime(),
            cdoRefreshing: false,
            cdoTreeData: null,
            cdoTreeError: '',
            cdoImportSuccess: '',
            cdoImportError: '',
            showModal: false,
            modalPath: '',
            modalText: '',
            modalTextEditable: false,
            modalMime: '',
        };
    },
    methods: {
        cdoRefresh() {
            let filter = $('#cdo-repository-tree-filter').is(':checked');
            this.cdoRefreshing = true;
            this.cdoTreeError = '';
            this.callCdoEndpoint('/info/cdo?filter='+filter, 'LIST', null, null, null, null,
                (responseText) => {
                    this.cdoRefreshing = false;
                    this.cdoTreeData = $.parseJSON(responseText);
                },
                (error) => {
                    this.cdoRefreshing = false;
                    this.cdoTreeError = error;
                });
        },
        getCdoItemLinkByType(type) {
            let link = false;
            if (type==='CamelModel') link = true;
            if (type==='ConstraintProblem') link = true;
            return link;
        },
        getCdoItemIconByType(type) {
            let icon = iconOther;
            if (type==='CamelModel') icon = iconCamelModel;
            if (type==='ConstraintProblem') icon = iconCpModel;
            if (type==='FOLDER') icon = iconFolder;
            return icon;
        },
        getCdoItemTextByType(type) {
            let text = '';
            if (type==='CamelModel') text = 'Camel Model';
            if (type==='ConstraintProblem') text = 'Constraint Problem';
            return text;
        },

        loadFile() {
            var inp = $('#cdo-import-file');
            var txt = $('#cdo-import-text').val();

            if (txt==null || txt.trim()==='' || txt!=null && txt.trim()!=='' && confirm('Do you want to replace current text area content?')) {
                let files = inp[0].files;
                if (files.length > 0) {
                    let file = files[0];
                    let reader = new FileReader();
                    reader.onload = function(event) {
                        $('#cdo-import-text').val(event.target.result);
                    };
                    reader.readAsText(file);
                }
            }

            // Clear selected file
            //inp.val(null);
        },

        cdoImport() {
            var inputs = $('#cdo-import-form :input');
            var values = {};
            inputs.each(function() {
                if (this.name==='cdo-import-operation') {
                    if (this.checked)
                        values[this.name] = $(this).val();
                } else {
                    values[this.name] = $(this).val();
                }
            });
            //console.log('CDO import form - Values: ', values);

            let path = values['cdo-import-resource-path'].trim();
            let op = values['cdo-import-operation'].trim();
            let xmi = values['cdo-import-text'];

            var error = false;
            if (path==='') { error = true; alert('You must provide resource path in "Resource" field'); }
            if (op==='') { error = true; alert('You must select an "Import mode"'); }
            if (op!=='PUT' && op!=='POST') { error = true; alert('Invalid "Import mode": '+op); }
            if (xmi.trim()==='') { error = true; alert('You must select an "XMI file" or enter XMI content in text area'); }
            if (error) return;

            this.cdoImportSuccess = '';
            this.cdoImportError = '';

            inputs.prop('disabled', true);
            this.callCdoEndpoint('/info/cdo/'+path, 'IMPORT', op, 'text/xml', xmi, null,
                () => {
                    this.cdoImportSuccess = 'Success';
                    this.clearForm();
                    this.cdoRefresh();
                    inputs.prop('disabled', false);
                },
                (error) => {
                    this.cdoImportError = error;
                    inputs.prop('disabled', false);
                });
        },
        cdoExport(path) {
            this.cdoTreeError = '';
            this.callCdoEndpoint('/info/cdo/'+path, null, 'GET', null, null, 'text',
                    (responseText) => this.saveToFile(this.createFileName(path), responseText),
                    (error) => this.cdoTreeError = error);
        },
        cdoDelete(path) {
            if (confirm('Delete resource from CDO repository?\n\n'+path)) {
                this.cdoTreeError = '';
                this.callCdoEndpoint('/info/cdo/'+path, null, 'DELETE', null, null, null,
                    (responseText) => {
                        if (responseText!=='OK')
                            this.cdoTreeError = responseText;
                        else
                            this.cdoRefresh();
                    },
                    (error) => this.cdoTreeError = error);
            }
        },
        clearForm() {
            $('#cdo-import-form').trigger("reset");
            this.cdoImportSuccess = '';
            this.cdoImportError = '';
        },

        callCdoEndpoint(url,descr,method,contentType,data,dataType,fnSuccess,fnError,fnAlways) {
            url = url.replace(/\/+/gi, '/');
            this.$nextTick(() => {
                if (descr && descr.trim()!=='') descr = ' for '+descr; else descr = '';
                if (descr==='' && method && method.trim()!=='') descr = ' for '+method.trim();

                let _this = this;
                let options = {
                    url: url,
                    complete: function(xhr, status) {
                        //console.log('Call completed to CDO REST endpoint'+descr+': ', url, ' => ', status, xhr);
                        if (xhr.readyState==4 && xhr.status>=200 && xhr.status<300) {
                            let contentType = xhr.getResponseHeader("content-type") || "";
                            fnSuccess( xhr.responseText, contentType );
                        } else {
                            fnError( _this.createErrorMessage(xhr) );
                        }
                        if (fnAlways)
                            fnAlways(xhr, status);
                    }
                };
                if (method) options['type'] = method;
                if (contentType) options['contentType'] = contentType;
                if (data) options['data'] = data;
                if (dataType) options['dataType'] = dataType;

                //console.log('Calling CDO REST endpoint'+descr+'...', url);
                $.ajax(options);
            });
        },
        createErrorMessage(xhr) {
            var responseText = xhr.responseText;
            try {
                if (xhr.responseText) {
                    var r = $.parseJSON(xhr.responseText);
                    if (r.message) {
                        r = r.message.split('\n');
                        var rr = [];
                        rr.push(r[0]);
                        for (var i=1; i<r.length; i++) {
                            if (r[i].trim().startsWith('Caused')) {
                                rr.push(r[i].trim());
                            }
                        }
                        responseText = rr.join('\n');
                    }
                }
            } catch (e) { e; }
            return `An error occurred: status=${status}, readyState=${xhr.readyState}, response=${responseText}`;
        },

        createFileName(path) {
            let base = path[0]==='/' ? path.substring(1) : path;
            base = base.replace(/[^\w]/gi,'_');
            let tm = new Date().getTime();
            let ext = 'xmi';
            return `${base}_${tm}.${ext}`;
        },
        saveToFile(fileName, textData){
            let blobData = new Blob([textData], {type: "text/xml"});
            let url = window.URL.createObjectURL(blobData);

            let a = document.createElement("a");
            a.style = "display: none";
            document.body.appendChild(a);
            a.href = url;
            a.download = fileName;
            a.click();
            window.URL.revokeObjectURL(url);
            a.remove();
        },

        gotoRestPane(type, path) {
            if (this.$root[this.restCallRootId]) {
                let restCall = this.$root[this.restCallRootId];
                if (type==='CamelModel') {
                    restCall.switchToForm('new-app-model', { 'applicationId': path });
                } else
                if (type==='ConstraintProblem') {
                    restCall.switchToForm('new-cp-model', { 'cp-model-id': path });
                } else
                    console.log('Ignoring type: '+type);
            }
        },

        openInViewer(type, path, e) {
            this.cdoTreeError = '';
            let spinner;
            $(e.target).after(spinner = $(`
                        <div class="spinner-border spinner-border-sm text-info" role="status">
                            <span class="sr-only">Loading...</span>
                        </div>
            `));
            $(e.target).hide();
            this.callCdoEndpoint('/info/cdo/'+path, null, 'GET', null, null, 'text',
                    (responseText, contentType) => {
                        this.modalPath = path;
                        this.modalText = responseText;
                        this.modalMime = contentType;
                        this.showModal = true;
                    },
                    (error) => this.cdoTreeError = error,
                    () => {
                        $(e.target).show();
                        spinner.remove();
                    }
            );
        },
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
        saveToCdo(path, data, e) {
            // Save current #cdo-import-form contents
//            let oldPath = $('#cdo-import-resource-path').val();
//            let oldOp = $('#cdo-import-operation-2').prop("checked");
//            let oldData = $('#cdo-import-text').val();

            let btSave = null;
            let icon = null;
            let spinner = null;
            if (e && e.target) {
                btSave = $(e.target);
                icon = btSave.find('i');
                spinner = icon.before(`
                        <div class="spinner-border spinner-border-sm" role="status">
                            <span class="sr-only">Loading...</span>
                        </div>
                `);
                icon.hide();
                btSave.prop("disabled", true);
            }

            // Update #cdo-import-form with the given data
            $('#cdo-import-resource-path').val(path);
            $('#cdo-import-operation-2').prop("checked", true);
            $('#cdo-import-text').val(data);

            // Send form to CDO

            // Restore #cdo-import-form with its previous contents
//            $('#cdo-import-resource-path').val(oldPath);
//            $(oldOp ? '#cdo-import-operation-2' : '#cdo-import-operation-1').prop("checked", true);
//            $('#cdo-import-text').val(oldData);

            if (spinner!=null) spinner.remove();
            if (icon!=null) icon.show();
            if (btSave!=null) btSave.prop("disabled", false);
        },
    }
}
</script>