/*
 * Copyright © 2018, 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.BlockingStreamingHttpRequester;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.cached;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultMultiAddressUrlHttpClientBuilderTest {

    @RegisterExtension
    static final ExecutionContextExtension CTX = immediate().setClassLevel(true);

    @RegisterExtension
    static final ExecutionContextExtension INTERNAL_CLIENT_CTX = cached().setClassLevel(true);

    @Test
    void buildWithDefaults() throws Exception {
        StreamingHttpRequester newRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .buildStreaming();
        assertNotNull(newRequester);
        newRequester.closeAsync().toFuture().get();
    }

    @Test
    void buildAggregatedWithDefaults() throws Exception {
        HttpRequester newAggregatedRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .executionStrategy(defaultStrategy())
                .build();
        assertNotNull(newAggregatedRequester);
        newAggregatedRequester.closeAsync().toFuture().get();
    }

    @Test
    void buildBlockingWithDefaults() throws Exception {
        BlockingStreamingHttpRequester newBlockingRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .buildBlockingStreaming();
        assertNotNull(newBlockingRequester);
        newBlockingRequester.close();
    }

    @Test
    void buildBlockingAggregatedWithDefaults() throws Exception {
        BlockingHttpRequester newBlockingAggregatedRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .buildBlocking();
        assertNotNull(newBlockingAggregatedRequester);
        newBlockingAggregatedRequester.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWithProvidedServiceDiscoverer() throws Exception {
        ServiceDiscoverer<HostAndPort, InetSocketAddress,
                ServiceDiscovererEvent<InetSocketAddress>> mockedServiceDiscoverer = mock(ServiceDiscoverer.class);
        StreamingHttpRequester newRequester = HttpClients.forMultiAddressUrl(mockedServiceDiscoverer)
                .ioExecutor(CTX.ioExecutor())
                .buildStreaming();
        newRequester.closeAsync().toFuture().get();
        verify(mockedServiceDiscoverer, never()).closeAsync();
    }

    @Test
    void buildWithCustomHeadersFactory() throws Exception {
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl()
                .headersFactory(new DelegatingHttpHeadersFactory(DefaultHttpHeadersFactory.INSTANCE) {
                    @Override
                    public HttpHeaders newHeaders() {
                        return super.newHeaders().add("custom-header", "custom-value");
                    }
                })
                .buildBlocking()) {
            HttpRequest request = client.get("/");
            assertThat(request.headers().get("custom-header"), contentEqualTo("custom-value"));
        }
    }

    @Test
    void internalClientsInheritExecutionContext() throws Exception {
        HttpExecutionContext expectedCtx = new HttpExecutionContextBuilder()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .bufferAllocator(PREFER_DIRECT_ALLOCATOR)
                .executionStrategy(offloadNever())
                .build();
        AtomicReference<HttpExecutionContext> actualInternalCtx = new AtomicReference<>();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .executionStrategy(offloadNever())
                .listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()));
             BlockingHttpClient blockingHttpClient = HttpClients.forMultiAddressUrl()
                     .initializer((scheme, address, builder) ->
                             builder.appendClientFilter(client -> {
                                 actualInternalCtx.set(client.executionContext());
                                 return new StreamingHttpClientFilter(client) {
                                 };
                             }))
                     .ioExecutor(expectedCtx.ioExecutor())
                     .executor(expectedCtx.executor())
                     .bufferAllocator(expectedCtx.bufferAllocator())
                     .executionStrategy(expectedCtx.executionStrategy())
                     .buildBlocking()) {

            // Check external client
            assertExecutionContext(expectedCtx, blockingHttpClient.executionContext());
            // Make a request to trigger the filter execution that extracts the execution context.
            HttpResponse response = blockingHttpClient.request(
                    blockingHttpClient.get("http://" + serverHostAndPort(serverContext)));
            assertThat(response.status(), is(OK));
            // Check internal client
            assertExecutionContext(expectedCtx, actualInternalCtx.get());
        }
    }

    @Test
    void internalClientsUseDifferentExecutionContextWhenConfigured() throws Exception {
        HttpExecutionContext externalCtx = new HttpExecutionContextBuilder()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .bufferAllocator(PREFER_HEAP_ALLOCATOR)
                .executionStrategy(offloadAll())
                .build();

        HttpExecutionContext internalCtx = new HttpExecutionContextBuilder()
                .ioExecutor(INTERNAL_CLIENT_CTX.ioExecutor())
                .executor(INTERNAL_CLIENT_CTX.executor())
                .bufferAllocator(PREFER_DIRECT_ALLOCATOR)
                .executionStrategy(offloadNever())
                .build();

        AtomicReference<HttpExecutionContext> actualInternalCtx = new AtomicReference<>();
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .executionStrategy(offloadNever())
                .listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()));
             BlockingHttpClient blockingHttpClient = HttpClients.forMultiAddressUrl()
                     .initializer((scheme, address, builder) ->
                             builder.executionStrategy(internalCtx.executionStrategy())
                                     .executor(internalCtx.executor())
                                     .ioExecutor(internalCtx.ioExecutor())
                                     .bufferAllocator(internalCtx.bufferAllocator())
                                     .appendClientFilter(client -> {
                                         actualInternalCtx.set(client.executionContext());
                                         return new StreamingHttpClientFilter(client) {
                                         };
                                     }))
                     .executor(externalCtx.executor())
                     .ioExecutor(externalCtx.ioExecutor())
                     .executionStrategy(externalCtx.executionStrategy())
                     .bufferAllocator(externalCtx.bufferAllocator())
                     .buildBlocking()) {

            // Check external client
            assertExecutionContext(externalCtx, blockingHttpClient.executionContext());
            // Make a request to trigger the filter execution that extracts the execution context.
            HttpResponse response = blockingHttpClient.request(
                    blockingHttpClient.get("http://" + serverHostAndPort(serverContext)));
            assertThat(response.status(), is(OK));
            // Check internal client
            assertExecutionContext(internalCtx, actualInternalCtx.get());
        }
    }

    private static void assertExecutionContext(HttpExecutionContext expected, HttpExecutionContext actual) {
        assertThat(actual, is(notNullValue()));
        assertThat(actual.ioExecutor(), equalTo(expected.ioExecutor()));
        assertThat(actual.executor(), equalTo(expected.executor()));
        assertThat(actual.bufferAllocator(), equalTo(expected.bufferAllocator()));
        assertThat(actual.executionStrategy(), equalTo(expected.executionStrategy()));
    }

    private static class DelegatingHttpHeadersFactory implements HttpHeadersFactory {
        private final HttpHeadersFactory delegate;

        DelegatingHttpHeadersFactory(final HttpHeadersFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpHeaders newHeaders() {
            return delegate.newHeaders();
        }

        @Override
        public HttpHeaders newTrailers() {
            return delegate.newTrailers();
        }

        @Override
        public boolean validateCookies() {
            return delegate.validateCookies();
        }

        @Override
        public boolean validateValues() {
            return delegate.validateValues();
        }
    }
}
