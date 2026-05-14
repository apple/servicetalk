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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.HttpKeepAlive.CLOSE_ADD_HEADER;
import static io.servicetalk.http.netty.HttpKeepAlive.CLOSE_NO_HEADER;
import static io.servicetalk.http.netty.HttpKeepAlive.KEEP_ALIVE_ADD_HEADER;
import static io.servicetalk.http.netty.HttpKeepAlive.KEEP_ALIVE_NO_HEADER;
import static io.servicetalk.http.netty.HttpKeepAlive.responseKeepAlive;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpKeepAliveTest {

    private static final DefaultHttpHeadersFactory HEADERS_FACTORY = new DefaultHttpHeadersFactory(false, false, false);

    @Test
    void http11NoConnectionHeaderIsKeepAlive() {
        assertThat(responseKeepAlive(metaData(HTTP_1_1)), is(KEEP_ALIVE_NO_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"close", "close, Upgrade", "Upgrade, close"})
    void http11ConnectionWithCloseIsClose(String connectionValue) {
        assertThat(responseKeepAlive(metaData(HTTP_1_1, connectionValue)), is(CLOSE_ADD_HEADER));
    }

    @Test
    void http11MultipleConnectionHeadersWithCloseIsClose() {
        HttpMetaData meta = metaData(HTTP_1_1);
        meta.headers().add(CONNECTION, "Upgrade");
        meta.headers().add(CONNECTION, "close");
        assertThat(responseKeepAlive(meta), is(CLOSE_ADD_HEADER));
    }

    @Test
    void http11ConnectionWithoutCloseIsKeepAlive() {
        assertThat(responseKeepAlive(metaData(HTTP_1_1, "Upgrade")), is(KEEP_ALIVE_NO_HEADER));
    }

    @Test
    void http10NoConnectionHeaderIsClose() {
        assertThat(responseKeepAlive(metaData(HTTP_1_0)), is(CLOSE_NO_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"close", "close, keep-alive", "keep-alive, close"})
    void http10ConnectionWithCloseIsClose(String connectionValue) {
        assertThat(responseKeepAlive(metaData(HTTP_1_0, connectionValue)), is(CLOSE_ADD_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"keep-alive", "keep-alive, Upgrade", "Upgrade, keep-alive"})
    void http10ConnectionWithKeepAliveIsKeepAlive(String connectionValue) {
        assertThat(responseKeepAlive(metaData(HTTP_1_0, connectionValue)), is(KEEP_ALIVE_ADD_HEADER));
    }

    @Test
    void http2NoConnectionHeaderIsKeepAliveNoHeader() {
        assertThat(responseKeepAlive(metaData(HTTP_2_0)), is(KEEP_ALIVE_NO_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"close", "keep-alive", "close, Upgrade", "keep-alive, Upgrade"})
    void http2IgnoresConnectionHeader(String connectionValue) {
        assertThat(responseKeepAlive(metaData(HTTP_2_0, connectionValue)), is(KEEP_ALIVE_NO_HEADER));
    }

    private static StreamingHttpResponse metaData(final HttpProtocolVersion version) {
        return newResponse(OK, version, HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR, HEADERS_FACTORY);
    }

    private static StreamingHttpResponse metaData(final HttpProtocolVersion version, final String connectionValue) {
        StreamingHttpResponse response = metaData(version);
        response.headers().add(CONNECTION, connectionValue);
        return response;
    }
}
