/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

/**
 * Constants for streams.
 */
public final class RestconfStreamsConstants {
    public static final String STREAM_PATH_PARAM_NAME = "path";
    public static final String DATASTORE_PARAM_NAME = "datastore";
    public static final String SCOPE_PARAM_NAME = "scope";
    public static final String OUTPUT_TYPE_PARAM_NAME = "notification-output-type";

    public static final String DS_URI = '/' + DATASTORE_PARAM_NAME + '=';
    public static final String SCOPE_URI = '/' + SCOPE_PARAM_NAME + '=';

    public static final String DATA_SUBSCRIPTION = "data-change-event-subscription";
    public static final String CREATE_DATA_SUBSCRIPTION = "create-" + DATA_SUBSCRIPTION;
    public static final String NOTIFICATION_STREAM = "notification-stream";
    public static final String CREATE_NOTIFICATION_STREAM = "create-" + NOTIFICATION_STREAM;

    public static final String STREAMS_PATH = "ietf-restconf-monitoring:restconf-state/streams";
    public static final String STREAM_PATH_PART = "/stream=";
    public static final String STREAM_PATH = STREAMS_PATH + STREAM_PATH_PART;
    public static final String STREAM_ACCESS_PATH_PART = "/access=";
    public static final String STREAM_LOCATION_PATH_PART = "/location";

    public static final String DATA_CHANGE_EVENT_STREAM_PATTERN = '/' + DATA_SUBSCRIPTION + "/*";
    public static final String YANG_NOTIFICATION_STREAM_PATTERN = '/' + NOTIFICATION_STREAM + "/*";

    private RestconfStreamsConstants() {
        // Hidden on purpose
    }
}
