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
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

final class HttpConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConfig.class);

    static final int DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE = 64 * 1024 * 1024;
    static final int DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE = 16 * 1024 * 1024;
    // FIXME: 0.43 - remove these temporary properties
    static final String DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY =
            "io.servicetalk.http.netty.temporaryDefaultClientMaxAggregatedPayloadSize";
    static final String DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY =
            "io.servicetalk.http.netty.temporaryDefaultServerMaxAggregatedPayloadSize";
    // Deprecated in favor of the client/server-specific properties above; kept for a release or two in case a
    // deployment already relies on it. A role-specific property, when set, takes precedence over this legacy one.
    static final String DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY =
            "io.servicetalk.http.netty.temporaryDefaultMaxAggregatedPayloadSize";
    // When set, override the built-in per-role default using the same sign convention as the builder API (see
    // maxAggregatedPayloadSize(int)). Null when neither the role-specific nor the legacy property is set.
    @Nullable
    static final Integer DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_OVERRIDE;
    @Nullable
    static final Integer DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_OVERRIDE;

    static {
        final Integer legacy = parseTemporaryProperty(DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY, true);
        final Integer client = parseTemporaryProperty(DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY, false);
        final Integer server = parseTemporaryProperty(DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY, false);
        DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_OVERRIDE = client != null ? client : legacy;
        DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_OVERRIDE = server != null ? server : legacy;
    }

    /**
     * Whether a config belongs to a client or a server. Selects the built-in default aggregation limit and the wording
     * of the warn-only log message.
     */
    enum Role {
        CLIENT(DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE, "client"),
        SERVER(DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE, "server");

        // Negative => warn-only (rate-limited) at this magnitude by default rather than rejecting; a value set
        // programmatically via the builder is always definitive.
        final int defaultMaxAggregatedPayloadSize;
        final String description;

        Role(final int defaultWarnSize, final String description) {
            this.defaultMaxAggregatedPayloadSize = -defaultWarnSize;
            this.description = description;
        }
    }

    private final Consumer<H2ProtocolConfig> h2ConfigValidator;
    private final Role role;
    @Nullable
    private H1ProtocolConfig h1Config;
    @Nullable
    private H2ProtocolConfig h2Config;
    private List<String> supportedAlpnProtocols;
    private boolean allowDropTrailers;
    private int maxAggregatedPayloadSize;

    HttpConfig(final Consumer<H2ProtocolConfig> h2ConfigValidator, final Role role) {
        this.h2ConfigValidator = requireNonNull(h2ConfigValidator);
        this.role = role;
        final Integer override = role == Role.CLIENT ? DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_OVERRIDE :
                DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_OVERRIDE;
        this.maxAggregatedPayloadSize = override != null ? override : role.defaultMaxAggregatedPayloadSize;
        h1Config = h1Default();
        h2Config = null;
        supportedAlpnProtocols = emptyList();
    }

    HttpConfig(final HttpConfig from) {
        this.h2ConfigValidator = from.h2ConfigValidator;
        this.role = from.role;
        this.h1Config = from.h1Config;
        this.h2Config = from.h2Config;
        this.supportedAlpnProtocols = from.supportedAlpnProtocols;
        this.allowDropTrailers = from.allowDropTrailers;
        this.maxAggregatedPayloadSize = from.maxAggregatedPayloadSize;
    }

    // Don't throw from the static initializer; ignore an invalid value and fall back to the per-role defaults.
    @Nullable
    private static Integer parseTemporaryProperty(final String name, final boolean legacy) {
        final String raw = System.getProperty(name);
        if (raw == null) {
            return null;
        }
        try {
            final Integer value = Integer.valueOf(raw.trim());
            if (legacy) {
                LOGGER.warn("-D{}={} This property is deprecated in favor of -D{} and -D{} and will be removed in a " +
                                "future release. Configure this value per client/server builder via " +
                                "maxAggregatedPayloadSize(int) instead.", name, value,
                        DEFAULT_CLIENT_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY,
                        DEFAULT_SERVER_MAX_AGGREGATED_PAYLOAD_SIZE_PROPERTY);
            } else {
                LOGGER.warn("-D{}={} This property is temporary and will be removed in a future release. Configure " +
                        "this value per client/server builder via maxAggregatedPayloadSize(int) instead.", name, value);
            }
            return value;
        } catch (NumberFormatException e) {
            LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: The value is not a valid integer; ignoring it and using " +
                    "the built-in per-client/server defaults.", name, raw);
            return null;
        }
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
        this.maxAggregatedPayloadSize = maxAggregatedPayloadSize;
    }

    /**
     * Build the aggregated-payload-size limiter for a single client/server from the configured value, as a
     * {@link LongConsumer} invoked with the running aggregated size. The returned instance carries the warn-mode
     * rate-limiting state, so it must be created once per client/server (see {@link ReadOnlyHttpClientConfig} /
     * {@link ReadOnlyHttpServerConfig}) and shared across its connections.
     */
    LongConsumer newAggregatedPayloadSizeLimiter(@Nullable final Object owner) {
        return toAggregatedPayloadSizeLimiter(maxAggregatedPayloadSize, role, owner);
    }

    /**
     * Map a configured {@code maxAggregatedPayloadSize} to a limiter: {@code 0} disables it, {@code >0} enforces
     * (rejects) at that size, and {@code <0} warns (without rejecting) at {@code abs(configured)}. The sign selects the
     * mode and the magnitude selects the threshold.
     */
    static LongConsumer toAggregatedPayloadSizeLimiter(final int configured, final Role role,
                                                       @Nullable final Object owner) {
        if (configured >= 0) {
            return AggregatedPayloadSizeLimiter.enforcing(configured);
        }
        // -Integer.MIN_VALUE overflows back to a negative value; clamp so it stays a warn-only limiter rather than
        // collapsing to disabled.
        final int warnThreshold = configured == Integer.MIN_VALUE ? Integer.MAX_VALUE : -configured;
        return AggregatedPayloadSizeLimiter.warning(warnThreshold, role, owner);
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
