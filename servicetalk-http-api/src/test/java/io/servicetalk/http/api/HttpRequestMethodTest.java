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

import org.junit.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class HttpRequestMethodTest {

    @Test
    public void testOfBufferReturnsConstants() {
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("GET")), sameInstance(GET));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("HEAD")), sameInstance(HEAD));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("POST")), sameInstance(POST));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("PUT")), sameInstance(PUT));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("DELETE")), sameInstance(DELETE));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("CONNECT")), sameInstance(CONNECT));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("OPTIONS")), sameInstance(OPTIONS));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("TRACE")), sameInstance(TRACE));
        assertThat(HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("PATCH")), sameInstance(PATCH));
    }

    @Test
    public void testOfStringReturnsConstants() {
        assertThat(HttpRequestMethod.of("GET"), sameInstance(GET));
        assertThat(HttpRequestMethod.of("HEAD"), sameInstance(HEAD));
        assertThat(HttpRequestMethod.of("POST"), sameInstance(POST));
        assertThat(HttpRequestMethod.of("PUT"), sameInstance(PUT));
        assertThat(HttpRequestMethod.of("DELETE"), sameInstance(DELETE));
        assertThat(HttpRequestMethod.of("CONNECT"), sameInstance(CONNECT));
        assertThat(HttpRequestMethod.of("OPTIONS"), sameInstance(OPTIONS));
        assertThat(HttpRequestMethod.of("TRACE"), sameInstance(TRACE));
        assertThat(HttpRequestMethod.of("PATCH"), sameInstance(PATCH));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfBufferThrowsExceptionForUnknownMethod() {
        HttpRequestMethod.of(DEFAULT_ALLOCATOR.fromAscii("UNKNOWN"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfStringThrowsExceptionForUnknownMethod() {
        HttpRequestMethod.of("UNKNOWN");
    }
}
