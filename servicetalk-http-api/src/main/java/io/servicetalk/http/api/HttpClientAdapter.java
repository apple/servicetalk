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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * A {@link HttpClient} that delegates all methods to a different {@link HttpClient}.
 */
public abstract class HttpClientAdapter extends HttpClient {
    private final HttpClient delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link HttpClient} to delegate all calls to.
     */
    protected HttpClientAdapter(final HttpClient delegate) {
        super(delegate.reqRespFactory);
        this.delegate = delegate;
    }

    /**
     * Get the {@link HttpClient} that this class delegates to.
     * @return the {@link HttpClient} that this class delegates to.
     */
    protected final HttpClient delegate() {
        return delegate;
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                      final HttpRequest request) {
        return delegate.reserveConnection(strategy, request);
    }

    @Override
    public Single<? extends UpgradableHttpResponse> upgradeConnection(final HttpRequest request) {
        return delegate.upgradeConnection(request);
    }

    @Override
    public Single<? extends HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
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
        return HttpClientAdapter.class.getSimpleName() + "(" + delegate + ")";
    }
}
