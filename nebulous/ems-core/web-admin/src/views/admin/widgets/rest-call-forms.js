/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

export const FORM_TYPE_OPTIONS = [
        {
            'id': 'basic-api-group',
            'text': 'Basic API - Model Translation',
            'priority': -1000,
            'options': [
                { 'id': 'new-app-model', 'text': 'Send App. model request', 'url': '/appModel', 'method': 'POST', 'form': 'app-model-form', 'priority': 1 },
                { 'id': 'new-app-exec-model', 'text': 'Send App. Execution model request', 'url': '/appExecModel', 'method': 'POST', 'form': 'app-exec-model-form', 'priority': 2 },
                { 'id': 'constants-update', 'text': 'Set constants (add name-value pairs in Payload)', 'url': '/appConstants', 'method': 'POST', 'form': '', 'priority': 3 },
                { 'id': 'translate-app-model', 'text': 'Translate App. model', 'url': '/translator/translate', 'method': 'POST', 'form': 'app-model-form', 'priority': 4 },
                { 'id': 'get-app-model', 'text': 'Current App. model', 'url': '/translator/currentAppModel', 'method': 'GET', 'form': '', 'priority': 5 },
                { 'id': 'get-app-exec-model', 'text': 'Current App. Execution model', 'url': '/translator/currentAppExecModel', 'method': 'GET', 'form': '', 'priority': 6 },
                { 'id': 'get-curr-tc', 'text': 'Current Translation Context', 'url': '/translator/currentTranslationContext', 'method': 'GET', 'form': '', 'priority': 7 },
            ]
        },

        {
            'id': 'topology-group',
            'text': 'Topology',
            'priority': 1,
            'options': [
                { 'id': 'new-vm', 'text': 'Register Node', 'url': '/baguette/registerNode', 'method': 'POST', 'form': 'vm-form', 'priority': 1 },
                { 'id': 'vm-reinstall', 'text': 'Reinstall Node', 'url': '/baguette/node/reinstall/{ip-address}', 'method': 'GET', 'form': 'ip-form', 'priority': 2 },
                { 'id': 'vm-unregister', 'text': 'Unregister Node', 'url': '/baguette/node/unregister/{ip-address}', 'method': 'GET', 'form': 'ip-form', 'priority': 3 },
                { 'id': 'vm-list', 'text': 'Node IP addresses', 'url': '/baguette/node/list', 'method': 'GET', 'form': '', 'priority': 4 },

                { 'id': 'topology-group-sep1', 'text': '-', 'disabled': true, 'priority': 5 },

                { 'id': 'client-list', 'text': 'Client list', 'url': '/client/list', 'method': 'GET', 'form': '', 'priority': 11 },
                { 'id': 'client-map', 'text': 'Client map', 'url': '/client/list/map', 'method': 'GET', 'form': '', 'priority': 12 },
                { 'id': 'node-info', 'text': 'Node Info by IP address', 'url': '/baguette/getNodeInfoByAddress/{ip-address}', 'method': 'GET', 'form': 'ip-form', 'priority': 13 },
                { 'id': 'node-name', 'text': 'Node Name by IP address', 'url': '/baguette/getNodeNameByAddress/{ip-address}', 'method': 'GET', 'form': 'ip-form', 'priority': 14 },
            ]
        },

        {
            'id': 'credentials-group',
            'text': 'Credentials',
            'priority': 1001,
            'options': [
                { 'id': 'get-bc-cred', 'text': 'EMS server Broker credentials', 'url': '/broker/credentials', 'method': 'GET', 'form': '', 'priority': 1 },
                { 'id': 'get-bs-cred', 'text': 'Baguette Server connection info', 'url': '/baguette/connectionInfo', 'method': 'GET', 'form': '', 'priority': 2 },
                { 'id': 'get-ref', 'text': 'VM credentials by Ref', 'url': '/baguette/ref/{ref}', 'method': 'GET', 'form': 'ref-form', 'priority': 3 },
                { 'id': 'new-otp', 'text': 'New OTP', 'url': '/ems/otp/new', 'method': 'GET', 'form': '', 'priority': 4 },
                { 'id': 'del-otp', 'text': 'Delete OTP', 'url': '/ems/otp/remove/{otp}', 'method': 'GET', 'form': 'otp-form', 'priority': 5 },
            ]
        },

        {
            'id': 'observability-group',
            'text': 'Observability',
            'priority': 1002,
            'options': [
                { 'id': 'get-all-logger-levels', 'text': 'Get All Loggers', 'url': '/actuator/loggers', 'method': 'GET', 'form': '', 'priority': 1 },
                { 'id': 'get-logger-level', 'text': 'Get Logger Level', 'url': '/actuator/loggers/{logger}', 'method': 'GET', 'form': 'logger-form', 'priority': 2 },
                { 'id': 'set-logger-level', 'text': 'Set Logger Level', 'url': '/actuator/loggers/{logger}', 'method': 'POST', 'form': 'logger-form', 'priority': 3 },

                { 'id': 'health', 'text': 'Health check', 'url': '/health', 'method': 'GET', 'form': '', 'priority': 4 }
            ]
        },

        {
            'id': 'debug-group',
            'text': 'Debug calls',
            'priority': 1003,
            'options': [
                { 'id': 'd-stop-baguette', 'text': 'Stop Baguette Server', 'url': '/baguette/stopServer', 'method': 'GET', 'form': '', 'priority': 1 },
                { 'id': 'd-shutdown', 'text': 'EMS server shutdown', 'url': '/ems/shutdown', 'method': 'GET', 'form': '', 'priority': 2 },
                { 'id': 'd-exit', 'text': 'EMS server shutdown and Exit', 'url': '/ems/exit/0', 'method': 'GET', 'form': '', 'priority': 3 },
                { 'id': 'd-restart', 'text': 'EMS server shutdown and Restart', 'url': '/ems/exit/99', 'method': 'GET', 'form': '', 'priority': 4 }
            ]
        }
    ];

export const FORM_SPECS = {
                '': { 'fields': [] },
                'app-model-form': {
                    'fields': [
                        { 'name': 'applicationId', 'text': 'App. model path' },
                        { 'name': 'app-model', 'text': 'App. Model' },
                        { 'name': 'notificationURI', 'text': 'Notification URI' },
                        { 'name': 'watermark.user', 'text': '-- User', 'defaultValue': function(_this) { return ('authUsername' in _this) ? _this.authUsername : ('username' in _this) ? _this.username : 'unknown'; } },
                        { 'name': 'watermark.system', 'text': '-- System', 'defaultValue': 'Ems-Web-Admin' },
                        { 'name': 'watermark.uuid', 'text': '-- UUID', 'defaultValue': function(_this) { return _this.create_UUID(); } },
                        { 'name': 'watermark.date', 'text': '-- Date', 'defaultValue': function() { return new Date().getTime(); } },
                    ],
                },
                'app-exec-model-form': {
                    'fields': [
                        { 'name': 'app-exec-model-id', 'text': 'App. Execution model path' },
                    ]
                },
                'vm-form': {
                    'fields': [
                        { 'name': 'id', 'text': 'VM Id' },
                        { 'name': 'name', 'text': 'VM Name' },
                        { 'name': 'operatingSystem', 'text': 'VM OS', 'defaultValue': 'UBUNTU' },
                        { 'name': 'type', 'text': 'VM type', 'defaultValue': 'VM' },
                        { 'name': 'provider', 'text': 'VM provider' },
                        { 'name': 'address', 'text': 'IP address' },
                        { 'name': 'ssh.port', 'text': 'SSH port', 'defaultValue': '22' },
                        { 'name': 'ssh.username', 'text': 'SSH username' },
                        { 'name': 'ssh.password', 'text': 'SSH password', 'type': 'password' },
                        { 'name': 'ssh.key', 'text': 'SSH key', 'type': 'password' },
                    ]
                },
                'logger-form': {
                    'fields': [
                        { 'name': 'logger', 'text': 'Logger name' },
                        { 'name': 'configuredLevel', 'text': 'New Level' },
                    ]
                },
                'ref-form': {
                    'fields': [
                        { 'name': 'ref', 'text': 'VM reference' },
                    ]
                },
                'ip-form': {
                    'fields': [
                        { 'name': 'ip-address', 'text': 'IP Address' },
                    ]
                },
                'otp-form': {
                    'fields': [
                        { 'name': 'otp', 'text': 'OTP' },
                    ]
                },
            };
