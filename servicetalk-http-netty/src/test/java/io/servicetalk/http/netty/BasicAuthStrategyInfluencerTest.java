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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.CredentialsVerifier;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.NettyIoExecutors;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Base64.getEncoder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicAuthStrategyInfluencerTest {
    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";

    @Nullable
    private OffloadCheckingService service;
    @Nullable
    private IoExecutor ioExecutor;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private BlockingHttpClient client;
    @Mock(lenient = true)
    private CredentialsVerifier<String> credentialsVerifier;

    @AfterEach
    void tearDown() throws Exception {
        CompositeCloseable closeable = newCompositeCloseable();
        if (client != null) {
            client.close();
        }
        if (serverContext != null) {
            closeable.append(serverContext);
        }
        if (ioExecutor != null) {
            closeable.append(ioExecutor);
        }
        closeable.close();
    }

    @Test
    void defaultOffloads() throws Exception {
        BlockingHttpClient client = setup(false);
        assert service != null;
        HttpResponse response = makeRequest(client);
        assertThat("Unexpected response.", response.status().code(), is(200));
        service.assertHandleOffload(not(startsWith(IO_EXECUTOR_NAME_PREFIX)));
        service.assertRequestOffload(not(startsWith(IO_EXECUTOR_NAME_PREFIX)));
        service.assertResponseOffload(not(startsWith(IO_EXECUTOR_NAME_PREFIX)));
    }

    @Test
    void noOffloadsInfluence() throws Exception {
        BlockingHttpClient client = setup(true);
        assert service != null;
        HttpResponse response = makeRequest(client);
        assertThat("Unexpected response.", response.status().code(), is(200));
        service.assertHandleOffload(startsWith(IO_EXECUTOR_NAME_PREFIX));
        service.assertRequestOffload(startsWith(IO_EXECUTOR_NAME_PREFIX));
        service.assertResponseOffload(startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    private HttpResponse makeRequest(final BlockingHttpClient client) throws Exception {
        return client.request(client.get("/").addHeader(AUTHORIZATION, "Basic " +
                getEncoder().encodeToString("foo:bar".getBytes(ISO_8859_1))));
    }

    private BlockingHttpClient setup(boolean noOffloadsInfluence) throws Exception {
        ioExecutor = NettyIoExecutors.createIoExecutor(IO_EXECUTOR_NAME_PREFIX);
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
        when(credentialsVerifier.apply(anyString(), anyString())).thenReturn(succeeded("success"));
        when(credentialsVerifier.closeAsync()).thenReturn(completed());
        when(credentialsVerifier.closeAsyncGracefully()).thenReturn(completed());
        CredentialsVerifier<String> verifier = credentialsVerifier;
        if (noOffloadsInfluence) {
            verifier = new InfluencingVerifier(verifier, offloadNone());
            serverBuilder.executionStrategy(offloadNone());
        }
        serverBuilder.appendServiceFilter(new BasicAuthHttpServiceFilter.Builder<>(verifier, "dummy")
                .buildServer());
        serverBuilder.ioExecutor(ioExecutor);
        service = new OffloadCheckingService();
        serverContext = serverBuilder.listenStreamingAndAwait(service);

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(serverContext));
        this.client = clientBuilder.buildBlocking();
        return this.client;
    }

    private static final class OffloadCheckingService implements StreamingHttpService,
                                                                 ExecutionStrategyInfluencer<HttpExecutionStrategy> {

        private enum OffloadPoint {
            ServiceHandle,
            RequestPayload,
            Response
        }

        private final ConcurrentMap<OffloadPoint, Thread> invoker = new ConcurrentHashMap<>();

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            invoker.put(OffloadPoint.ServiceHandle, Thread.currentThread());
            return new Single<StreamingHttpResponse>() {
                @Override
                protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                    invoker.put(OffloadPoint.Response, Thread.currentThread());
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onSuccess(responseFactory.ok()
                            .payloadBody(request.payloadBody()
                                    .whenOnSubscribe(__ ->
                                            invoker.put(OffloadPoint.RequestPayload, Thread.currentThread()))));
                }
            };
        }

        void assertHandleOffload(Matcher<String> threadNameMatcher) {
            assertThat("Unexpected thread invoked Service#handle",
                    invoker.get(OffloadPoint.ServiceHandle).getName(), threadNameMatcher);
        }

        void assertRequestOffload(Matcher<String> threadNameMatcher) {
            assertThat("Unexpected thread invoked request payload",
                    invoker.get(OffloadPoint.RequestPayload).getName(), threadNameMatcher);
        }

        void assertResponseOffload(Matcher<String> threadNameMatcher) {
            assertThat("Unexpected thread invoked response",
                    invoker.get(OffloadPoint.Response).getName(), threadNameMatcher);
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // No influence since we do not block.
            return offloadNone();
        }
    }

    private static final class InfluencingVerifier implements CredentialsVerifier<String> {

        private final CredentialsVerifier<String> delegate;
        private final HttpExecutionStrategy requiredOffloads;

        InfluencingVerifier(final CredentialsVerifier<String> delegate,
                            final HttpExecutionStrategy requiredOffloads) {
            this.delegate = delegate;
            this.requiredOffloads = requiredOffloads;
        }

        @Override
        public Single<String> apply(final String userId, final String password) {
            return delegate.apply(userId, password);
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return requiredOffloads;
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }
    }
}
