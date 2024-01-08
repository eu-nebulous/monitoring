<!--
  ~ Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
  ~
  ~ This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
  ~ Esper library is used, in which case it is subject to the terms of General Public License v2.0.
  ~ If a copy of the MPL was not distributed with this file, you can obtain one at
  ~ https://www.mozilla.org/en-US/MPL/2.0/
  -->
<template>
    <!-- Content Header (Page header) -->
    <section class="content-header">
        <div class="container-fluid">
            <div class="row mb-2">
                <div class="col-sm-6">
                    <h1>{{title}}</h1>
                </div>
                <div class="col-sm-6">
                    <ol class="breadcrumb float-sm-right">
                        <li class="breadcrumb-item"><router-link to="/">Home</router-link></li>
                        <li v-for="item in breadcrumb" :key="item.index" class="breadcrumb-item active">{{item.name}}</li>
                    </ol>
                </div>
            </div>
        </div><!-- /.container-fluid -->
    </section>
</template>

<script>
export default {
  name: 'ViewTitle',
  data() {
    return {
      title: '',
      breadcrumb: [],
    }
  },
  watch: {
    $route (to /*, from*/) {
      this.title = to.name;
      this.breadcrumb = [ ];
      if (to.fullPath!=='/') this.breadcrumb.push({ name: to.name, url: to.fullPath });
    }
  }
}
</script>