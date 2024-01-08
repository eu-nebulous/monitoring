<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
    See: https://jvectormap.com/download/
         https://github.com/alex-pex/jvectormap
         https://www.npmjs.com/package/jvectormap-next
         https://www.npmjs.com/package/jvectormap-content
         https://stackoverflow.com/questions/42710155/auto-zoom-a-region-on-click-event-in-jvectormap
         https://jvectormap.com/documentation/javascript-api/jvm-map/
         https://jvectormap.com/tutorials/getting-started/
         https://stackoverflow.com/questions/14728371/jvector-map-how-to-focus-on-a-marker
         https://stackoverflow.com/questions/48059365/scroll-control-in-jvectormap
-->

<template>
    <div :id="(id ?? jvmid)+'-wrapper'" style="position:relative;">
        <!--<input :id="(id ?? jvmid)+'-zoom-slider'" type="range"
            value="0" min="0" max="8" orient="vertical"
            style="position:absolute; height:25%; bottom:10px; text-align:center;">-->
        <div :id="id ?? jvmid" :class="classes" style="width:100%; height:100%"></div>
    </div>
</template>

<script>
var $ = require('jquery');
require('jvectormap-next')($);
$.fn.vectorMap('addMap', 'world_mill_en', require('jvectormap-content/world-mill'));

export default {
    name: 'jvectormap',
    props: {
        id: String,
        classes: String,
        width: { type:String, default:'100%' },
        height: { type:String, default:'500px' },
        options: Object,
        markers: Array,
    },
    data() {
        return {
            jvmid: 'jvectormap_'+(Math.floor(Math.random()*1000000000))
        }
    },
    mounted() {
        let _this = this;
        let _id = this.id ?? this.jvmid;
        let _conf = {
            map              : 'world_mill',
            zoomButtons      : false,
            zoomOnScroll     : true,
            normalizeFunction: 'polynomial',
            hoverOpacity     : 0.7,
            hoverColor       : false,
            backgroundColor  : 'transparent',
            regionStyle      : {
                initial      : {
                    fill            : 'rgba(210, 214, 222, 1)',
                    'fill-opacity'  : 1,
                    stroke          : 'none',
                    'stroke-width'  : 0,
                    'stroke-opacity': 1
                },
                hover        : {
                    'fill-opacity': 0.7,
                    cursor        : 'pointer'
                },
                selected     : {
                    fill: 'yellow'
                },
                selectedHover: {}
            },
            regionsSelectable: false,
            regionsSelectableOne: true,
            onRegionTipShow: function(e, el, code){
                el.html(el.html()+' ['+code+']');
            },

            markerStyle      : {
                initial: {
                    fill  : '#00a65a',
                    stroke: '#111'
                }
            },
            markersSelectable: false,
            markersSelectableOne: false,
            /*onMarkerTipShow: function(e, el, code){
                el.html(el.html()+' ['+code+']');
            },*/
            onMarkerClick: function(e, code){
                let mapId = $(e.target).parent().attr('id');
                let mapObj = $('#'+mapId).vectorMap('getMapObject');
                mapObj.vectorMap('setSelectedMarkers', [code]);
            },
            onMarkerSelected: function(e, code, isSelected, selectedMarkers){
                console.log('JVM: ', (isSelected?'':'de-')+'selected marker '+code, _this.markers[code].name);
                console.log('JVM: selected markers: ', selectedMarkers);
            },

            series: {
//                regions: [{
//                    values: {
//                        'AF': 16.63,
//                        'AL': 11.58,
//                        'DZ': 158.97,
//                    },
//                    scale: ['#C8EEFF', '#0071A4'],  // low value color, high value color
//                    normalizeFunction: 'polynomial'
//                }]
            },
        };
        _conf = Object.assign(_conf, this.options);
        _conf['markers'] = this.markers;

        $.fn.vectorMap('addMap', _conf.map, require('jvectormap-content/'+_conf.map.replace('_','-')));

        $("#"+_id+'-wrapper').css('width', this.width).css('height', this.height);
        //$("#"+_id).empty().css('width', '100%').css('height', '100%');
        let _map = $("#"+_id).vectorMap(_conf);
        this.mapElement = _map.vectorMap('mapObject');
        this.mapElementId = _id;

        //$("#"+_id+'-zoom-slider').change(function() { _this.zoomMap(this.value, _map[0]); });
        $("#"+_id+'-zoom-slider').on('input', function() { _this.zoomMap(this.value, _map[0]); });

        $("#"+_id+'-wrapper').closest('.card').bind('resize', function() {
            _map.vectorMap('updateSize');
        });
    },

    methods: {
        zoomMap(zoom) {
            let map = $('#'+(this.id ?? this.jvmid)).vectorMap('get','mapObject');
            map.setScale(zoom, map.width/2, map.height/2, false, true);
                        //(scale, anchorX, anchorY, isCentered, animation)
        },
        //See also: $('#map').vectorMap('get','mapObject').setFocus({region: code, animate: true});
        // and      $('#map').vectorMap('get','mapObject').setFocus({scale: 5, lat: -25.360790, lng: 131.016800, x: 0.5, y: 0.5, animate: true});
    },

    watch: {
        markers: {
            immediate: true,
            handler(newVal) {
                if (!this.mapElement || this.mapElement==null) return;
                let map = $('#'+(this.id ?? this.jvmid)).vectorMap('get','mapObject');
                map.removeAllMarkers();
                map.addMarkers(newVal);
            }
        }
    }
}
</script>

<style>
.jvectormap-container {
    width: 100%;
    height: 100%;
}
</style>