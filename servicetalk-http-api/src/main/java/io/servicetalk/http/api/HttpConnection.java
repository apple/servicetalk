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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;

import org.reactivestreams.Subscriber;

/**
 * Represents a single fixed connection to a HTTP server.
 */
public abstract class HttpConnection extends HttpRequester {
    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #getHttpResponseFactory()}.
     */
    protected HttpConnection(final HttpRequestResponseFactory reqRespFactory) {
        super(reqRespFactory);
    }

    /**
     * Get the {@link ConnectionContext}.
     *
     * @return the {@link ConnectionContext}.
     */
    public abstract ConnectionContext getConnectionContext();

    /**
     * Returns a {@link Publisher} that gives the current value of the setting as well as subsequent changes to
     * the setting value as long as the {@link Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link Publisher} for the setting values.
     */
    public abstract <T> Publisher<T> getSettingStream(SettingKey<T> settingKey);

    /**
     * Convert this {@link HttpConnection} to the {@link StreamingHttpConnection} API.
     *
     * @return a {@link StreamingHttpConnection} representation of this {@link HttpConnection}.
     */
    public final StreamingHttpConnection asStreamingConnection() {
        return asStreamingConnectionInternal();
    }

    /**
     * Convert this {@link HttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     *
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link HttpConnection}.
     */
    public final BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return asStreamingConnection().asBlockingStreamingConnection();
    }

    /**
     * Convert this {@link HttpConnection} to the {@link BlockingHttpConnection} API.
     *
     * @return a {@link BlockingHttpConnection} representation of this {@link HttpConnection}.
     */
    public final BlockingHttpConnection asBlockingConnection() {
        return asBlockingConnectionInternal();
    }

    StreamingHttpConnection asStreamingConnectionInternal() {
        return new HttpConnectionToStreamingHttpConnection(this);
    }

    BlockingHttpConnection asBlockingConnectionInternal() {
        return new HttpConnectionToBlockingHttpConnection(this);
    }
}
