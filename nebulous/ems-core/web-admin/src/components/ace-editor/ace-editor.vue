<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
  See:  https://www.npmjs.com/package/vue3-ace-editor
        https://stackoverflow.com/questions/90178/make-a-div-fill-the-height-of-the-remaining-screen-space
        https://www.npmjs.com/package/mime-types
        https://www.npmjs.com/package/mime-db
-->

<template>
    <div :style="style">
        <div style="display: flex; flex-flow: column; height: 100%;">
            <div style="flex: 1 1 auto;">
                <v-ace-editor
                        :id="id"
                        v-model:value="editorText"
                        @init="editorInit"
                        :lang="editorMode"
                        :readonly="editorReadonly"
                        :theme="editorTheme"
                        :options="{ wrap: editorWrap, indentedSoftWrap: editorIndentedWrap }"
                        style="width: 100%; height: 100%; box-sizing: border-box;" />
            </div>

            <div v-if="showModeList || showThemeList || showReadOnly || showWrap" style="flex: 0 1 auto; text-align: center;">
                <span v-if="showModeList">
                    Language/Mode:
                    <select v-model="editorMode">
                        <option v-for="mode in ace_modes"
                                :key="mode"
                                :value="mode">{{mode}}</option>
                    </select>
                </span>
                <span v-if="showThemeList">
                    &nbsp;Theme:
                    <select v-model="editorTheme">
                        <option v-for="theme in ace_themes"
                                :key="theme"
                                :value="theme">{{editorThemeName( theme )}}</option>
                    </select>
                </span>
                <span v-if="showReadOnly">
                    &nbsp;Read Only:
                    <input type="checkbox" v-model="editorReadonly" />
                </span>
                <span v-if="showWrap">
                    &nbsp;Wrap:
                    <input type="checkbox" v-model="editorWrap" />
                    &nbsp;Indented:
                    <input type="checkbox" v-model="editorIndentedWrap" :disabled="!editorWrap" />
                </span>
            </div>
        </div>
    </div>
</template>

<script>
import { VAceEditor } from 'vue3-ace-editor';
import 'ace-builds/src-noconflict/mode-xml.js';
import 'file-loader?esModule=false!ace-builds/src-noconflict/worker-xml.js'; // For webpack / vue-cli

const modelist = require('ace-builds/src-noconflict/ext-modelist');
const themelist = require('ace-builds/src-noconflict/ext-themelist');

const mimeTypes = require('mime-types');

export default {
    name: 'ace-editor',
    components:  { VAceEditor },
    props: {
        id: String,
        modelValue: { type: String, required: true },
        mode: { type: String, required: true },
        theme: { type: String, default: 'chrome' },
        readonly: { type: Boolean, default: false },
        wrap: { type: Boolean, default: false },
        style: { type: Object },
        showModeList: { type: Boolean, default: false },
        showThemeList: { type: Boolean, default: false },
        showReadOnly: { type: Boolean, default: false },
        showWrap: { type: Boolean, default: false },
    },

    data() {
        return {
            editor: null,
            editorMode: this.mode || 'xml',
            editorTheme: this.theme || 'chrome',
            editorReadonly: this.readonly,
            editorWrap: this.wrap,
            editorIndentedWrap: true,
            ace_modes: Object.keys(modelist.modesByName).sort(),
            ace_themes: Object.keys(themelist.themesByName).sort(),
        };
    },

    emits: [ 'update:modelValue' ],
    computed: {
        editorText: {
            get: function() {
                return this.modelValue
            },
            set: function(value) {
                this.$emit('update:modelValue', value)
            }
        }
    },

    beforeMount() {
        // Register editor modes
        for (let m of this.ace_modes) {
            try {
                //console.log('ACE: requiring mode: ', m);
                require( 'ace-builds/src-noconflict/mode-'+m+'.js' );
            } catch (e) {
                console.debug('ACE: Mode not found: ', m, e);
            }
        }

        // Register editor themes
        for (let t of Object.keys(themelist.themesByName).sort()) {
            try {
                //console.log('ACE: requiring theme: ', t);
                require( 'ace-builds/src-noconflict/theme-'+t+'.js' );
            } catch (e) {
                console.debug('ACE: Theme not found: ', t, e);
            }
        }
    },

    methods: {
        editorThemeName(themeId) {
            let name = themeId.replaceAll('_', ' ');
            return name[0].toUpperCase() + name.slice(1);
        },
        editorInit(ace) {
            //console.log('ACE: editorInit: ', ace);
            this.editor = ace;
        },
        setModeWithMime(mime) {
            let ext = mimeTypes.extension( mime.split(';')[0] );
            console.log('ACE: setModeWithMime: mime=', mime, 'ext=', ext);
            this.setModeWithFilePath(ext);
        },
        setModeWithFilePath(filePath) {
            let ext = filePath.substring(filePath.lastIndexOf('.')+1, filePath.length) || filePath;
            ext = ext.substring(ext.lastIndexOf('/')+1, ext.length) || ext;

            let mode = modelist.modesByName.text;
            if (ext!=='') {
                if (ext in modelist.modesByName) {
                    mode = modelist.modesByName[ext];
                } else {
                    for (let m of modelist.modes) {
                        if (m.extensions) {
                            let extArr = m.extensions.split('|');
                            for (let s of extArr) {
                                if (s===ext)
                                    mode = m;
                            }
                        }
                    }
                }
            }
            console.log('ACE: setModeWithFileName: ', filePath, mode);
            this.editorMode = mode.name;
        }
    }
};
</script>