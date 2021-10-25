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
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.internal.DeadlineUtils;
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
import io.servicetalk.http.utils.TimeoutFromRequest;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;

import java.time.Duration;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.grpc.api.GrpcStatus.fromThrowable;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_MAX_TIMEOUT;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.utils.TimeoutFromRequest.toTimeoutFromRequest;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientBuilder<U, R> extends GrpcClientBuilder<U, R> {

    /**
     * A function which determines the timeout for a given request.
     */
    private static final TimeoutFromRequest GRPC_TIMEOUT_REQHDR =
            toTimeoutFromRequest(DeadlineUtils::readTimeoutHeader, HttpExecutionStrategies.anyStrategy());

    @Nullable
    private Duration defaultTimeout;
    private HttpInitializer<U, R> httpInitializer = builder -> {
        // no-op
    };

    private final Supplier<SingleAddressHttpClientBuilder<U, R>> httpClientBuilderSupplier;

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
    protected GrpcClientCallFactory newGrpcClientCallFactory() {
        SingleAddressHttpClientBuilder<U, R> builder = httpClientBuilderSupplier.get().protocols(h2Default());
        builder.appendClientFilter(CatchAllHttpClientFilter.INSTANCE);
        httpInitializer.initialize(builder);
        builder.appendClientFilter(new TimeoutHttpRequesterFilter(GRPC_TIMEOUT_REQHDR, true));
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
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return CatchAllHttpClientFilter.request(delegate, strategy, request);
                }

                @Override
                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                        final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {

                    return delegate().reserveConnection(strategy, metaData)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                                final HttpExecutionStrategy strategy,
                                                                                final StreamingHttpRequest request) {
                                    return CatchAllHttpClientFilter.request(delegate, strategy, request);
                                }
                            });
                }
            };
        }

        private static Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                             final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
            final Single<StreamingHttpResponse> resp;
            try {
                resp = delegate.request(strategy, request);
            } catch (Throwable t) {
                return failed(toGrpcException(t));
            }
            return resp.onErrorMap(CatchAllHttpClientFilter::toGrpcException);
        }

        private static GrpcStatusException toGrpcException(Throwable cause) {
            return fromThrowable(cause).asException();
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // no influence since we do not block
            return HttpExecutionStrategies.anyStrategy();
        }
    }
}
