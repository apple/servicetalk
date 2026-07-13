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
package io.servicetalk.grpc.api;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for a client {@link GrpcClientCallFactory}: the shared {@link GrpcMessageConfig} message settings plus
 * client-only settings such as the default call timeout.
 *
 * @see Builder
 */
public final class GrpcClientCallConfig {

    private final GrpcMessageConfig messageConfig;
    @Nullable
    private final Duration defaultTimeout;

    private GrpcClientCallConfig(final GrpcMessageConfig messageConfig, @Nullable final Duration defaultTimeout) {
        this.messageConfig = messageConfig;
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Returns the {@link GrpcMessageConfig} message settings.
     *
     * @return the {@link GrpcMessageConfig} message settings.
     */
    public GrpcMessageConfig messageConfig() {
        return messageConfig;
    }

    /**
     * Returns the default call timeout, or {@code null} if none is configured.
     *
     * @return the default call timeout, or {@code null} if none is configured.
     */
    @Nullable
    public Duration defaultTimeout() {
        return defaultTimeout;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{messageConfig=" + messageConfig +
                ", defaultTimeout=" + defaultTimeout + '}';
    }

    /**
     * Builder for {@link GrpcClientCallConfig}.
     */
    public static final class Builder {

        private GrpcMessageConfig messageConfig = new GrpcMessageConfig.Builder().build();
        @Nullable
        private Duration defaultTimeout;

        /**
         * Set the {@link GrpcMessageConfig} message settings, such as the maximum inbound (response) message size.
         *
         * @param messageConfig the message settings to use.
         * @return {@code this}.
         */
        public Builder messageConfig(final GrpcMessageConfig messageConfig) {
            this.messageConfig = requireNonNull(messageConfig);
            return this;
        }

        /**
         * Set the default timeout applied to calls that carry no deadline of their own; a timeout specified on a
         * request supersedes this default.
         *
         * @param defaultTimeout the default timeout (should be positive), or {@code null} for no default timeout.
         * @return {@code this}.
         */
        public Builder defaultTimeout(@Nullable final Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout == null ? null : ensurePositive(defaultTimeout, "defaultTimeout");
            return this;
        }

        /**
         * Builds a new {@link GrpcClientCallConfig}.
         *
         * @return a new {@link GrpcClientCallConfig}.
         */
        public GrpcClientCallConfig build() {
            return new GrpcClientCallConfig(messageConfig, defaultTimeout);
        }
    }
}
