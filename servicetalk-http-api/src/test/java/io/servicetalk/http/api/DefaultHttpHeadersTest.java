/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultHttpHeadersTest extends AbstractHttpHeadersTest {

    @Test
    void addHeadersValidatesNamesWhenSourceDoesNot() {
        DefaultHttpHeaders source = newHeaders(false, false);
        source.add("invalid name", "value");

        DefaultHttpHeaders dest = newHeaders(true, false);
        assertThrows(IllegalArgumentException.class, () -> dest.add(source));
    }

    @Test
    void addHeadersValidatesValuesWhenSourceDoesNot() {
        DefaultHttpHeaders source = newHeaders(false, false);
        source.add("name", "invalid\0value");

        DefaultHttpHeaders dest = newHeaders(false, true);
        assertThrows(IllegalArgumentException.class, () -> dest.add(source));
    }

    @Test
    void addHeadersSkipsNameValidationWhenSourceAlreadyValidates() {
        // Load an invalid name into a source that claims to validate names.
        DefaultHttpHeaders raw = newHeaders(false, false);
        raw.add("invalid name", "value");
        DefaultHttpHeaders source = newHeaders(true, false);
        source.putAll(raw, false, false);

        DefaultHttpHeaders dest = newHeaders(true, false);
        assertDoesNotThrow(() -> dest.add(source));
    }

    @Test
    void addHeadersSkipsValueValidationWhenSourceAlreadyValidates() {
        // Load an invalid value into a source that claims to validate values.
        DefaultHttpHeaders raw = newHeaders(false, false);
        raw.add("name", "invalid\0value");
        DefaultHttpHeaders source = newHeaders(false, true);
        source.putAll(raw, false, false);

        DefaultHttpHeaders dest = newHeaders(false, true);
        assertDoesNotThrow(() -> dest.add(source));
    }

    private static DefaultHttpHeaders newHeaders(boolean validateNames, boolean validateValues) {
        return new DefaultHttpHeaders(16, validateNames, false, validateValues);
    }

    @Override
    protected HttpHeaders newHeaders() {
        return DefaultHttpHeadersFactory.INSTANCE.newHeaders();
    }

    @Override
    protected HttpHeaders newHeaders(final int initialSizeHint) {
        return new DefaultHttpHeaders(initialSizeHint, true, true, true);
    }
}
