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
        <div class="col-3">
            <select id="root" class="form-control form-control-sm" v-model="rootId" v-on:change="rootChange" :disabled="disabled">
                <option v-for="(root, index) in roots" :key="index" :value="index">[{{index}}] {{root}}</option>
            </select>
        </div>
        <div class="col-1">
            <a href="javascript:void(0)" v-on:click="rootChange" class="btn btn-sm" title="Refresh files"><i class="fas fa-sync" /></a>
        </div>
        <div class="col-8">
            <input id="path" class="form-control form-control-sm" v-model="path" v-on:change="pathChange" :disabled="disabled" />
        </div>
    </div>
    <div class="row">
        <div class="col-12">
            <div style="margin: 10px 0px; min-height: 50px; height: 300px; border: 1px solid #ced4da; border-radius: .2rem; overflow: auto; resize: vertical;">
                <div  v-if="!disabled" class="table-responsive-sm">
                    <!--<span v-if="isRoot()" style="padding-left: 10px; color: grey; font-style: italic;">[ {{roots[rootId]}} ]</span>-->
                    <table id="files" class="table table-striped table-hover table-sm">
                        <thead>
                            <tr id="columns">
                                <th scope="col" class="p-0">&nbsp;</th>
                                <th scope="col" v-on:click="sortColumn" style="cursor: pointer; white-space: nowrap;"><i class="fas fa-sort" />&nbsp;File</th>
                                <th scope="col" v-on:click="sortColumn" style="cursor: pointer; white-space: nowrap;"><i class="fas fa-sort" />&nbsp;Type</th>
                                <th scope="col" v-on:click="sortColumn" style="cursor: pointer; white-space: nowrap;"><i class="fas fa-sort" />&nbsp;Size</th>
                                <th scope="col" v-on:click="sortColumn" style="cursor: pointer; white-space: nowrap;"><i class="fas fa-sort" />&nbsp;Modified</th>
                                <th scope="col">Permissions</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-if="! isRoot()">
                                <td></td>
                                <td><a href="javascript:void(0)" v-on:click="folderUp"><i class="fas fa-arrow-up" /> ..</a></td>
                                <td></td>
                                <td></td>
                                <td></td>
                                <td></td>
                            </tr>
                            <tr v-for="f in files" :key="f" style="font-size: small;">
                                <td class="align-middle p-0 pl-1">
                                    <small><a v-if="!f.dir && !f.noLink" href="javascript:void(0);" v-on:click="openInViewer(f.type, f.path, $event)" style="color: green"><i class="fas fa-eye" /></a></small>
                                </td>
                                <td class="align-middle">
                                    <a v-if="!f.noLink" href="javascript:void(0)" v-on:click="fileClick(f)">{{f.path.substring(1)}}</a>
                                    <span v-else>{{f.path.substring(1)}}</span>
                                    <span v-if="f.hidden" style="color:darkred; font-size:small; font-style:italic;"> [Hidden]</span>
                                </td>
                                <td v-if="f.dir" style="color: grey; font-style: italic;" class="table-info align-middle">&lt;DIR&gt;</td>
                                <td v-else style="color: grey; font-style: italic;" class="text-center align-middle">{{f.type}}</td>
                                <td class="text-right align-middle">{{f.size}}</td>
                                <td class="align-middle">{{new Date(f.lastModified).toLocaleDateString('sv')+' '+new Date(f.lastModified).toLocaleTimeString('sv')}}</td>
                                <td class="text-center align-middle">[{{f.read?'r':'-'}}{{f.write?'w':'-'}}{{f.exec?'x':'-'}}]</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div v-else style="background-color: #e9ecef; width: 100%; height: 100%;"></div>
            </div>
        </div>
    </div>

    <teleport to="body" :disabled="!showModal" v-if="showModal">
        <Modal id="cdo-viewer" width="80%" @close-modal-request="showModal=false">
            <template v-slot:header>
                <div style="width:100%; display: flex; justify-content: center;">
                    <b>[File] {{rootId}}: {{modalPath}}</b>
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
                        :showModeList="true"
                        :showThemeList="false"
                        :showReadOnly="false"
                        :showWrap="true"
                        ref="fileViewer"
                />
            </div>

            <template v-slot:footer>
                <div style="width:100%; text-align: center;">
                    <button v-on:click="downloadFile(rootId, modalPath)" class="btn btn-outline-dark btn-sm"><i class="fas fa-download" /> Download to file</button>
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

import Modal from '@/components/modal/modal.vue';
import AceEditor from '@/components/ace-editor/ace-editor';

export default {
    name: 'File Explorer',
    components: { Modal, AceEditor },
    data() {
        return {
            disabled: false,
            roots: [ ],
            rootId: 0,
            path: '',
            pathOk: '',
            files: [ ],
            showModal: false,
            modalPath: '',
            modalText: '',
            modalTextEditable: false,
        };
    },
    mounted() {
        this.restCall('/files', (_this, responseText) => {
            if (responseText!=='') {
                let d = JSON.parse(responseText);
                if (Array.isArray(d)) {
                    _this.roots = _this.toPath(d);
                    _this.rootId = _this.roots.length>0 ? 0 : -1;
                    _this.rootChange();
                    return;
                }
            }
            _this.disabled = true;
            _this.roots = [];
            _this.rootId = -1;
            _this.path = _this.pathOk = '';
            _this.files = [ ];
        },
        (_this) => _this.disabled = true);
    },
    methods: {
        rootChange() {
            this.path = '';
            this.$nextTick(() => this.refreshFiles() );
            this.clearSort();
        },
        pathChange() {
            this.refreshFiles();
        },
        pathNotFound(_this, xhr) {
            if (xhr.status==404) {
                alert('Path not found');
                _this.path = _this.pathOk;
                return true;
            }
            return false;
        },
        normalizePath(path) {
            return path.trim().replace(/^[\\/]+/, "");
        },
        isRoot() {
            return this.normalizePath(this.pathOk)==='';
        },
        refreshFiles() {
            let _rootId = this.rootId;
            if (_rootId<0) {
                console.warn('Web Admin: File Manager: No Root exists or no Root selected');
                return;
            }
            let _path = this.normalizePath(this.path);
            this.restCall(
                '/files/dir/'+_rootId+'/'+_path,
                (_this, d) => {
                    if (d!=null && d!=='') {
                        try {
                            d = JSON.parse(d);
                            if (Array.isArray(d)) {
                                _this.pathOk = _path;
                                _this.files = d;
                                _this.addFileTypes();
                                return;
                            }
                        } catch (e) {
                            console.error('Exception while processing server response: ', e, 'Server data: ', d);
                        }
                    }
                    _this.path = _this.pathOk;
                    alert('Error occurred while retrieving file list');
                },
                this.pathNotFound);
        },
        folderUp() {
            let _path = this.path;
            let _newPath = '';
            if (_path!=='') {
                let _p = _path.lastIndexOf('/');
                if (_p>0)
                    _newPath = _path.substring(0, _p);
            }
            this.path = _newPath;
            this.$nextTick(() => this.refreshFiles() );
        },
        fileClick(file) {
            let _newPath = this.normalizePath(this.path + file.path);
            if (file.dir) {
                // Open folder
                this.path = _newPath;
                this.pathChange();
            } else {
                // Download file
                this.downloadFile(this.rootId, _newPath);
            }
        },
        downloadFile(rootId, path) {
            try {
                var downloadLink = document.createElement('a');
                downloadLink.target = '_blank';
                downloadLink.href = '/files/get/'+rootId+'/'+path;
                document.body.append(downloadLink);
                downloadLink.click();
                document.body.removeChild(downloadLink);
            } catch (e) {
                console.error('Exception while downloading file: '+rootId+': '+path, e);
            }
        },

        addFileTypes() {
            let _this = this;
            $(this.files).each((i,f) => {
                if (f.dir) f.type = '<DIR>';
                else f.type = _this.getFileType(f);
                if (f.path==='MY_IP') alert(f.type);
            });
        },
        getFileType(f) {
            let p1 = f.path.lastIndexOf('.');
            let p2 = f.path.lastIndexOf('/');
            if (p1>p2 && p1>-1) {
                return f.path.substring(p1+1).toLowerCase();
            }
            return '';
        },
        toPath(obj) {
            if (Array.isArray(obj))
                return obj.map(p => p.path);
            else
                return obj.path;
        },

        restCall(url, fnSuccess, fnError) {
            let _this = this;
            this.$nextTick(() => {
                $.ajax({
                    url: url,
                    complete: function(xhr,status) {
                        if (status!=='error' && xhr.readyState==4) {
                            fnSuccess(_this, xhr.responseText, xhr, status);
                        } else {
                            let _show = true;
                            if (fnError) {
                                let _b = fnError(_this, xhr, status);
                                if (typeof _b === 'boolean')
                                    _show = ! _b;
                            }

                            if (_show) {
                                let _mesg = xhr.responseText;
                                try { let _r = JSON.parse(_mesg); if (_r && _r.message) _mesg = _r.message; } catch (e) { e; }
                                alert(` AJAX status: ${status} \n HTTP status: ${xhr.status} ${xhr.statusText} \n Message: \n ${_mesg}`);
                            }
                        }
                    }
                });
            });
        },

        sortColumn(e) {
            this.clearSort();
            let el = $(e.target);
            let text = el.text().trim().toLowerCase();

            let col = '';
            switch (text) {
                case 'file': col = 'path'; break;
                case 'modified': col = 'lastModified'; break;
                default: col = text;
            }

            let i = $(el.children().get(0));
            i.removeClass('fa-sort');

            let colSort = el.attr('data-sort');
            if (!colSort || colSort!=='asc') {
                colSort = 'asc';
                i.addClass('fa-sort-up');
                this.sortFiles(col, true);
            } else if (colSort==='asc') {
                colSort = 'desc';
                i.addClass('fa-sort-down');
                this.sortFiles(col, false);
            }
            el.attr('data-sort', colSort);
        },
        clearSort() {
            $('#columns').children().each((index,col) => {
                $(col).data('sort','');
                let i = $($(col).children());
                i.addClass('fa-sort');
                i.removeClass('fa-sort-up');
                i.removeClass('fa-sort-down');
            });
        },
        sortFiles(col, asc) {
            let f = asc ? 1 : -1;
            this.files.sort((a,b) => {
                return (typeof a[col]==='string')
                    ? f * a[col].localeCompare(b[col])
                    : f * (a[col] - b[col]);
            });
        },

        openInViewer(type, path, e) {
            let spinner;
            $(e.target).after(spinner = $(`
                        <div class="spinner-border spinner-border-sm text-success" role="status">
                            <span class="sr-only">Loading...</span>
                        </div>
            `));
            $(e.target).hide();

            path = this.normalizePath(this.path + path);
            this.restCall(
                '/files/get/'+this.rootId+'/'+path,
                (_this, data, xhr) => {
                    $(e.target).show();
                    spinner.remove();
                    if (data!=null && data!=='') {
                        this.modalPath = path;
                        this.modalText = data;
                        this.showModal = true;
                        this.$nextTick(() => {
                            if (this.$refs.fileViewer && this.$refs.fileViewer.setModeWithFilePath) {
                                xhr;
                                //let mime = xhr.getResponseHeader('content-type');
                                //this.$refs.fileViewer.setModeWithMime(mime);
                                this.$refs.fileViewer.setModeWithFilePath(path);
                            }
                        });
                        return;
                    }
                    alert('Error occurred while retrieving file');
                },
                (_this, xhr) => {
                    $(e.target).show();
                    spinner.remove();
                    return this.pathNotFound(_this, xhr);
                });
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
    },
}
</script>

<style>
thead tr:nth-child(1) th {
    background: white;
    position: sticky;
    top: 0;
    z-index: 10;
}
</style>