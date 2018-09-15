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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

/**
 * Makes the wrapped {@link StreamingHttpConnection} aware of the {@link LoadBalancer}.
 */
final class LoadBalancedStreamingHttpConnection extends ReservedStreamingHttpConnection
        implements ReservableRequestConcurrencyController {
    private final StreamingHttpConnection delegate;
    private final ReservableRequestConcurrencyController limiter;

    LoadBalancedStreamingHttpConnection(StreamingHttpConnection delegate,
                                        ReservableRequestConcurrencyController limiter) {
        super(delegate, delegate.getHttpResponseFactory());
        this.delegate = requireNonNull(delegate);
        this.limiter = requireNonNull(limiter);
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return delegate.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
        return delegate.getSettingStream(settingKey);
    }

    @Override
    public Single<? extends StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return delegate.request(request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return delegate.getExecutionContext();
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
    public boolean tryReserve() {
        return limiter.tryReserve();
    }

    @Override
    public boolean tryRequest() {
        return limiter.tryRequest();
    }

    @Override
    public void requestFinished() {
        limiter.requestFinished();
    }

    @Override
    public Completable releaseAsync() {
        return limiter.releaseAsync();
    }

    @Override
    public String toString() {
        return LoadBalancedStreamingHttpConnection.class.getSimpleName() + "(" + delegate + ")";
    }
}
