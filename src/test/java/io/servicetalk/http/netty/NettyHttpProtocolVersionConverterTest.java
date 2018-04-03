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

import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpProtocolVersions;

import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.fromNettyHttpVersion;
import static io.servicetalk.http.netty.NettyHttpProtocolVersionConverter.toNettyHttpVersion;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertSame;

public class NettyHttpProtocolVersionConverterTest {
    @Test
    public void testFromNettyStandardProtocolVersion() {
        assertSame(HTTP_1_0, fromNettyHttpVersion(HttpVersion.HTTP_1_0));
        assertSame(HTTP_1_1, fromNettyHttpVersion(HttpVersion.HTTP_1_1));
    }

    @Test
    public void testFromNettyStandardProtocolVersionDifferentInstance() {
        final HttpProtocolVersion protocolVersion = fromNettyHttpVersion(new HttpVersion("HTTP", 1, 1, true));

        assertEquals(HTTP_1_1, protocolVersion);
    }

    @Test
    public void testFromNettyUnknownProtocolVersion() {
        final HttpProtocolVersion protocolVersion = fromNettyHttpVersion(new HttpVersion("HTTP", 1, 2, true));

        assertEquals(HttpProtocolVersions.getProtocolVersion(1, 2), protocolVersion);
    }

    @Test
    public void testToNettyStandardProtocolVersion() {
        assertSame(HttpVersion.HTTP_1_0, toNettyHttpVersion(HTTP_1_0));
        assertSame(HttpVersion.HTTP_1_1, toNettyHttpVersion(HTTP_1_1));
    }

    @Test
    public void testToNettyStandardProtocolVersionDifferentInstance() {
        final HttpVersion protocolVersion = toNettyHttpVersion(HttpProtocolVersions.getProtocolVersion(1, 1));

        assertEquals(HttpVersion.HTTP_1_1, protocolVersion);
    }

    @Test
    public void testToNettyUnknownProtocolVersion() {
        final HttpVersion protocolVersion = toNettyHttpVersion(HttpProtocolVersions.getProtocolVersion(1, 2));

        assertEquals(new HttpVersion("HTTP", 1, 2, true), protocolVersion);
    }
}
