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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;

/**
 * The equivalent of {@link HttpConnection} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpConnection extends BlockingHttpRequester {

    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests}.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    BlockingHttpConnection(final HttpRequestResponseFactory reqRespFactory, final HttpExecutionStrategy strategy) {
        super(reqRespFactory, strategy);
    }

    /**
     * Get the {@link ConnectionContext}.
     *
     * @return the {@link ConnectionContext}.
     */
    public abstract ConnectionContext connectionContext();

    /**
     * Returns a {@link BlockingIterable} that gives the current value of the setting as well as subsequent changes to
     * the setting value as long as the {@link Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link BlockingIterable} for the setting values.
     */
    public abstract <T> BlockingIterable<T> settingIterable(SettingKey<T> settingKey);

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link StreamingHttpConnection} API.
     * <p>
     * Note that the resulting {@link StreamingHttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link StreamingHttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    public abstract StreamingHttpConnection asStreamingConnection();

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * Note that the resulting {@link HttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link HttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ HttpConnection asConnection() {
        return asStreamingConnection().asConnection();
    }

    /**
     * Convert this {@link BlockingHttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     * <p>
     * Note that the resulting {@link BlockingStreamingHttpConnection} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingHttpConnection}.
     *
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link BlockingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return asStreamingConnection().asBlockingStreamingConnection();
    }
}
