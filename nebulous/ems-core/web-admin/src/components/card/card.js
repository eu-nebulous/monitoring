/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

export default {
  name: 'card',
  props: {
    id: String,
    classes: String,
    style: String,
    bodyClasses: String,
    header: String,
    icon: String,
    title: String,
    footer: String,
    links: Object,
    hasRefresh: Boolean,
    hasCollapse: Boolean,
    hasMaximize: Boolean,
    hasRemove: Boolean,
    runRefresh: String
  },
  methods: {
    doRefresh() {
        if (this.runRefresh && this.runRefresh!=='')
            eval(this.runRefresh);      // Can access local scope (and variables)
            //new Function( 'return (' + this.runRefresh + ')' )();     // Cannot access local scope (and variables)
    }
  }
}
