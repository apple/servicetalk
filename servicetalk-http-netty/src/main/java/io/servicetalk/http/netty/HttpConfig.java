/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpProtocolConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.lang.Integer.getInteger;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

final class HttpConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConfig.class);

    static final int DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE = 4 * 1024 * 1024;
    // Magic value accepted by the temporaryDefaultMaxAggregatedPayloadSize system property (but not by the
    // maxAggregatedPayloadSize(int) builder API): warn (rate-limited) when the default limit is exceeded but let the
    // payload through rather than rejecting it. It exists to ease rollout of a global default; a value set
    // programmatically is always definitive.
    static final int WARN_ONLY_MAX_AGGREGATED_PAYLOAD_SIZE = -1;
    // FIXME: 0.43 - remove this temporary property
    static final String DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY =
            "io.servicetalk.http.netty.temporaryDefaultMaxAggregatedPayloadSize";
    static final int DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE;

    static {
        final int value = getInteger(DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY,
                DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE);
        // The property additionally accepts the warn-only magic value; only values below it are invalid. Don't throw
        // from this static initializer; fall back to the hardcoded default instead.
        if (value < WARN_ONLY_MAX_AGGREGATED_PAYLOAD_SIZE) {
            LOGGER.warn("-D{}: {} is invalid (expected >= {}). Falling back to the default of {} bytes.",
                    DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY, value, WARN_ONLY_MAX_AGGREGATED_PAYLOAD_SIZE,
                    DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE);
            DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE = DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE;
        } else {
            DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE = value;
            if (value != DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE) {
                LOGGER.warn("-D{}: {}. This property will be removed in the future releases. Configure this value " +
                                "per client/server builder via maxAggregatedPayloadSize(int) instead.",
                        DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY, value);
            }
        }
    }

    private final Consumer<H2ProtocolConfig> h2ConfigValidator;
    @Nullable
    private H1ProtocolConfig h1Config;
    @Nullable
    private H2ProtocolConfig h2Config;
    private List<String> supportedAlpnProtocols;
    private boolean allowDropTrailers;
    private int maxAggregatedPayloadSize = DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE;

    HttpConfig(final Consumer<H2ProtocolConfig> h2ConfigValidator) {
        this.h2ConfigValidator = requireNonNull(h2ConfigValidator);
        h1Config = h1Default();
        h2Config = null;
        supportedAlpnProtocols = emptyList();
    }

    HttpConfig(final HttpConfig from) {
        this.h2ConfigValidator = from.h2ConfigValidator;
        this.h1Config = from.h1Config;
        this.h2Config = from.h2Config;
        this.supportedAlpnProtocols = from.supportedAlpnProtocols;
        this.allowDropTrailers = from.allowDropTrailers;
        this.maxAggregatedPayloadSize = from.maxAggregatedPayloadSize;
    }

    @Nullable
    H1ProtocolConfig h1Config() {
        return h1Config;
    }

    @Nullable
    H2ProtocolConfig h2Config() {
        return h2Config;
    }

    List<String> supportedAlpnProtocols() {
        return supportedAlpnProtocols;
    }

    boolean allowDropTrailersReadFromTransport() {
        return allowDropTrailers;
    }

    void allowDropTrailersReadFromTransport(boolean allowDrop) {
        this.allowDropTrailers = allowDrop;
    }

    void maxAggregatedPayloadSize(int maxAggregatedPayloadSize) {
        this.maxAggregatedPayloadSize = ensureNonNegative(maxAggregatedPayloadSize, "maxAggregatedPayloadSize");
    }

    /**
     * Build the aggregated-payload-size limiter for a single client/server from the configured value, as a
     * {@link LongConsumer} invoked with the running aggregated size. The returned instance carries the warn-mode
     * rate-limiting state, so it must be created once per client/server (see {@link ReadOnlyHttpClientConfig} /
     * {@link ReadOnlyHttpServerConfig}) and shared across its connections.
     */
    LongConsumer newAggregatedPayloadSizeLimiter() {
        return toAggregatedPayloadSizeLimiter(maxAggregatedPayloadSize, DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE);
    }

    /**
     * Map a configured {@code maxAggregatedPayloadSize} to a limiter. {@code 0} disables it, {@code >0} enforces
     * (rejects) at that size, and {@link #WARN_ONLY_MAX_AGGREGATED_PAYLOAD_SIZE -1} warns (without rejecting) at
     * {@code resolvedDefault}. Because the {@code resolvedDefault} can itself be the warn-only ({@code -1}) or disabled
     * ({@code 0}) selector when the default was set via system property, warn-only mode falls back to
     * {@link #DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE} when {@code resolvedDefault} is not positive, so warn-only
     * mode never silently collapses to "disabled".
     */
    static LongConsumer toAggregatedPayloadSizeLimiter(final int configured, final int resolvedDefault) {
        if (configured == WARN_ONLY_MAX_AGGREGATED_PAYLOAD_SIZE) {
            return AggregatedPayloadSizeLimiter.warning(
                    resolvedDefault > 0 ? resolvedDefault : DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE);
        }
        return AggregatedPayloadSizeLimiter.enforcing(configured);
    }

    void protocols(final HttpProtocolConfig... protocols) {
        requireNonNull(protocols);
        if (protocols.length < 1) {
            throw new IllegalArgumentException("No protocols specified");
        }

        h1Config = null;
        h2Config = null;
        for (HttpProtocolConfig protocol : protocols) {
            if (protocol instanceof H1ProtocolConfig) {
                h1Config((H1ProtocolConfig) protocol);
            } else if (protocol instanceof H2ProtocolConfig) {
                h2Config((H2ProtocolConfig) protocol);
            } else {
                throw new IllegalArgumentException("Unsupported HttpProtocolConfig: " + protocol.getClass().getName() +
                        ", see " + HttpProtocolConfigs.class.getName());
            }
        }
    }

    private void h1Config(final H1ProtocolConfig h1Config) {
        if (this.h1Config != null) {
            throw new IllegalArgumentException("Duplicated configuration for HTTP/1.1 was found");
        }
        this.h1Config = h1Config;
        // We intentionally do not configure a list of ALPN IDs when only h1Config is provided, because it's
        // not required for HTTP/1.1 and users' environment may not support ALPN
        supportedAlpnProtocols = h2Config == null ? emptyList() :
                unmodifiableList(asList(h2Config.alpnId(), h1Config.alpnId()));
    }

    private void h2Config(final H2ProtocolConfig h2Config) {
        if (this.h2Config != null) {
            throw new IllegalArgumentException("Duplicated configuration for HTTP/2 was found");
        }
        h2ConfigValidator.accept(h2Config);
        this.h2Config = h2Config;
        supportedAlpnProtocols = h1Config == null ? singletonList(h2Config.alpnId()) :
                unmodifiableList(asList(h1Config.alpnId(), h2Config.alpnId()));
    }
}
