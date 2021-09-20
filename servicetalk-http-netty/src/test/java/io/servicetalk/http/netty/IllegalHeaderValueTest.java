/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IllegalHeaderValueTest extends AbstractNettyHttpServerTest {

    private static final CharSequence HEADER_NAME = newAsciiString("header-name");
    private static final CharSequence X_HEADER_NAME = newAsciiString("x-header-name");
    private static final CharSequence ILLEGAL_HEADER_VALUE = newAsciiString("header-value\r\n");
    private static final CharSequence OK_HEADER_VALUE = newAsciiString("!ok\t value~");

    @Override
    void service(final StreamingHttpService ignore) {
        super.service((ctx, request, responseFactory) -> {
            StreamingHttpResponse ok = responseFactory.ok();
            request.headers().forEach(entry -> {
                if (contentEqualsIgnoreCase(HOST, entry.getKey())) {
                    return;
                }
                ok.addHeader(entry.getKey(), entry.getValue());
            });
            return succeeded(ok);
        });
    }

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void noHeaderValueValidation(HttpProtocol protocol) throws Exception {
        protocol(config(protocol, false));
        setUp(CACHED, CACHED_SERVER);

        StreamingHttpConnection connection = streamingHttpConnection();
        StreamingHttpResponse response = requestWithInvalidHeader(connection);
        assertResponse(response, protocol.version, OK, 0);

        if (protocol == HTTP_1) {
            // HTTP/1.1 will try parsing the X_HEADER_NAME as a new request and fail, closing the connection:
            ExecutionException e = assertThrows(ExecutionException.class, () -> requestWithInvalidHeader(connection));
            assertThat(e.getCause(), instanceOf(IOException.class));
        } else {
            // HTTP/2 uses binary encoding and can transfer illegal header values with not problem:
            StreamingHttpResponse secondResponse = requestWithInvalidHeader(connection);
            assertResponse(secondResponse, protocol.version, OK, 0);
        }
    }

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void withHeaderValueValidation(HttpProtocol protocol) throws Exception {
        protocol(config(protocol, true));
        setUp(CACHED, CACHED_SERVER);

        assertThrows(IllegalArgumentException.class, () -> streamingHttpConnection().get("/")
                .addHeader(HEADER_NAME, ILLEGAL_HEADER_VALUE));
    }

    private static HttpProtocolConfig config(HttpProtocol protocol, boolean validateHeaderValues) {
        switch (protocol) {
            case HTTP_1:
                return h1().headersFactory(new DefaultHttpHeadersFactory(true, true, validateHeaderValues)).build();
            case HTTP_2:
                return h2().headersFactory(new H2HeadersFactory(true, true, validateHeaderValues))
                        .enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true)
                        .build();
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
    }

    private StreamingHttpResponse requestWithInvalidHeader(StreamingHttpConnection connection) throws Exception {
        return makeRequest(connection.get("/")
                // enable wire-logging and make that the header with ILLEGAL_HEADER_VALUE is *not* the last one:
                .addHeader(HEADER_NAME, ILLEGAL_HEADER_VALUE)
                .addHeader(X_HEADER_NAME, OK_HEADER_VALUE));
    }
}
