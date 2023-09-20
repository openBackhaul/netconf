/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yangtools.yang.common.Revision;

class RestconfSchemaSourceUrlProviderTest {
    private static final String URL_PREFIX = "/" + URLConstants.BASE_PATH + "/modules";

    @Test
    @DisplayName("Unsupported module-set name.")
    void unsupportedModuleSet() {
        final var urlProvider = new RestconfSchemaSourceUrlProvider();
        final var result = urlProvider.getSchemaSourceUrl("some-module-set", "module", null);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest(name = "Supported module-set name. URL: {2}")
    @MethodSource("getSchemaSourceUrlArgs")
    void getSchemaSourceUrl(final String moduleName, final Revision revision, final Uri expected) {
        final var urlProvider = new RestconfSchemaSourceUrlProvider();
        final var result = urlProvider.getSchemaSourceUrl("ODL_modules", moduleName, revision);
        assertTrue(result.isPresent());
        assertEquals(Optional.of(expected), result);
    }

    private static Stream<Arguments> getSchemaSourceUrlArgs() {
        return Stream.of(
            Arguments.of("odl-module", Revision.of("2023-02-23"), new Uri(URL_PREFIX + "/odl-module/2023-02-23")),
            Arguments.of("module-no-revision", null, new Uri(URL_PREFIX + "/module-no-revision"))
        );
    }
}