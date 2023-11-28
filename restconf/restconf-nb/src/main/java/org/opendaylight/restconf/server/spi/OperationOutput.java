/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.DatabindAware;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * Output of {@link RpcImplementation#invoke(java.net.URI, OperationInput)}.
 */
public record OperationOutput(
        @NonNull DatabindContext databind,
        @NonNull Inference operation,
        @Nullable ContainerNode output) implements DatabindAware {
    public OperationOutput {
        requireNonNull(databind);
        requireNonNull(operation);
        if (output != null && output.isEmpty()) {
            output = null;
        }
    }
}