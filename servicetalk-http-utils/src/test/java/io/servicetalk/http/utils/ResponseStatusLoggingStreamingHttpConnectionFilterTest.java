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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static org.mockito.Mockito.when;

public class ResponseStatusLoggingStreamingHttpConnectionFilterTest {
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private ConnectionContext connectionContext;

    @Mock
    private StreamingHttpRequest mockRequest;

    private StreamingHttpConnection testConnection;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        InetSocketAddress localAddress = new InetSocketAddress(0);
        InetSocketAddress remoteAddress = new InetSocketAddress(0);
        when(connectionContext.localAddress()).thenReturn(localAddress);
        when(connectionContext.remoteAddress()).thenReturn(remoteAddress);
        testConnection =
                new TestStreamingHttpConnection(reqRespFactory, executionContext, connectionContext) {
                    @Override
                    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                        return Single.success(httpResponseFactory().ok());
                    }
                };
    }

    @Test
    public void successfulRequestResponse() throws ExecutionException, InterruptedException {
        new ResponseStatusLoggingStreamingHttpConnectionFilter("testClient", testConnection)
                .request(mockRequest)
                .toFuture().get().payloadBody().ignoreElements().toFuture().get();
        // we are not asserting that the log methods are actually called, but instead just verified code paths
        // execute "normally".
    }
}
