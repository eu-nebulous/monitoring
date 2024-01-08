<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->

<!--
    See: https://codepen.io/fskirschbaum/pen/MYJNaj
         https://stackoverflow.com/questions/5560248/programmatically-lighten-or-darken-a-hex-color-or-rgb-and-blend-colors
         and https://github.com/PimpTrizkit/PJs/wiki/12.-Shade,-Blend-and-Convert-a-Web-Color-(pSBC.js)
-->

<template>
    <div :id="id" :class="['status-led', blink?'status-led-blink':'']" @click="toggleBlinking();"></div>
</template>

<script>
var $ = require('jquery');

import { eventBus } from "@/main.js"

export default {
    name: 'status-led',
    props: {
        id: String,
        on: { type: Boolean, default: false },
        blink: { type: Boolean, default: false },
        width: { type: Number, default: 18 },
        height: { type: Number, default: 18 },
        colorOn: { type: String, required: true },    // Required property
        colorOff: { type: String },
        colorOffDarkening: { type: Number, default: -0.5 },
        blinkPeriod: String,
    },
    data() {
        let css = this.calcStyle();
        return {
            state: this.blink || this.on,
            blinking: this.blink,
            blinking_period: this.blinkPeriod,

            width_calc: this.width+'px',
            height_calc: this.height+'px',

            colorOn_calc: css.colorOn_calc,
            colorOff_calc: css.colOff,
            shadows: css.shadows,
            shadows_50: css.shadows_50,
        };
    },
    mounted() {
        if (this.id && this.id.trim()!=='') {
            eventBus.on(this.id+'_turnOn', () => this.tunOn() );
            eventBus.on(this.id+'_turnOff', () => this.tunOff() );
            eventBus.on(this.id+'_toggleState', () => this.toggleState() );
            eventBus.on(this.id+'_toggleBlinking', () => this.toggleBlinking() );
        }
    },
    watch: {
        on(newVal) { this.setState(newVal); },
        blink(newVal) { this.setBlinking(newVal); },
        colorOn() { this.toggleState(); this.toggleState(); },
        colorOff() { this.toggleState(); this.toggleState(); },
        colorOffDarkening() { this.toggleState(); this.toggleState(); },
        blinkPeriod() { this.toggleBlinking(); this.toggleBlinking(); },
    },
    methods: {
        getState()  { return this.state; },
        setState(b) {
            if (b==this.state) return;
            if (b==false) this.setBlinking(false);
            this.state = b;
            let css = this.calcStyle();
            $(this.$el).css('background-color', this.state ? css.colorOn_calc : css.colorOff_calc);
            $(this.$el).css('box-shadow', css.shadows);
        },
        toggleState() { this.setState( ! this.getState() ); },
        turnOn()  { this.setState(true); },
        turnOff() { this.setState(false); },

        getBlinking()  { return $(this.$el).hasClass('status-led-blink'); },
        setBlinking(b)  {
            if (b==this.blinking) return;
            //if (this.state==false) this.turnOn();
            this.blinking = b;
            if (b) $(this.$el).addClass('status-led-blink');
            else   $(this.$el).removeClass('status-led-blink');
        },
        toggleBlinking() { this.setBlinking( ! this.getBlinking() ); },

        calcStyle() {
            let _data = { };
            _data['state'] = this.state ?? (this.blink || this.on);
            _data['blinking'] = this.blinking ?? this.blink;
            _data['blinking_period'] = this.blinking_period ?? this.blinkPeriod;

            _data['colOn'] = this.colorOn;
            _data['colOff'] = this.colorOff ?? this.pSBC(this.colorOffDarkening, this.colorOn, "c");
            _data['colStatus'] = _data.state ? _data.colOn : _data.colOff,
            _data['colOff_tr'] = _data.colOff.replace('rgb','rgba').replace(')',', 0.5)'),
            _data['colorOn_calc'] = _data.blink ? _data.colOn : _data.colStatus;
            _data['colorOff_calc'] = _data.colOff;

            _data['shadow1'] = 'rgba(0, 0, 0, 0.2) 0 -1px '+Math.round(this.height*7/24)+'px 1px';
            _data['shadow2'] = 'inset #131313 0 -1px '+Math.round(this.height*9/24)+'px';
            _data['shadow3_on']  = _data.colOff_tr + ' 0 2px '+Math.round(this.height*12/24)+'px';
            _data['shadow3_off'] = _data.colOff_tr + ' 0 2px 0';
            _data['shadow3'] = _data.blinking ? _data.shadow3_on : (_data.state ? _data.shadow3_on : _data.shadow3_off);
            _data['shadows'] = _data.shadow1+', '+_data.shadow2+', '+_data.shadow3;
            _data['shadows_50'] = _data.shadow1+', '+_data.shadow2+', '+_data.shadow3_off;
            return _data;
        },

        // See: https://stackoverflow.com/questions/5560248/programmatically-lighten-or-darken-a-hex-color-or-rgb-and-blend-colors
        pSBC(p,c0,c1,l) {
            let r,g,b,P,f,t,h,i=parseInt,m=Math.round,a=typeof(c1)=="string";
            if(typeof(p)!="number"||p<-1||p>1||typeof(c0)!="string"||(c0[0]!='r'&&c0[0]!='#')||(c1&&!a))return null;
            if(!this.pSBCr)this.pSBCr=(d)=>{
                let n=d.length,x={};
                if(n>9){
                    [r,g,b,a]=d=d.split(","),n=d.length;
                    if(n<3||n>4)return null;
                    x.r=i(r[3]=="a"?r.slice(5):r.slice(4)),x.g=i(g),x.b=i(b),x.a=a?parseFloat(a):-1
                }else{
                    if(n==8||n==6||n<4)return null;
                    if(n<6)d="#"+d[1]+d[1]+d[2]+d[2]+d[3]+d[3]+(n>4?d[4]+d[4]:"");
                    d=i(d.slice(1),16);
                    if(n==9||n==5)x.r=d>>24&255,x.g=d>>16&255,x.b=d>>8&255,x.a=m((d&255)/0.255)/1000;
                    else x.r=d>>16,x.g=d>>8&255,x.b=d&255,x.a=-1
                }return x};
            h=c0.length>9,h=a?c1.length>9?true:c1=="c"?!h:false:h,f=this.pSBCr(c0),P=p<0,t=c1&&c1!="c"?this.pSBCr(c1):P?{r:0,g:0,b:0,a:-1}:{r:255,g:255,b:255,a:-1},p=P?p*-1:p,P=1-p;
            if(!f||!t)return null;
            if(l)r=m(P*f.r+p*t.r),g=m(P*f.g+p*t.g),b=m(P*f.b+p*t.b);
            else r=m((P*f.r**2+p*t.r**2)**0.5),g=m((P*f.g**2+p*t.g**2)**0.5),b=m((P*f.b**2+p*t.b**2)**0.5);
            a=f.a,t=t.a,f=a>=0||t>=0,a=f?a<0?t:t<0?a:a*P+t*p:0;
            return "rgb"+(f?"a(":"(")+r+","+g+","+b+(f?","+m(a*1000)/1000:"")+")";
            //if(h)return"rgb"+(f?"a(":"(")+r+","+g+","+b+(f?","+m(a*1000)/1000:"")+")";
            //else return"#"+(4294967296+r*16777216+g*65536+b*256+(f?m(a*255):0)).toString(16).slice(1,f?undefined:-2)
        }
    }
}
</script>

<style>

.status-led {
    margin: 0 auto;
    width: v-bind(width_calc);
    height: v-bind(height_calc);
    background-color: v-bind(colorOn_calc);
    border-radius: 50%;
    box-shadow: v-bind(shadows);
    margin: 2px 2px;
    float: left;
}

.status-led-blink {
    -webkit-animation: blinkLed v-bind(blinking_period) infinite;
    -moz-animation: blinkLed v-bind(blinking_period) infinite;
    -ms-animation: blinkLed v-bind(blinking_period) infinite;
    -o-animation: blinkLed v-bind(blinking_period) infinite;
    animation: blinkLed v-bind(blinking_period) infinite;
}

@-webkit-keyframes blinkLed {
    from { background-color: v-bind(colorOn); }
    50% { background-color: v-bind(colorOff_calc); box-shadow: v-bind(shadows_50); }
    to { background-color: v-bind(colorOn); }
}
@-moz-keyframes blinkLed {
    from { background-color: v-bind(colorOn); }
    50% { background-color: v-bind(colorOff_calc); box-shadow: v-bind(shadows_50); }
    to { background-color: v-bind(colorOn); }
}
@-ms-keyframes blinkLed {
    from { background-color: v-bind(colorOn); }
    50% { background-color: v-bind(colorOff_calc); box-shadow: v-bind(shadows_50); }
    to { background-color: v-bind(colorOn); }
}
@-o-keyframes blinkLed {
    from { background-color: v-bind(colorOn); }
    50% { background-color: v-bind(colorOff_calc); box-shadow: v-bind(shadows_50); }
    to { background-color: v-bind(colorOn); }
}
@keyframes blinkLed {
    from { background-color: v-bind(colorOn); }
    50% { background-color: v-bind(colorOff_calc); box-shadow: v-bind(shadows_50); }
    to { background-color: v-bind(colorOn); }
}

</style>