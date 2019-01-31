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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ReservedStreamingHttpConnection} that delegates all methods to a different
 * {@link ReservedStreamingHttpConnection}.
 */
public abstract class ReservedStreamingHttpConnectionFilter extends ReservedStreamingHttpConnection {
    private final ReservedStreamingHttpConnection delegate;
    private final HttpExecutionStrategy defaultStrategy;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedStreamingHttpConnection} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final ReservedStreamingHttpConnection delegate) {
        super(delegate.reqRespFactory);
        this.delegate = requireNonNull(delegate);
        defaultStrategy = executionStrategy();
    }

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedStreamingHttpConnection} to delegate all calls to
     * @param defaultStrategy Default {@link HttpExecutionStrategy} to use
     */
    protected ReservedStreamingHttpConnectionFilter(final ReservedStreamingHttpConnection delegate,
                                                    final HttpExecutionStrategy defaultStrategy) {
        super(delegate.reqRespFactory);
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = requireNonNull(defaultStrategy);
    }

    /**
     * Get the {@link ReservedStreamingHttpConnection} that this class delegates to.
     *
     * @return the {@link ReservedStreamingHttpConnection} that this class delegates to
     */
    protected final ReservedStreamingHttpConnection delegate() {
        return delegate;
    }

    @Override
    public Completable releaseAsync() {
        return delegate.releaseAsync();
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(defaultStrategy, request);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }
}
