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
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * Represents a single fixed connection to a HTTP server.
 */
public class HttpConnection extends HttpRequester {

    private final StreamingHttpConnection connection;

    /**
     * Create a new instance.
     *
     * @param connection {@link StreamingHttpConnection} to convert from.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    HttpConnection(final StreamingHttpConnection connection,
                   final HttpExecutionStrategy strategy) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(connection.reqRespFactory), strategy);
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
    public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
        return connection.request(strategy, request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
    }

    /**
     * Returns a {@link Publisher} that gives the current value of the setting as well as subsequent changes to
     * the setting value as long as the {@link Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link Publisher} for the setting values.
     */
    public final <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
        return connection.settingStream(settingKey);
    }

    /**
     * Convert this {@link HttpConnection} to the {@link StreamingHttpConnection} API.
     *
     * @return a {@link StreamingHttpConnection} representation of this {@link HttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ StreamingHttpConnection asStreamingConnection() {
        return connection;
    }

    /**
     * Convert this {@link HttpConnection} to the {@link BlockingStreamingHttpConnection} API.
     *
     * @return a {@link BlockingStreamingHttpConnection} representation of this {@link HttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return asStreamingConnection().asBlockingStreamingConnection();
    }

    /**
     * Convert this {@link HttpConnection} to the {@link BlockingHttpConnection} API.
     *
     * @return a {@link BlockingHttpConnection} representation of this {@link HttpConnection}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However the constructor of this class is package private so the user will not be able to override this method.
    public /* final */ BlockingHttpConnection asBlockingConnection() {
        return asStreamingConnection().asBlockingConnection();
    }

    @Override
    public ExecutionContext executionContext() {
        return connection.executionContext();
    }

    @Override
    public Completable onClose() {
        return connection.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connection.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connection.closeAsyncGracefully();
    }
}
