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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static org.junit.Assert.assertEquals;

public abstract class AbstractHttpResponseMetaDataTest<T extends HttpResponseMetaData> {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    protected T fixture;

    protected abstract void createFixture();

    @Test
    public void testToString() {
        createFixture();
        fixture.headers().set(CONTENT_TYPE, "text/json");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("HTTP/1.1 200 OK", fixture.toString());
    }

    @Test
    public void testToStringWithPassFilter() {
        createFixture();
        fixture.headers().set(CONTENT_TYPE, "text/json");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("HTTP/1.1 200 OK\n" +
                "DefaultHttpHeaders[authorization: some auth info\n" +
                "content-type: text/json]", fixture.toString((k, v) -> v));
    }

    @Test
    public void testToStringWithRedactFilter() {
        createFixture();
        fixture.headers().set(CONTENT_TYPE, "text/json");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("HTTP/1.1 200 OK\n" +
                "DefaultHttpHeaders[authorization: redacted\n" +
                "content-type: redacted]", fixture.toString((k, v) -> "redacted"));
    }
}
