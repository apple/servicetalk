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

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;

import org.reactivestreams.Subscriber;

/**
 * The equivalent of {@link HttpConnection} but that accepts {@link FullHttpRequest} and returns
 * {@link FullHttpResponse}.
 */
public abstract class AggregatedHttpConnection extends AggregatedHttpRequester {
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
     * @return {@link BlockingIterable} for the setting values.
     */
    public abstract <T> Publisher<T> getSettingStream(SettingKey<T> settingKey);

    /**
     * Convert this {@link AggregatedHttpConnection} to the {@link HttpConnection} asynchronous API.
     *
     * @return a {@link HttpConnection} representation of this {@link AggregatedHttpConnection}.
     */
    public final HttpConnection<HttpPayloadChunk, HttpPayloadChunk> asClient() {
        return asClientInternal();
    }

    HttpConnection<HttpPayloadChunk, HttpPayloadChunk> asClientInternal() {
        return new AggregatedHttpConnectionToHttpConnection(this);
    }
}
