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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;

/**
 * Provides a means to issue requests against HTTP service. The implementation is free to maintain a collection of
 * {@link HttpConnection} instances and distribute calls to {@link #request(HttpRequest)} amongst this collection.
 */
public abstract class HttpClient extends HttpRequester {

    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests}.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    HttpClient(final HttpRequestResponseFactory reqRespFactory, final HttpExecutionStrategy strategy) {
        super(reqRespFactory, strategy);
    }

    /**
     * Reserve an {@link HttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link HttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedHttpConnection} upon completion.
     */
    public Single<ReservedHttpConnection> reserveConnection(HttpRequestMetaData metaData) {
        return reserveConnection(executionStrategy(), metaData);
    }

    /**
     * Reserve an {@link HttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link HttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedHttpConnection} upon completion.
     */
    public abstract Single<ReservedHttpConnection> reserveConnection(HttpExecutionStrategy strategy,
                                                                     HttpRequestMetaData metaData);

    /**
     * Convert this {@link HttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link HttpClient}.
     */
    public abstract StreamingHttpClient asStreamingClient();

    /**
     * Convert this {@link HttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link HttpClient}.
     */
    public final BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }

    /**
     * Convert this {@link HttpClient} to the {@link BlockingHttpClient} API.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link HttpClient}.
     */
    public final BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    /**
     * A special type of {@link HttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public abstract static class ReservedHttpConnection extends HttpConnection {

        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests}.
         * @param strategy Default {@link HttpExecutionStrategy} to use.
         */
        ReservedHttpConnection(final HttpRequestResponseFactory reqRespFactory,
                               final HttpExecutionStrategy strategy) {
            super(reqRespFactory, strategy);
        }

        /**
         * Get the {@link ConnectionContext}.
         *
         * @return the {@link ConnectionContext}.
         */
        @Override
        public final ConnectionContext connectionContext() {
            return asStreamingConnection().connectionContext();
        }

        /**
         * Returns a {@link Publisher} that gives the current value of the setting as well as subsequent changes to
         * the setting value as long as the {@link PublisherSource.Subscriber} has expressed enough demand.
         *
         * @param settingKey Name of the setting to fetch.
         * @param <T> Type of the setting value.
         * @return {@link Publisher} for the setting values.
         */
        @Override
        public final <T> Publisher<T> settingStream(StreamingHttpConnection.SettingKey<T> settingKey) {
            return asStreamingConnection().settingStream(settingKey);
        }

        /**
         * Releases this reserved {@link ReservedHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public final Completable releaseAsync() {
            return asStreamingConnection().releaseAsync();
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedStreamingHttpConnection} API.
         *
         * @return a {@link ReservedStreamingHttpConnection} representation of this {@link ReservedHttpConnection}.
         */
        @Override
        public abstract ReservedStreamingHttpConnection asStreamingConnection();

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedBlockingStreamingHttpConnection} API.
         *
         * @return a {@link ReservedBlockingStreamingHttpConnection} representation of this
         * {@link ReservedHttpConnection}.
         */
        @Override
        public final ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return asStreamingConnection().asBlockingStreamingConnection();
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedBlockingHttpConnection}
         * API.
         *
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedHttpConnection}.
         */
        @Override
        public final ReservedBlockingHttpConnection asBlockingConnection() {
            return asStreamingConnection().asBlockingConnection();
        }
    }
}
