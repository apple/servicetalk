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

import io.servicetalk.transport.api.ExecutionContext;

import java.util.Objects;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Configuration for binding a {@link GrpcServiceFactory} to a server: the shared message settings from
 * {@link GrpcConfig} plus the {@link ExecutionContext} the bound service runs on.
 *
 * @see Builder
 */
public final class GrpcServiceConfig extends GrpcConfig {

    private final ExecutionContext<?> executionContext;

    private GrpcServiceConfig(final int maxInboundMessageSize, final ExecutionContext<?> executionContext) {
        super(maxInboundMessageSize);
        this.executionContext = executionContext;
    }

    /**
     * Returns the {@link ExecutionContext} the bound service runs on.
     *
     * @return the {@link ExecutionContext} the bound service runs on.
     */
    public ExecutionContext<?> executionContext() {
        return executionContext;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GrpcServiceConfig that = (GrpcServiceConfig) o;
        return maxInboundMessageSize() == that.maxInboundMessageSize() &&
                executionContext.equals(that.executionContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxInboundMessageSize(), executionContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{maxInboundMessageSize=" + maxInboundMessageSize() +
                ", executionContext=" + executionContext + '}';
    }

    /**
     * Builder for {@link GrpcServiceConfig}.
     */
    public static final class Builder extends GrpcConfig.Builder<Builder> {

        @Nullable
        private ExecutionContext<?> executionContext;

        /**
         * Set the {@link ExecutionContext} the bound service runs on.
         *
         * @param executionContext the {@link ExecutionContext} to use for the bound service.
         * @return {@code this}.
         */
        public Builder executionContext(final ExecutionContext<?> executionContext) {
            this.executionContext = requireNonNull(executionContext);
            return this;
        }

        /**
         * Builds a new {@link GrpcServiceConfig}.
         *
         * @return a new {@link GrpcServiceConfig}.
         */
        public GrpcServiceConfig build() {
            return new GrpcServiceConfig(maxInboundMessageSize(),
                    requireNonNull(executionContext, "executionContext"));
        }

        @Override
        protected Builder thisBuilder() {
            return this;
        }
    }
}
