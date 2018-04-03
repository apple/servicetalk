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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpRequestMethods;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;

import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.NONE;
import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.fromNettyHttpMethod;
import static io.servicetalk.http.netty.NettyHttpRequestMethodConverter.toNettyHttpMethod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class NettyHttpRequestMethodConverterTest {

    @Test
    public void testFromNettyStandardMethod() {
        final HttpRequestMethod method = fromNettyHttpMethod(HttpMethod.GET);

        assertSame(GET, method);
    }

    @Test
    public void testFromNettyStandardMethodDifferentInstance() {
        final HttpRequestMethod method = fromNettyHttpMethod(new HttpMethod("GET"));

        assertSame(GET, method);
    }

    @Test
    public void testFromNettyUnknownMethod() {
        final HttpRequestMethod method = fromNettyHttpMethod(HttpMethod.valueOf("COPY"));

        assertEquals("COPY", method.getName());
        assertEquals(NONE, method.getMethodProperties());
    }

    @Test
    public void testToNettyStandardMethod() {
        final HttpMethod method = toNettyHttpMethod(GET);

        assertSame(HttpMethod.GET, method);
    }

    @Test
    public void testToNettyStandardMethodDifferentInstance() {
        final HttpMethod method = toNettyHttpMethod(HttpRequestMethods.getRequestMethod("GET"));

        assertSame(HttpMethod.GET, method);
    }

    @Test
    public void testToNettyUnknownMethod() {
        final HttpMethod method = toNettyHttpMethod(
                HttpRequestMethods.getRequestMethod("COPY"));

        assertEquals("COPY", method.name());
    }
}
