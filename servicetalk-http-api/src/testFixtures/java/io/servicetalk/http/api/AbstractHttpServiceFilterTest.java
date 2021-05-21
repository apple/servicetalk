/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequestHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import javax.net.ssl.SSLSession;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * This parameterized test facilitates running HTTP service filter tests under all calling variations:
 * with and without SSL context.
 */
@ExtendWith(MockitoExtension.class)
public abstract class AbstractHttpServiceFilterTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    public enum SecurityType { Secure, Insecure }

    @Mock
    private HttpExecutionContext executionContext;

    @Mock
    private HttpServiceContext mockConnectionContext;

    @BeforeEach
    void setUp() {
        lenient().when(mockConnectionContext.executionContext()).thenReturn(executionContext);
        lenient().when(mockConnectionContext.remoteAddress()).thenAnswer(__ -> remoteAddress());
        lenient().when(mockConnectionContext.localAddress()).thenAnswer(__ -> localAddress());
    }

    protected void setUp(SecurityType security) {
        lenient().when(mockConnectionContext.sslSession()).thenAnswer(__ -> {
            switch (security) {
                case Secure:
                    return sslSession();
                case Insecure:
                default:
                    return null;
            }
        });
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    protected InetSocketAddress remoteAddress() {
        return InetSocketAddress.createUnresolved("127.0.1.2", 28080);
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    protected InetSocketAddress localAddress() {
        return InetSocketAddress.createUnresolved("127.0.1.1", 80);
    }

    protected SSLSession sslSession() {
        return mock(SSLSession.class);
    }

    protected final StreamingHttpRequester createFilter(final RequestHandler handler,
                                                        final StreamingHttpServiceFilterFactory factory) {

        StreamingHttpServiceFilter service = factory.create(
            (ctx, request, responseFactory) -> handler.request(responseFactory, request));

        return new StreamingHttpRequester() {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {

                return service.handle(mockConnectionContext, request, REQ_RES_FACTORY);
            }

            @Override
            public HttpExecutionContext executionContext() {
                return executionContext;
            }

            @Override
            public StreamingHttpResponseFactory httpResponseFactory() {
                return REQ_RES_FACTORY;
            }

            @Override
            public Completable onClose() {
                return Completable.completed();
            }

            @Override
            public Completable closeAsync() {
                return Completable.completed();
            }

            @Override
            public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                return REQ_RES_FACTORY.newRequest(method, requestTarget);
            }
        };
    }
}
