<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
    See: https://leafletjs.com/
         https://github.com/leaflet-extras/leaflet-providers
         https://leaflet-extras.github.io/leaflet-providers/preview/
         https://stackoverflow.com/questions/34897704/use-svg-as-map-using-leaflet-js
         https://github.com/Leaflet/Leaflet.markercluster
-->

<template>
    <div :class="wrapperClasses">
        <div :id="lid" :style="style"></div>
    </div>
</template>

<script>
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import 'leaflet-providers/leaflet-providers.js';

import 'leaflet.markercluster/dist/MarkerCluster.css';
import 'leaflet.markercluster/dist/MarkerCluster.Default.css';
import 'leaflet.markercluster/dist/leaflet.markercluster.js';

export default {
    name: 'Leaflet',
    props: {
        id: String,
        mapType: { type: String, default: 'openstreetmap' },
        mapTilesUrl: String,
        mapTilesConfig: Object,
        style: Object,
        wrapperClasses: String,
        markers: Array,
        markerTypes: Object,
        markerConnections: Array,
        useMarkerGrouping: { type: Boolean, default: false },
    },
    data() {
        return {
            markerTypeIcons: this.initMarkerTypeIcons(),
            currentMarkers: { }
        };
    },
    computed: {
        lid: function() { return this.id ?? 'leafjs_'+(Math.floor(Math.random()*1000000000)); },
    },
    mounted() {
        var map = L.map(this.lid).setView([0, 0], 1);

        switch (this.mapType) {
            case 'user-defined':
                var tileLayer = L.tileLayer(this.mapTilesUrl, this.mapTilesConfig);
                map.addLayer(tileLayer);
                break;
            case 'stadia-outdoors':
                // Stadia base map
                var Stadia_Outdoors = L.tileLayer('https://tiles.stadiamaps.com/tiles/outdoors/{z}/{x}/{y}{r}.png', {
                    maxZoom: 20,
                    attribution: '&copy; <a href="https://stadiamaps.com/">Stadia Maps</a>, &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors'
                });
                map.addLayer(Stadia_Outdoors);
                break;
            case 'openstreetmap':
            default:
                // OpenStreetMap base map
                var OpenStreetMap_Mapnik = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                });
                map.addLayer(OpenStreetMap_Mapnik);
        }

        /*// Add legend
        var legend = L.control({position: 'bottomleft'});
        legend.onAdd = function (map) {
            map;
            var div = L.DomUtil.create('div', 'info legend');
            //div.innerHTML = 'LEGEND -- TO DO';
            return div;
        };
        legend.addTo(map);*/

        this.map = map;
    },
    watch: {
        markers: {
            immediate: true,
            handler(newVal) {
                if (!this.map || this.map==null) return;
                if (this.useMarkerGrouping && (!this.markerGroup || this.markerGroup==null)) {
                    this.markerGroup = L.markerClusterGroup();
                    this.markerGroup.addTo(this.map);
                }

                // Add new markers or Update existing
                for (let markerData of newVal) {
                    // Get marker (if exists) by 'id'
                    let marker = this.currentMarkers[markerData.id];

                    // Add new marker if not already exists
                    if (!marker) {
                        marker = L.marker(markerData.latLng, this.markerTypeIcons['__default__']);
                        if (this.useMarkerGrouping)
                            this.markerGroup.addLayer(marker);
                        else
                            marker.addTo(this.map);
                        marker.bindTooltip('---', { permanent: true, className: "marker-tooltip", offset: [10, -15] });
                        marker.bindPopup('---');
                        marker._type = '';
                        this.currentMarkers[markerData.id] = marker;
                    }

                    // Update marker data (position, icon, tooltip and popup texts)
                    marker.setLatLng(markerData.latLng);
                    if (marker._type!==markerData.type) {
                        let markerIcon = this.markerTypeIcons[markerData.type];
                        if (!markerIcon) markerIcon = this.markerTypeIcons['__default__'];
                        marker.setIcon(markerIcon);
                        marker._type = markerData.type;
                    }
                    marker.setTooltipContent(markerData.tooltipText);
                    marker.setPopupContent(markerData.popupText);
                }

                // Remove markers not in 'newVal'
                let ids = newVal.map(x => x.id);
                let removedIds = Object.keys(this.currentMarkers).filter(x => !ids.includes(x));
                for (let id of removedIds) {
                    let m = this.currentMarkers[id];
                    if (m) {
                        if (this.useMarkerGrouping)
                            this.markerGroup.removeLayer(m);
                        else
                            this.map.removeLayer(m);
                        delete this.currentMarkers[id];
                    }
                }
            }
        },
        markerConnections: {
            immediate: true,
            handler(newVal) {
                if (!newVal || !this.map) return;

                // Clear previous polylines
                this.clearLayers();

                // Add new connections (as polylines)
                for (const conn of newVal) {
                    let id1 = conn.startMarker;
                    let id2 = conn.endMarker;
                    let mr1 = this.currentMarkers[id1];
                    let mr2 = this.currentMarkers[id2];
                    if (mr1 && mr2) {
                        let points = [mr1.getLatLng(), mr2.getLatLng()];
                        L.polyline(points, conn.line ?? { color: 'black' }).addTo(this.map);
                    }
                }
                this.map.invalidateSize();
            }
        },
    },
    methods: {
        initMarkerTypeIcons() {
            let markerTypeIcons = {};
            if (this.markerTypes) {
                for (const [type, conf] of Object.entries(this.markerTypes)) {
                    markerTypeIcons[type] = this.makeIcon(type+'-type-pin', conf.markerColor);
                }
            }
            if (!markerTypeIcons['__default__']) {
                markerTypeIcons['__default__'] = this.makeIcon('default-type-pin', 'green');
            }
            return markerTypeIcons;
        },
        makeIcon(markerClass, markerColor) {
            const icon = L.divIcon({
                className: markerClass,
                iconAnchor: [0, 24],
                labelAnchor: [-6, 0],
                popupAnchor: [0, -36],
                html: `<div class="pin bounce" style="background-color: ${markerColor};" />`
            });
            return icon;
        },
        clearLayers() {
            for (let i in this.map._layers) {
                if (this.map._layers[i]._path != undefined) {
                    try {
                        this.map.removeLayer(this.map._layers[i]);
                    } catch(e) {
                        console.log("Problem while removing layer ", this.map._layers[i], e);
                    }
                }
            }
        }
    }
}
</script>

<style>
/*.leaflet-default-icon-path {
    background-image: url(https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png);
}*/

/* Not used. See: https://stackoverflow.com/questions/23567203/leaflet-changing-marker-color */
/*.marker {
    background-color: darkgreen;
    width: 3rem;
    height: 3rem;
    display: block;
    left: -1.5rem;
    top: -1.5rem;
    position: relative;
    border-radius: 3rem 3rem 0;
    transform: rotate(45deg);
    border: 1px solid #FFFFFF`
}*/

/* See: https://cssdeck.com/labs/tedyvui4 */
.pin {
    width: 30px;
    height: 30px;
    border-radius: 50% 50% 50% 0;
    background: #00cae9;
    position: absolute;
    transform: rotate(-45deg);
    left: 50%;
    top: 50%;
    margin: -20px 0 0 -20px;
}
.pin:after {
    content: "";
    width: 14px;
    height: 14px;
    margin: 8px 0 0 8px;
    background: #e6e6e6;
    position: absolute;
    border-radius: 50%;
}

.bounce {
    animation-name: bounce;
    animation-fill-mode: both;
    animation-duration: 1s;
}

.pulse {
    background: #d6d4d4;
    border-radius: 50%;
    height: 14px;
    width: 14px;
    position: absolute;
    left: 50%;
    top: 50%;
    margin: 11px 0px 0px -12px;
    transform: rotateX(55deg);
    z-index: -2;
}
.pulse:after {
    content: "";
    border-radius: 50%;
    height: 40px;
    width: 40px;
    position: absolute;
    margin: -13px 0 0 -13px;
    animation: pulsate 1s ease-out;
    animation-iteration-count: infinite;
    opacity: 0;
    box-shadow: 0 0 1px 2px #00cae9;
    animation-delay: 1.1s;
}

@keyframes pulsate {
    0% {
        transform: scale(0.1, 0.1);
        opacity: 0;
    }

    50% {
        opacity: 1;
    }

    100% {
        transform: scale(1.2, 1.2);
        opacity: 0;
    }
}

@keyframes bounce {
    0% {
        opacity: 0;
        transform: translateY(-2000px) rotate(-45deg);
    }

    60% {
        opacity: 1;
        transform: translateY(30px) rotate(-45deg);
    }

    80% {
        transform: translateY(-10px) rotate(-45deg);
    }

    100% {
        transform: translateY(0) rotate(-45deg);
    }
}

.marker-tooltip {
    background-color: lightyellow;
}
</style>