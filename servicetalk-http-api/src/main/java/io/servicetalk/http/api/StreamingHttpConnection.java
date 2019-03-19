/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_NONE_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_SEND_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpConnection} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public class StreamingHttpConnection extends StreamingHttpRequester {

    final StreamingHttpConnectionFilter filterChain;

    /**
     * Create a new instance.
     *
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    StreamingHttpConnection(final StreamingHttpConnectionFilter filterChain, final HttpExecutionStrategy strategy) {
        super(requireNonNull(filterChain).reqRespFactory, requireNonNull(strategy));
        this.filterChain = filterChain;
    }

    /**
     * Get the {@link ConnectionContext}.
     * @return the {@link ConnectionContext}.
     */
    public final ConnectionContext connectionContext() {
        return filterChain.connectionContext();
    }

    /**
     * Returns a {@link Publisher} that gives the current value of the setting as well as subsequent changes to the
     * setting value as long as the {@link Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link Publisher} for the setting values.
     */
    public final <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
        return filterChain.settingStream(settingKey);
    }

    /**
     * Convert this {@link StreamingHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpConnection} asynchronous API for maximum portability.
     * @return a {@link HttpConnection} representation of this {@link StreamingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ HttpConnection asConnection() {
        return new HttpConnection(this, filterChain
                .effectiveExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY));
    }

    /**
     * Convert this {@link StreamingHttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpConnection} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link StreamingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return new BlockingStreamingHttpConnection(this,
                filterChain.effectiveExecutionStrategy(OFFLOAD_SEND_STRATEGY));
    }

    /**
     * Convert this {@link StreamingHttpConnection} to the {@link BlockingHttpConnection} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpConnection} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpConnection} representation of this {@link StreamingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ BlockingHttpConnection asBlockingConnection() {
        return new BlockingHttpConnection(this, filterChain.effectiveExecutionStrategy(OFFLOAD_NONE_STRATEGY));
    }

    @Override
    public final Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        return filterChain.request(strategy, request);
    }

    @Override
    public final ExecutionContext executionContext() {
        return filterChain.executionContext();
    }

    @Override
    public final Completable onClose() {
        return filterChain.onClose();
    }

    @Override
    public final Completable closeAsync() {
        return filterChain.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return filterChain.closeAsyncGracefully();
    }

    /**
     * A key which identifies a configuration setting for a connection. Setting values may change over time.
     * @param <T> Type of the value of this setting.
     */
    @SuppressWarnings("unused")
    public static final class SettingKey<T> {
        /**
         * Option to define max concurrent requests allowed on a connection.
         */
        public static final SettingKey<Integer> MAX_CONCURRENCY = newKeyWithDebugToString("max-concurrency");

        private final String stringRepresentation;

        private SettingKey(String stringRepresentation) {
            this.stringRepresentation = requireNonNull(stringRepresentation);
        }

        private SettingKey() {
            this.stringRepresentation = super.toString();
        }

        /**
         * Creates a new {@link SettingKey} with the specific {@code name}.
         *
         * @param stringRepresentation of the option. This is only used for debugging purpose and not for key equality.
         * @param <T>                  Type of the value of the option.
         * @return A new {@link SettingKey}.
         */
        static <T> SettingKey<T> newKeyWithDebugToString(String stringRepresentation) {
            return new SettingKey<>(stringRepresentation);
        }

        /**
         * Creates a new {@link SettingKey}.
         *
         * @param <T> Type of the value of the option.
         * @return A new {@link SettingKey}.
         */
        static <T> SettingKey<T> newKey() {
            return new SettingKey<>();
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }
    }
}
