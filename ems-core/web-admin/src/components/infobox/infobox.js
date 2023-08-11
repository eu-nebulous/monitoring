/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

export default {
  name: 'infobox',
  props: {
    value: String,
    id: String,
    classes: String,
    message: String,
    text_before: String,
    text_default: String,
    text_after: String,
    bg_class: String,
    icon_classes: String,
    loading: Boolean,
    style: { type: String, default: 'padding:0;' }
  }
}
