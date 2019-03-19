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
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.RequestResponseFactories.toBlockingStreaming;

/**
 * The equivalent of {@link StreamingHttpConnection} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public /* final */ class BlockingStreamingHttpConnection extends BlockingStreamingHttpRequester {

    private final StreamingHttpConnection connection;

    /**
     * Create a new instance.
     *
     * @param connection {@link StreamingHttpConnection} to convert from.
     * {@link #newRequest(HttpRequestMethod, String) create new requests}.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    BlockingStreamingHttpConnection(final StreamingHttpConnection connection,
                                    final HttpExecutionStrategy strategy) {
        super(toBlockingStreaming(connection.reqRespFactory), strategy);
        this.connection = connection;
    }

    /**
     * Get the {@link ConnectionContext}.
     *
     * @return the {@link ConnectionContext}.
     */
    public final ConnectionContext connectionContext() {
        return connection.connectionContext();
    }

    @Override
    public final BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                       final BlockingStreamingHttpRequest request) throws Exception {
        return BlockingUtils.request(connection, strategy, request);
    }

    /**
     * Returns a {@link BlockingIterable} that gives the current value of the setting as well as subsequent changes to
     * the setting value.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link BlockingIterable} for the setting values.
     */
    public final <T> BlockingIterable<T> settingIterable(SettingKey<T> settingKey) {
        return connection.settingStream(settingKey).toIterable();
    }

    /**
     * Convert this {@link BlockingStreamingHttpConnection} to the {@link StreamingHttpConnection} API.
     * <p>
     * Note that the resulting {@link StreamingHttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpConnection}.
     *
     * @return a {@link StreamingHttpConnection} representation of this {@link BlockingStreamingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ StreamingHttpConnection asStreamingConnection() {
        return connection;
    }

    /**
     * Convert this {@link BlockingStreamingHttpConnection} to the {@link HttpConnection} API.
     * <p>
     * Note that the resulting {@link HttpConnection} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpConnection}.
     *
     * @return a {@link HttpConnection} representation of this {@link BlockingStreamingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ HttpConnection asConnection() {
        return asStreamingConnection().asConnection();
    }

    /**
     * Convert this {@link BlockingStreamingHttpConnection} to the {@link BlockingHttpConnection} API.
     * <p>
     * Note that the resulting {@link BlockingHttpConnection} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingStreamingHttpConnection}.
     *
     * @return a {@link BlockingHttpConnection} representation of this
     * {@link BlockingStreamingHttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ BlockingHttpConnection asBlockingConnection() {
        return asStreamingConnection().asBlockingConnection();
    }

    @Override
    public final ExecutionContext executionContext() {
        return connection.executionContext();
    }

    @Override
    public final void close() throws Exception {
        blockingInvocation(connection.closeAsync());
    }
}
