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
import io.servicetalk.http.api.HttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;

import org.reactivestreams.Subscriber;

/**
 * The equivalent of {@link HttpConnection} but with synchronous/blocking APIs instead of asynchronous APIs.
 * @param <I> Type of payload of a request handled by this {@link BlockingHttpConnection}.
 * @param <O> Type of payload of a response handled by this {@link BlockingHttpConnection}.
 */
public abstract class BlockingHttpConnection<I, O> extends BlockingHttpRequester<I, O> {
    /**
     * Get the {@link ConnectionContext}.
     * @return the {@link ConnectionContext}.
     */
    public abstract ConnectionContext getConnectionContext();

    /**
     * Returns a {@link BlockingIterable} that gives the current value of the setting as well as subsequent changes to
     * the setting value as long as the {@link Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link BlockingIterable} for the setting values.
     */
    public abstract <T> BlockingIterable<T> getSettingIterable(SettingKey<T> settingKey);

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link HttpConnection} asynchronous API.
     * <p>
     * Note that the resulting {@link HttpConnection} will still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpConnection}.
     * @return a {@link HttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    public final HttpConnection<I, O> asAsynchronousClient() {
        return asAsynchronousClientInternal();
    }

    HttpConnection<I, O> asAsynchronousClientInternal() {
        return new BlockingHttpConnectionToHttpConnection<>(this);
    }
}
