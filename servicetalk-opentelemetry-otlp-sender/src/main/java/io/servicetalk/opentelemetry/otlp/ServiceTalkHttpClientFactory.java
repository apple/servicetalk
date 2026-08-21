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
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ProxyConfigBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.HttpResponseException;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import io.opentelemetry.sdk.common.export.ProxyOptions;
import io.opentelemetry.sdk.common.export.RetryPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

final class ServiceTalkHttpClientFactory {

    // Retryable gRPC status codes per the OTLP spec.
    private static final Set<Integer> RETRYABLE_GRPC_STATUSES;
    // Retryable HTTP status codes per the OTLP spec.
    private static final Set<Integer> RETRYABLE_HTTP_STATUSES;
    static {
        Set<Integer> grpc = new HashSet<>();
        grpc.add(GrpcStatusCode.CANCELLED.value());
        grpc.add(GrpcStatusCode.DEADLINE_EXCEEDED.value());
        grpc.add(GrpcStatusCode.RESOURCE_EXHAUSTED.value());
        grpc.add(GrpcStatusCode.ABORTED.value());
        grpc.add(GrpcStatusCode.OUT_OF_RANGE.value());
        grpc.add(GrpcStatusCode.UNAVAILABLE.value());
        grpc.add(GrpcStatusCode.DATA_LOSS.value());
        RETRYABLE_GRPC_STATUSES = Collections.unmodifiableSet(grpc);

        Set<Integer> http = new HashSet<>();
        http.add(HttpResponseStatus.TOO_MANY_REQUESTS.code());
        http.add(HttpResponseStatus.BAD_GATEWAY.code());
        http.add(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
        http.add(HttpResponseStatus.GATEWAY_TIMEOUT.code());
        RETRYABLE_HTTP_STATUSES = Collections.unmodifiableSet(http);
    }

    // Maps a gRPC response with a retryable {@code grpc-status} header to an
    // {@link HttpResponseException} so the surrounding retry filter triggers. Only the response headers are
    // inspected (a trailers-only response), which is how a server signals a unary-call failure in practice.
    private static final Function<HttpResponseMetaData, HttpResponseException> GRPC_RETRYABLE_RESPONSE_MAPPER =
            metaData -> {
                CharSequence status = metaData.headers().get("grpc-status");
                if (status == null) {
                    return null;
                }
                final int code;
                try {
                    // parseLong avoids a String allocation when status is an AsciiString.
                    code = (int) CharSequences.parseLong(status);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (RETRYABLE_GRPC_STATUSES.contains(code)) {
                    return new HttpResponseException("retryable grpc-status: " + status, metaData);
                }
                return null;
            };

    /** Maps an HTTP response with a retryable status code to an {@link HttpResponseException}. */
    private static final Function<HttpResponseMetaData, HttpResponseException> HTTP_RETRYABLE_RESPONSE_MAPPER =
            metaData -> {
                if (RETRYABLE_HTTP_STATUSES.contains(metaData.status().code())) {
                    return new HttpResponseException("retryable HTTP status: " + metaData.status(), metaData);
                }
                return null;
            };

    private ServiceTalkHttpClientFactory() {
    }

    static StreamingHttpClient buildGrpcClient(
            URI endpoint,
            @Nullable Duration timeout,
            @Nullable Duration connectTimeout,
            @Nullable SSLContext sslContext,
            @Nullable RetryPolicy retryPolicy,
            @Nullable Supplier<Map<String, List<String>>> headersSupplier) {

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = createBaseBuilder(endpoint);

        builder.protocols(HttpProtocolConfigs.h2Default());
        applyConnectTimeout(builder, connectTimeout);
        applySslConfiguration(builder, endpoint, sslContext);
        // Timeout is the per-export budget, so it wraps the retry filter.
        applyRequestTimeout(builder, timeout);
        // Append above the retry filter so the supplier is evaluated once per export and its headers are
        // added once: the retry filter re-subscribes to the same request instance beneath this filter.
        if (headersSupplier != null) {
            builder.appendClientFilter(new HeadersSupplierFilterFactory(headersSupplier));
        }
        applyRetryPolicy(builder, retryPolicy, GRPC_RETRYABLE_RESPONSE_MAPPER);

        return builder.buildStreaming();
    }

    static HttpClient buildHttpClient(
            URI endpoint,
            @Nullable Duration timeout,
            @Nullable Duration connectTimeout,
            @Nullable SSLContext sslContext,
            @Nullable ProxyOptions proxyOptions,
            @Nullable RetryPolicy retryPolicy,
            @Nullable Supplier<Map<String, List<String>>> headersSupplier) {

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = createBaseBuilder(endpoint);

        builder.protocols(HttpProtocolConfigs.h1Default());
        applyConnectTimeout(builder, connectTimeout);
        applySslConfiguration(builder, endpoint, sslContext);
        applyProxyConfiguration(builder, endpoint, proxyOptions);
        applyRequestTimeout(builder, timeout);
        // Append above the retry filter so the supplier is evaluated once per export and its headers are
        // added once: the retry filter re-subscribes to the same request instance beneath this filter.
        if (headersSupplier != null) {
            builder.appendClientFilter(new HeadersSupplierFilterFactory(headersSupplier));
        }
        applyRetryPolicy(builder, retryPolicy, HTTP_RETRYABLE_RESPONSE_MAPPER);

        return builder.build();
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> createBaseBuilder(URI endpoint) {
        String host = endpoint.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Endpoint must have a host: " + endpoint);
        }

        int port = endpoint.getPort();
        if (port <= 0) {
            String scheme = endpoint.getScheme();
            if ("https".equalsIgnoreCase(scheme) || "grpcs".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = 80;
            }
        }

        return HttpClients.forSingleAddress(host, port);
    }

    private static void applyConnectTimeout(
            SingleAddressHttpClientBuilder<?, ?> builder,
            @Nullable Duration connectTimeout) {

        if (connectTimeout == null) {
            return;
        }
        builder.socketOption(ServiceTalkSocketOptions.CONNECT_TIMEOUT, (int) connectTimeout.toMillis());
    }

    private static void applyRequestTimeout(
            SingleAddressHttpClientBuilder<?, ?> builder,
            @Nullable Duration timeout) {

        if (timeout == null) {
            return;
        }
        builder.appendClientFilter(new TimeoutHttpRequesterFilter(timeout, true));
    }

    private static void applySslConfiguration(
            SingleAddressHttpClientBuilder<?, ?> builder,
            URI endpoint,
            @Nullable SSLContext sslContext) {

        String scheme = endpoint.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"grpcs".equalsIgnoreCase(scheme)) {
            return;
        }

        try {
            ClientSslConfigBuilder sslConfigBuilder = sslContext != null ?
                    new ClientSslConfigBuilder(sslContext)
                    : new ClientSslConfigBuilder();

            String host = endpoint.getHost();
            if (host != null && !host.isEmpty()) {
                sslConfigBuilder.sniHostname(host);
                sslConfigBuilder.peerHost(host);

                int port = endpoint.getPort();
                if (port > 0) {
                    sslConfigBuilder.peerPort(port);
                }
            }

            builder.sslConfig(sslConfigBuilder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure SSL", e);
        }
    }

    private static void applyProxyConfiguration(
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder,
            URI endpoint,
            @Nullable ProxyOptions proxyOptions) {
        if (proxyOptions == null) {
            return;
        }
        java.net.ProxySelector proxySelector = proxyOptions.getProxySelector();
        if (proxySelector == null) {
            return;
        }
        java.util.List<java.net.Proxy> proxies = proxySelector.select(endpoint);
        if (proxies == null || proxies.isEmpty() || proxies.get(0).type() == java.net.Proxy.Type.DIRECT) {
            return;
        }
        java.net.Proxy proxy = proxies.get(0);
        if (proxy.type() != java.net.Proxy.Type.HTTP) {
            // ServiceTalk only supports HTTP proxies for CONNECT tunneling.
            return;
        }
        java.net.SocketAddress proxySocketAddress = proxy.address();
        if (!(proxySocketAddress instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Proxy address must be InetSocketAddress, got: " +
                    proxySocketAddress.getClass().getName());
        }
        HostAndPort proxyAddress = HostAndPort.of((InetSocketAddress) proxySocketAddress);
        // TODO: proxy authentication is not plumbed through. OTel's ProxyOptions does not currently
        //  expose credentials directly; users that need authenticated proxies must rely on
        //  java.net.Authenticator.setDefault() or system properties.
        builder.proxyConfig(new ProxyConfigBuilder<>(proxyAddress).build());
    }

    private static void applyRetryPolicy(
            SingleAddressHttpClientBuilder<?, ?> builder,
            @Nullable RetryPolicy retryPolicy,
            Function<HttpResponseMetaData, HttpResponseException> responseMapper) {

        if (retryPolicy == null || retryPolicy.getMaxAttempts() <= 1) {
            return;
        }

        // OTel's maxAttempts includes the initial attempt; ServiceTalk's maxTotalRetries is the
        // count of additional attempts.
        final int maxRetries = retryPolicy.getMaxAttempts() - 1;
        final Duration initialBackoff = retryPolicy.getInitialBackoff();
        final Duration maxBackoff = retryPolicy.getMaxBackoff();
        final double backoffMultiplier = retryPolicy.getBackoffMultiplier();
        final Predicate<IOException> userPredicate = retryPolicy.getRetryExceptionPredicate();

        RetryingHttpRequesterFilter.Builder retryBuilder = new RetryingHttpRequesterFilter.Builder()
                .maxTotalRetries(maxRetries)
                .responseMapper(responseMapper);

        // Invoked only for ServiceTalk-classified RetryableExceptions (RetryableConnectException,
        // RetryableClosedChannelException, etc.); the user predicate may narrow within that set.
        retryBuilder.retryRetryableExceptions((metadata, throwable) -> {
            final boolean retry = userPredicate == null
                    || (throwable instanceof IOException && userPredicate.test((IOException) throwable));
            return retry ? backOff(backoffMultiplier, initialBackoff, maxBackoff, maxRetries)
                    : RetryingHttpRequesterFilter.BackOffPolicy.ofNoRetries();
        });

        // retryOther catches anything not matched by the typed hooks above, letting a user predicate
        // broaden retries to plain IOExceptions ServiceTalk did not classify as retryable.
        if (userPredicate != null) {
            retryBuilder.retryOther((metadata, throwable) -> {
                if (throwable instanceof IOException && userPredicate.test((IOException) throwable)) {
                    return backOff(backoffMultiplier, initialBackoff, maxBackoff, maxRetries);
                }
                return RetryingHttpRequesterFilter.BackOffPolicy.ofNoRetries();
            });
        }

        // returnOriginalResponses=true: after retries are exhausted, the original response (with
        // its retryable status) is returned to the caller rather than the synthetic exception.
        retryBuilder.retryResponses(
                (metadata, exception) -> backOff(backoffMultiplier, initialBackoff, maxBackoff, maxRetries),
                /* returnOriginalResponses */ true);

        builder.appendClientFilter(retryBuilder.build());
    }

    // ServiceTalk's BackOffPolicy doesn't expose the multiplier, so an OTel multiplier > 1.0 is
    // coerced to ServiceTalk's fixed 2.0x exponential. Multiplier == 1.0 maps cleanly to constant.
    private static RetryingHttpRequesterFilter.BackOffPolicy backOff(
            double multiplier, Duration initial, Duration max, int maxRetries) {
        return multiplier > 1.0 ?
                RetryingHttpRequesterFilter.BackOffPolicy.ofExponentialBackoffFullJitter(initial, max, maxRetries)
                : RetryingHttpRequesterFilter.BackOffPolicy.ofConstantBackoffFullJitter(initial, maxRetries);
    }

    private static final class HeadersSupplierFilterFactory implements StreamingHttpClientFilterFactory {

        private final Supplier<Map<String, List<String>>> headersSupplier;

        HeadersSupplierFilterFactory(Supplier<Map<String, List<String>>> headersSupplier) {
            this.headersSupplier = headersSupplier;
        }

        @Override
        public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                StreamingHttpRequest request) {
                    Map<String, List<String>> dynamic = headersSupplier.get();
                    if (dynamic != null && !dynamic.isEmpty()) {
                        dynamic.forEach((key, values) -> {
                            if (values != null) {
                                values.forEach(value -> request.headers().add(key, value));
                            }
                        });
                    }
                    return delegate.request(request);
                }
            };
        }
    }
}
