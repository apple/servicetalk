/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadBalancerUnhealthyTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void doesNotRetryIndefinitely(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     // Intentionally set maxTotalRetries to MAX_VALUE
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder().maxTotalRetries(MAX_VALUE).build())
                     .appendConnectionFactoryFilter(factory -> new DelegatingConnectionFactory<InetSocketAddress,
                             FilterableStreamingHttpConnection>(factory) {
                         @Override
                         public Single<FilterableStreamingHttpConnection> newConnection(
                                 InetSocketAddress address, @Nullable ContextMap context,
                                 @Nullable TransportObserver observer) {
                             return Single.failed(DELIBERATE_EXCEPTION);
                         }
                     })
                     .buildBlocking()) {
            // Turn LoadBalancer into "unhealthy" state:
            for (int i = 0; i < 5; i++) {
                assertThrows(DeliberateException.class, () -> client.request(client.get("/")));
            }
            assertThrows(NoActiveHostException.class, () -> client.request(client.get("/")));
        }
    }
}
