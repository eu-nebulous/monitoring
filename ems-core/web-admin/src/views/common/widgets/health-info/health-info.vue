<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <!-- Health Indicators Dropdown Menu -->
    <li class="nav-item dropdown">
        <a class="nav-link" data-toggle="dropdown" href="#">
          <StatusLed id="EmsStatusLed" :colorOn="overall_led.color" :on=overall_led.on :blink=overall_led.blink blinkPeriod="1.5s"></StatusLed>
          &nbsp;
          <span id="EmsStatus">{{overall_health}}</span>
        </a>
        <div class="dropdown-menu dropdown-menu-lg dropdown-menu-right">
          <span class="dropdown-item dropdown-header">EMS subsystems</span>

          <template v-for="item in components" :key="item.id">
            <div class="dropdown-divider"></div>
            <a :href=item.href class="dropdown-item">
              <i class="fas fa-puzzle-piece mr-2"></i> {{item.name}}
              <span class="float-right text-muted text-sm">
                <StatusLed :id="item.id" :colorOn="item.led.color" :on=item.led.on :blink=item.led.blink :width=12 :height=12></StatusLed>
              </span>
              <span class="float-right text-muted text-sm">&nbsp;&nbsp;</span>
              <span class="float-right text-muted text-sm">
                <small>{{item.health}}</small>
              </span>
            </a>
          </template>

        </div>
    </li>
</template>

<script>
import StatusLed from '@/components/status-led/status-led.vue';

export default {
  name: 'health-info',
  components: {
    StatusLed
  },
  data() {
    let healthData = [
        { id: 'CsHealthLed', href: "#CsSection", name: 'Control Service', health: 'UP' },
        { id: 'BcHealthLed', href: "#BcSection", name: 'Broker-CEP', health: 'UP' },
        { id: 'BsHealthLed', href: "#BsSection", name: 'Baguette Server', health: 'UP' },
        { id: 'BciHealthLed',href: "#BciSection",name: 'Client Installer', health: 'UP' },
        { id: 'TrHealthLed', href: "#TrSection", name: 'Translator', health: 'UP' },
        { id: 'IsHealthLed', href: "#IsSection", name: 'Info Service', health: 'UP' },
    ];
    let componentsList = [];
    let upCount = 0;
    let downCount = 0;
    let otherCount = 0;
    for (let d of healthData) {
        let _health;
        let _led;
        switch (d.health) {
            case 'UP': upCount++;
                _health = 'Normal';
                _led = { color: '#0F0', on: true, blink: false };
                break;
            case 'DOWN': downCount++;
                _health = 'Error';
                _led = { color: '#F00', on: true, blink: true };
                break;
            default: otherCount++;
                _health = 'Unknown';
                _led = { color: '#888', on: false, blink: false };
                break;
        }
        componentsList.push( { id: d.id, href: d.href, name: d.name, health: _health, led: _led } );
    }
    let _overall_health = (downCount>0) ? 'Errors' : (otherCount>0 ? (upCount>0 ? 'Issues' : 'Unknown') : (upCount>0 ? 'Normal' : 'Unknown'));
    let _overall_led = (downCount>0)
        ? { color: '#F00', on: true, blink: true }
        : (otherCount>0
            ? (upCount>0
                ? { color: '#FFA500', on: true, blink: true }
                : { color: '#888', on: false, blink: false }
            )
            : (upCount>0
                ? { color: '#0F0', on: true, blink: false }
                : { color: '#888', on: false, blink: false }
            )
        );
    return {
        components: componentsList,
        overall_health: _overall_health,
        overall_led: _overall_led,
    };
  }
}
</script>
