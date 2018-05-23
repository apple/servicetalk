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
 * The equivalent of {@link AggregatedHttpConnection} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingAggregatedHttpConnection extends BlockingAggregatedHttpRequester {
    /**
     * Get the {@link ConnectionContext}.
     *
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
     * Convert this {@link BlockingAggregatedHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * Note that the resulting {@link HttpConnection} may still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingAggregatedHttpConnection}.
     *
     * @return a {@link HttpConnection} representation of this {@link BlockingAggregatedHttpConnection}.
     */
    public final HttpConnection asConnection() {
        return asConnectionInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpConnection} to the {@link AggregatedHttpConnection} API.
     * <p>
     * Note that the resulting {@link AggregatedHttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingAggregatedHttpConnection}.
     *
     * @return a {@link AggregatedHttpConnection} representation of this {@link BlockingAggregatedHttpConnection}.
     */
    public final AggregatedHttpConnection asAggregatedConnection() {
        return asAggregatedConnectionInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpConnection} to the {@link BlockingHttpConnection} API.
     * <p>
     * Note that the resulting {@link BlockingHttpConnection} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingAggregatedHttpConnection}.
     *
     * @return a {@link BlockingHttpConnection} representation of this {@link BlockingAggregatedHttpConnection}.
     */
    public final BlockingHttpConnection asBlockingConnection() {
        return asConnection().asBlockingConnection();
    }

    HttpConnection asConnectionInternal() {
        return new BlockingAggregatedHttpConnectionToHttpConnection(this);
    }

    AggregatedHttpConnection asAggregatedConnectionInternal() {
        return new BlockingAggregatedHttpConnectionToAggregatedHttpConnection(this);
    }
}
