<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
  <!-- EMS Server-Side Events component (EMS-SSE) -->
  <ems-sse
          ref="ems_sse"
          :interval="1"
          :autoStart="true"
          :showDebugForm="false"
          :missingEventsWarnLimit="5"
          :missingEventsErrorLimit="10"
          :retryNum="-1"
          :retryDelay="10000"
          :retryBackoffFactor="1"
          v-model="emsSseData"
  />

  <!-- Site wrapper -->
  <div class="wrapper" style="min-height: 100vh;">
    <Header :links="menuItems" :showClock="true" v-model:emsData="emsSseData.data">
      <!--<SearchForm />-->
      <template v-slot:right-side>
        <!--<HealthInfo />
        <Notifications />-->
        <ConnectionInfo v-model="emsSseData.sseInfo" v-model:data="emsSseData.data"
                        sseRef="ems_sse" settingsTarget="#control-sidebar-widget-settings" />
      </template>
    </Header>

    <!--<MenuSidebar />-->

    <!-- Content Wrapper. Contains page content -->
    <div class="content-wrapper">
      <div class="bg-image background-circuit"></div>
      <div class="mask gradient-custom">

        <TitleAndBreadcrumb></TitleAndBreadcrumb>
        <router-view v-model="emsSseData" sseRef="ems_sse" />

      </div>
    </div>
    <!-- /.content-wrapper -->

    <ControlSidebar />

    <Footer />
  </div>
  <!-- ./wrapper -->

</template>

<script>
import EmsSse from '@/components/ems/sse/ems-sse.vue';

const menuItems = [
  { name: 'Home',  url: '/' },
//  { name: 'Sample', url: '/sample' },
//  { name: 'About', url: '/about' },
//  { name: 'Help',  url: '/help' },
];

import Header from '@/views/common/header/header.vue'
import Footer from '@/views/common/footer/footer.vue'
//import MenuSidebar from '@/views/common/menu-sidebar/menu-sidebar.vue'
import ControlSidebar from '@/views/common/control-sidebar/control-sidebar.vue'

//import SearchForm from '@/views/common/widgets/search/search.vue';
//import HealthInfo from '@/views/common/widgets/health-info/health-info.vue';
//import Notifications from '@/views/common/widgets/notifications/notifications.vue';
import ConnectionInfo from '@/views/common/widgets/connection-info/connection-info.vue';

import TitleAndBreadcrumb from '@/views/common/title-and-breadcrumb/title-and-breadcrumb.vue'

export default {
  name: 'App',
  components: {
    EmsSse,
    Header, Footer, /*MenuSidebar,*/ ControlSidebar, /*SearchForm,*/
    /*HealthInfo, Notifications,*/ ConnectionInfo, TitleAndBreadcrumb
  },
  emits: ['update:modelValue'],

  data() {
    return {
      menuItems: menuItems,
      emsSseData: { },
      ems_version: process.env.VUE_APP_EMS_VERSION,
      ems_build: process.env.VUE_APP_EMS_BUILD,
    }
  },

  mounted() {
    this.$router.push('/');
  }
}
</script>

<style>
  .background-circuit {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: url('./assets/img/backgrounds/circuit-board-5907811_1920-bw.png');
    opacity: 0.05;
  }

  .gradient-custom {
    /* fallback for old browsers */
    background: #eeeeee;

    /* Chrome 10-25, Safari 5.1-6 */
    background: -webkit-linear-gradient(45deg,
        rgba(255, 255, 255, 0.6),
        rgba(240, 240, 240, 0.6) 100%);

    /* W3C, IE 10+/ Edge, Firefox 16+, Chrome 26+, Opera 12+, Safari 7+ */
    background: linear-gradient(45deg,
        rgba(255, 255, 255, 0.6),
        rgba(240, 240, 240, 0.6) 100%);
  }
</style>