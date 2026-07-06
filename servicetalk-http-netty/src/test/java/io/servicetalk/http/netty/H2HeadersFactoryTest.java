/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.netty;

import org.junit.jupiter.api.Test;

import static java.lang.System.clearProperty;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class H2HeadersFactoryTest {

    @Test
    void defaultFactoryValidatesValues() {
        assertTrue(H2HeadersFactory.INSTANCE.validateValues());
        assertThrows(IllegalArgumentException.class,
                () -> H2HeadersFactory.INSTANCE.newHeaders().add("name", "invalid\0value"));
    }

    @Test
    void explicitFactoryCanDisableValueValidation() {
        assertDoesNotThrow(() -> new H2HeadersFactory(true, true, false).newHeaders()
                .add("name", "invalid\0value"));
    }

    @Test
    void temporaryDefaultValidateValuesPropertyCanDisableDefault() {
        final String oldValue = getProperty(H2HeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY);
        try {
            clearProperty(H2HeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY);
            assertTrue(H2HeadersFactory.defaultValidateValues());

            setProperty(H2HeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY, "false");
            assertFalse(H2HeadersFactory.defaultValidateValues());
        } finally {
            if (oldValue == null) {
                clearProperty(H2HeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY);
            } else {
                setProperty(H2HeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY, oldValue);
            }
        }
    }
}
