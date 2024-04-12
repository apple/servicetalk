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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.BlockingGrpcClient;
import io.servicetalk.grpc.api.GrpcClient;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcClientFactory;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.grpc.api.GrpcFilters.newGrpcDeadlineClientFilterFactory;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_MAX_TIMEOUT;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientBuilder<U, R> implements GrpcClientBuilder<U, R> {
    @Nullable
    private Duration defaultTimeout;
    private boolean appendTimeoutFilter = true;
    private HttpInitializer<U, R> httpInitializer = builder -> {
        // no-op
    };

    private final Supplier<SingleAddressHttpClientBuilder<U, R>> httpClientBuilderSupplier;

    // Do not use this ctor directly, GrpcClients is the entry point for creating a new builder.
    DefaultGrpcClientBuilder(final Supplier<SingleAddressHttpClientBuilder<U, R>> httpClientBuilderSupplier) {
        this.httpClientBuilderSupplier = httpClientBuilderSupplier;
    }

    @Override
    public GrpcClientBuilder<U, R> initializeHttp(final GrpcClientBuilder.HttpInitializer<U, R> initializer) {
        httpInitializer = requireNonNull(initializer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = ensurePositive(defaultTimeout, "defaultTimeout");
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> defaultTimeout(@Nullable final Duration defaultTimeout,
                                                  final boolean appendTimeoutFilter) {
        this.defaultTimeout = defaultTimeout == null ? null : ensurePositive(defaultTimeout, "defaultTimeout");
        this.appendTimeoutFilter = appendTimeoutFilter;
        return this;
    }

    @Override
    public <Client extends GrpcClient<?>> Client build(GrpcClientFactory<Client, ?> clientFactory) {
        return clientFactory.newClientForCallFactory(newGrpcClientCallFactory());
    }

    @Override
    public <BlockingClient extends BlockingGrpcClient<?>> BlockingClient buildBlocking(
            GrpcClientFactory<?, BlockingClient> clientFactory) {
        return clientFactory.newBlockingClientForCallFactory(newGrpcClientCallFactory());
    }

    @Override
    public MultiClientBuilder buildMulti() {
        final GrpcClientCallFactory callFactory = newGrpcClientCallFactory();

        return new MultiClientBuilder() {
            @Override
            public <Client extends GrpcClient<?>> Client build(final GrpcClientFactory<Client, ?> clientFactory) {
                return clientFactory.newClientForCallFactory(callFactory);
            }

            @Override
            public <BlockingClient extends BlockingGrpcClient<?>> BlockingClient buildBlocking(
                    final GrpcClientFactory<?, BlockingClient> clientFactory) {
                return clientFactory.newBlockingClientForCallFactory(callFactory);
            }
        };
    }

    private GrpcClientCallFactory newGrpcClientCallFactory() {
        SingleAddressHttpClientBuilder<U, R> builder = httpClientBuilderSupplier.get()
            .protocols(h2Default());
        builder.appendClientFilter(CatchAllHttpClientFilter.INSTANCE);
        if (appendTimeoutFilter) {
            builder.appendClientFilter(newGrpcDeadlineClientFilterFactory());
        }
        // We append the GrpcRequestTracker filter before we let the `httpInitializer` see the builder because we
        // extract the RequestTracker on the way back, therefore filters in the front will be the last to do the
        // extraction, letting user override it if they like.
        builder.appendConnectionFactoryFilter(GrpcRequestTracker.filter());
        httpInitializer.initialize(builder);
        Duration timeout = isInfinite(defaultTimeout, GRPC_MAX_TIMEOUT) ? null : defaultTimeout;
        return GrpcClientCallFactory.from(builder.buildStreaming(), timeout);
    }

    static final class CatchAllHttpClientFilter implements StreamingHttpClientFilterFactory {

        static final StreamingHttpClientFilterFactory INSTANCE = new CatchAllHttpClientFilter();

        private CatchAllHttpClientFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    return CatchAllHttpClientFilter.request(delegate, request);
                }

                @Override
                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                        final HttpRequestMetaData metaData) {

                    return delegate().reserveConnection(metaData)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                    return CatchAllHttpClientFilter.request(delegate(), request);
                                }
                            });
                }
            };
        }

        private static Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                             final StreamingHttpRequest request) {
            final Single<StreamingHttpResponse> resp;
            try {
                resp = delegate.request(request);
            } catch (Throwable t) {
                return failed(toGrpcException(t));
            }
            return resp.onErrorMap(CatchAllHttpClientFilter::toGrpcException);
        }

        private static GrpcStatusException toGrpcException(Throwable cause) {
            return GrpcStatusException.fromThrowable(cause);
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // no influence since we do not block
            return HttpExecutionStrategies.offloadNone();
        }
    }
}
