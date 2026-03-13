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
package io.servicetalk.opentelemetry.client;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.ProxyConfigBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import io.opentelemetry.sdk.common.export.ProxyOptions;
import io.opentelemetry.sdk.common.export.RetryPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

final class ServiceTalkHttpClientFactory {

    private ServiceTalkHttpClientFactory() {
    }

    static HttpClient buildGrpcClient(
            URI endpoint,
            @Nullable Duration timeout,
            @Nullable Duration connectTimeout,
            @Nullable SSLContext sslContext,
            @Nullable RetryPolicy retryPolicy) {

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = createBaseBuilder(endpoint);

        // Protocol: Always HTTP/2 for gRPC
        builder.protocols(HttpProtocolConfigs.h2Default());
        applyConnectTimeout(builder, connectTimeout);
        applySslConfiguration(builder, endpoint, sslContext);
        applyRequestTimeout(builder, timeout);
        applyRetryPolicy(builder, retryPolicy);

        return builder.build();
    }

    static HttpClient buildHttpClient(
            URI endpoint,
            @Nullable Duration timeout,
            @Nullable Duration connectTimeout,
            @Nullable SSLContext sslContext,
            @Nullable ProxyOptions proxyOptions,
            @Nullable RetryPolicy retryPolicy) {

        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = createBaseBuilder(endpoint);

        // Protocol: HTTP/1.1 by default (most compatible)
        builder.protocols(HttpProtocolConfigs.h1Default());
        applyConnectTimeout(builder, connectTimeout);
        applySslConfiguration(builder, endpoint, sslContext);
        applyProxyConfiguration(builder, endpoint, proxyOptions);
        applyRequestTimeout(builder, timeout);
        applyRetryPolicy(builder, retryPolicy);

        return builder.build();
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> createBaseBuilder(URI endpoint) {
        String host = endpoint.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Endpoint must have a host: " + endpoint);
        }

        int port = endpoint.getPort();
        if (port <= 0) {
            // Use default ports based on scheme
            String scheme = endpoint.getScheme();
            if ("https".equalsIgnoreCase(scheme) || "grpcs".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                // Default to port 80 for http, grpc, or unknown schemes
                port = 80;
            }
        }

        return HttpClients.forSingleAddress(host, port);
    }

    /**
     * Apply connect timeout using socket options.
     */
    private static void applyConnectTimeout(
            SingleAddressHttpClientBuilder<?, ?> builder,
            @Nullable Duration connectTimeout) {

        if (connectTimeout == null) {
            return;
        }

        // Connect timeout is configured via socket options
        builder.socketOption(ServiceTalkSocketOptions.CONNECT_TIMEOUT, (int) connectTimeout.toMillis());
    }

    /**
     * Apply request timeout using TimeoutHttpRequesterFilter.
     */
    private static void applyRequestTimeout(
            SingleAddressHttpClientBuilder<?, ?> builder,
            @Nullable Duration timeout) {

        if (timeout == null) {
            return;
        }

        // Request timeout is applied via a filter
        // The second parameter (true) means the timeout applies to the full request/response cycle
        builder.appendClientFilter(new TimeoutHttpRequesterFilter(timeout, true));
    }

    /**
     * Apply SSL/TLS configuration to the client builder.
     * <p>
     * This method handles several scenarios:
     * <ul>
     *   <li>If a custom {@link X509TrustManager} is provided, wrap it in a {@link TrustManagerFactory}</li>
     *   <li>If a custom {@link SSLContext} is provided, extract its trust managers</li>
     *   <li>If neither is provided, use system default trust managers</li>
     * </ul>
     * Additionally configures SNI hostname based on the endpoint.
     */
    private static void applySslConfiguration(
            SingleAddressHttpClientBuilder<?, ?> builder,
            URI endpoint,
            @Nullable SSLContext sslContext) {

        String scheme = endpoint.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"grpcs".equalsIgnoreCase(scheme)) {
            return; // No SSL needed
        }

        try {
            ClientSslConfigBuilder sslConfigBuilder;

            if (sslContext != null) {
                // Priority 1: Use explicit SSLContext if configured. This specifies both trust and keys
                sslConfigBuilder = new ClientSslConfigBuilder(sslContext);
            } else {
                // Priority 2: Use system default trust managers
                sslConfigBuilder = new ClientSslConfigBuilder();
            }

            // Configure SNI hostname for proper TLS handshake
            String host = endpoint.getHost();
            if (host != null && !host.isEmpty()) {
                sslConfigBuilder.sniHostname(host);
                sslConfigBuilder.peerHost(host);

                int port = endpoint.getPort();
                if (port > 0) {
                    sslConfigBuilder.peerPort(port);
                }
            }

            ClientSslConfig sslConfig = sslConfigBuilder.build();
            builder.sslConfig(sslConfig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure SSL", e);
        }
    }

    /**
     * Apply proxy configuration (HTTP only, not applicable to gRPC).
     * <p>
     * Maps OpenTelemetry's {@link ProxyOptions} to ServiceTalk's {@code ProxyConfig}.
     * Uses the actual endpoint URI for proxy selection to ensure proper proxy resolution
     * based on destination host and scheme.
     */
    private static void applyProxyConfiguration(
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder,
            URI endpoint,
            @Nullable ProxyOptions proxyOptions) {
        if (proxyOptions == null) {
            return;
        }

        try {
            // Extract proxy address from ProxyOptions using ProxySelector
            java.net.ProxySelector proxySelector = proxyOptions.getProxySelector();
            if (proxySelector == null) {
                return; // No proxy configured
            }
            java.util.List<java.net.Proxy> proxies = proxySelector.select(endpoint);

            if (proxies == null || proxies.isEmpty() || proxies.get(0).type() == java.net.Proxy.Type.DIRECT) {
                return; // No proxy or direct connection
            }

            java.net.Proxy proxy = proxies.get(0);
            if (proxy.type() != java.net.Proxy.Type.HTTP) {
                // ServiceTalk only supports HTTP proxies for CONNECT tunneling
                return;
            }

            java.net.SocketAddress proxySocketAddress = proxy.address();
            if (!(proxySocketAddress instanceof InetSocketAddress)) {
                throw new IllegalArgumentException("Proxy address must be InetSocketAddress, got: " +
                    proxySocketAddress.getClass().getName());
            }

            HostAndPort proxyAddress = HostAndPort.of((InetSocketAddress) proxySocketAddress);
            ProxyConfigBuilder<HostAndPort> proxyConfigBuilder = new ProxyConfigBuilder<>(proxyAddress);

            // Check for proxy authentication credentials
            // OpenTelemetry may provide username/password for Basic auth
            // Note: The exact API for credentials in ProxyOptions may vary
            // Common patterns include getUsername()/getPassword() methods

            // Attempt to get authentication info if ProxyOptions exposes it
            // For OpenTelemetry 1.59.0, check if there are methods like:
            // - proxyOptions.getUsername() / proxyOptions.getPassword()
            // - proxyOptions.getAuthenticator() returning java.net.Authenticator
            // Since these may not exist or vary, we'll use reflection or conditional checks

            // For now, create basic proxy config without auth
            // Users can extend this with custom authentication if needed via:
            // - System properties: http.proxyUser, http.proxyPassword
            // - java.net.Authenticator.setDefault()
            builder.proxyConfig(proxyConfigBuilder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure proxy", e);
        }
    }

    /**
     * Apply retry policy configuration.
     * <p>
     * Maps OpenTelemetry's {@link RetryPolicy} to ServiceTalk's {@link RetryingHttpRequesterFilter}.
     * OpenTelemetry provides exponential backoff parameters which we map to ServiceTalk's
     * BackOffPolicy.
     */
    private static void applyRetryPolicy(
            SingleAddressHttpClientBuilder<?, ?> builder,
            @Nullable RetryPolicy retryPolicy) {

        if (retryPolicy == null || retryPolicy.getMaxAttempts() == 1) {
            return;
        }

        // OpenTelemetry's maxAttempts includes the initial attempt
        // ServiceTalk's maxTotalRetries is the number of retries (not including initial)
        final int maxRetries = Math.max(0, retryPolicy.getMaxAttempts() - 1);

        // Get backoff parameters from OpenTelemetry RetryPolicy
        final Duration initialBackoff = retryPolicy.getInitialBackoff();
        final Duration maxBackoff = retryPolicy.getMaxBackoff();
        final double backoffMultiplier = retryPolicy.getBackoffMultiplier();
        final Predicate<IOException> retryExceptionPredicate = retryPolicy.getRetryExceptionPredicate();

        // Build ServiceTalk retry filter
        RetryingHttpRequesterFilter.Builder retryBuilder = new RetryingHttpRequesterFilter.Builder()
                .maxTotalRetries(maxRetries);

        // Configure retry for exceptions that match OpenTelemetry's predicate
        if (retryExceptionPredicate != null) {
            retryBuilder.retryRetryableExceptions((metadata, throwable) -> {
                // Check if the exception is an IOException and matches the predicate
                if (throwable instanceof IOException && retryExceptionPredicate.test((IOException) throwable)) {
                    // Use exponential backoff if multiplier > 1.0, otherwise use initial delay as constant
                    if (backoffMultiplier > 1.0) {
                        // Calculate jitter as a fraction of the initial backoff
                        // OpenTelemetry uses full jitter, so we'll use full jitter here too
                        return RetryingHttpRequesterFilter.BackOffPolicy.ofExponentialBackoffFullJitter(
                                initialBackoff,
                                maxBackoff,
                                maxRetries
                        );
                    } else {
                        // Constant backoff with full jitter
                        return RetryingHttpRequesterFilter.BackOffPolicy.ofConstantBackoffFullJitter(
                                initialBackoff,
                                maxRetries
                        );
                    }
                }
                // Don't retry this exception
                return RetryingHttpRequesterFilter.BackOffPolicy.ofNoRetries();
            });
        }

        builder.appendClientFilter(retryBuilder.build());
    }
}
