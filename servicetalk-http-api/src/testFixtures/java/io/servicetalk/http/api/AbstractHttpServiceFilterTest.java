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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.AbstractHttpRequesterFilterTest.RequestHandler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.InetSocketAddress;
import java.util.Collection;
import javax.net.ssl.SSLSession;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static io.servicetalk.http.api.AbstractHttpServiceFilterTest.SecurityType.Insecure;
import static io.servicetalk.http.api.AbstractHttpServiceFilterTest.SecurityType.Secure;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This parameterized test facilitates running HTTP service filter tests under all calling variations:
 * with and without SSL context.
 */
@RunWith(Parameterized.class)
public abstract class AbstractHttpServiceFilterTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    public enum SecurityType { Secure, Insecure }

    public final SecurityType security;

    @Mock
    private HttpExecutionContext executionContext;

    @Mock
    private HttpServiceContext mockConnectionContext;

    public AbstractHttpServiceFilterTest(final SecurityType security) {
        this.security = security;
    }

    @SuppressWarnings("unused")
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> securityTypes() {
        return asList(new Object[][]{
                {Secure},
                {Insecure},
        });
    }

    @Before
    public void setUp() {
        when(mockConnectionContext.executionContext()).thenReturn(executionContext);
        when(mockConnectionContext.remoteAddress()).thenAnswer(__ -> remoteAddress());
        when(mockConnectionContext.localAddress()).thenAnswer(__ -> localAddress());
        when(mockConnectionContext.sslSession()).thenAnswer(__ -> {
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

        StreamingHttpServiceFilter service = factory.create(new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return handler.request(responseFactory, request);
            }
        });

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
            public void close() throws Exception {
                awaitTermination(closeAsyncGracefully().toFuture());
            }

            @Override
            public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                return REQ_RES_FACTORY.newRequest(method, requestTarget);
            }
        };
    }
}
