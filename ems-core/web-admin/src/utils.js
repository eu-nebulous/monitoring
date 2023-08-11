/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

const precision = 100;

class Utils {
    valueExists(modelValue, name) {
        if (!modelValue) return false;
        let part = name.split('.');
        let d = modelValue;
        for (let i=0; i<part.length; i++) {
            if (!part[i]) continue;
            let n = part[i].trim();
            if (n==='') continue;
            if (!d[n]) return false;
            d = d[n];
        }
        return true;
    }
    getValue(modelValue, name) {
        let part = name.split('.');
        let d = modelValue;
        for (let i=0; i<part.length; i++) {
            if (!part[i]) continue;
            let n = part[i].trim();
            if (n==='') continue;
            if (!d[n]) return null;
            d = d[n];
        }
        return d;
    }

    toIsoFormat(data, inUnit, outPart) {
        let mult = inUnit==='s' || inUnit==='sec' ? 1000 : 1;
        let start = 0;
        let len = 100;
        if (outPart==='time') { start = 11; len = 8; }
        if (outPart==='time+frac') { start = 11; }
        if (outPart==='frac' || outPart==='fraction') { start = 19; len = 4; }
        if (outPart==='date') { start = 0; len = 10; }
        if (outPart==='datetime') { start = 0; len = 19; }
        if (outPart==='tz' || outPart==='timezone') { start = 23; len = 1; }
        return new Date(data * mult).toISOString().substr(start, len);
    }

    toDuration(data) {
        let s = data % 60;
        data = (data-s) / 60;
        let m = data % 60;
        data = (data-m) / 60;
        let h = data;
        return h+':'+new String(100+m).substr(1)+':'+new String(100+s).substr(1);
    }

    toKB(data) {
        return (data) ? (Math.round(precision * data / 1024) / precision).toString() : data;
    }

    toMB(data) {
        return (data) ? (Math.round(precision * data / 1024 / 1024) / precision).toString() : data;
    }

    toGB(data) {
        return (data) ? (Math.round(precision * data / 1024 / 1024 / 1024) / precision).toString() : data;
    }

    toNum(num, fragDigits) {
        //return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        if (!fragDigits || fragDigits<0) fragDigits = 0;
        return new Intl.NumberFormat('en-US', {
            minimumFractionDigits: fragDigits,
            maximumFractionDigits: fragDigits
        }).format(num);
    }

    orderOfMagnitude(data) {
        if (!data || typeof data !== 'number') return false;
        let d = Math.abs(data);
        let r = 0;
        while (d>=1) {
            r++;
            d = d / 10;
        }
        return r;
    }

    /*updateSelect(newVal, targetMap, valueField, textField) {
        // add new or update targetMap entries
        for (let c of newVal) {
            if (!targetMap[c[valueField]] || targetMap[c[valueField]].text !== c[textField]) {
                targetMap[c[valueField]] = { value: c[valueField], text: c[textField] };
                console.log('updateMap: ADD/UPD: ', c.id, targetMap[c.id]);
            }
        }

        // remove obsolete targetMap entries
        let newVal_ids = newVal.map(o=>o[valueField]);
        for (let cid of Object.keys(targetMap)) {
            if (!newVal_ids.includes(cid)) {
                delete targetMap[cid];
                console.log('updateMap: DEL: ', cid);
            }
        }
    }*/
}

var utils = new Utils();

export default utils;