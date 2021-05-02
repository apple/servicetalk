/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.lang.System.lineSeparator;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractHttpResponseMetaDataTest<T extends HttpResponseMetaData> {

    protected T fixture;

    protected abstract void createFixture();

    @Test
    void testToString() {
        createFixture();
        fixture.headers().set(CONTENT_TYPE, "text/json");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("HTTP/1.1 200 OK", fixture.toString());
    }

    @Test
    void testToStringWithPassFilter() {
        createFixture();
        fixture.headers().set(CONTENT_TYPE, "text/json");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("HTTP/1.1 200 OK" + lineSeparator() +
                "DefaultHttpHeaders[authorization: some auth info" + lineSeparator() +
                "content-type: text/json]", fixture.toString((k, v) -> v));
    }

    @Test
    void testToStringWithRedactFilter() {
        createFixture();
        fixture.headers().set(CONTENT_TYPE, "text/json");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("HTTP/1.1 200 OK" + lineSeparator() +
                "DefaultHttpHeaders[authorization: redacted" + lineSeparator() +
                "content-type: redacted]", fixture.toString((k, v) -> "redacted"));
    }
}
