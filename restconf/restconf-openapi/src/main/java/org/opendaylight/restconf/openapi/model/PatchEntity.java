/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.HttpMethod;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class PatchEntity extends OperationEntity {

    private final String fullName;

    public PatchEntity(final SchemaNode schema, final String deviceName, final String moduleName,
            final List<ParameterEntity> parameters, final String refPath, final String fullName) {
        super(schema, deviceName, moduleName, parameters, refPath);
        this.fullName = fullName;
    }

    @Override
    protected String operation() {
        return "patch";
    }

    @Override
    @Nullable String summary() {
        return SUMMARY_TEMPLATE.formatted(HttpMethod.PATCH, moduleName(), deviceName(), nodeName());
    }

    @Override
    void generateResponses(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart(RESPONSES);
        generator.writeObjectFieldStart(String.valueOf(OK.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "OK");
        generator.writeEndObject(); //end of 200
        generator.writeObjectFieldStart(String.valueOf(NO_CONTENT.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "Updated");
        generator.writeEndObject(); //end of 204
        generator.writeEndObject();
    }

    @Override
    void generateRequestBody(final @NonNull JsonGenerator generator) throws IOException {
        final var ref = COMPONENTS_PREFIX + moduleName() + "_" + refPath();
        generator.writeObjectFieldStart(REQUEST_BODY);
        generator.writeStringField(DESCRIPTION, nodeName());
        generator.writeObjectFieldStart(CONTENT);
        generator.writeObjectFieldStart("application/yang-data+json");
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeObjectFieldStart(PROPERTIES);
        generator.writeObjectFieldStart(fullName);
        if (schema() instanceof ListSchemaNode) {
            generator.writeStringField(TYPE, ARRAY);
            generator.writeObjectFieldStart(ITEMS);
            generator.writeStringField(REF, ref);
            generator.writeStringField(TYPE, OBJECT);
            generator.writeEndObject(); //end of items
        } else {
            generator.writeStringField(REF, ref);
            generator.writeStringField(TYPE, OBJECT);
        }
        generator.writeEndObject(); //end of nodeName
        generator.writeEndObject(); //end of props
        generator.writeEndObject(); //end of schema
        generator.writeEndObject(); //end of json
        generateMediaTypeSchemaRef(generator, "application/yang-data+xml", ref);
        generator.writeEndObject(); //end of content
        generator.writeEndObject(); //end of request body
    }
}