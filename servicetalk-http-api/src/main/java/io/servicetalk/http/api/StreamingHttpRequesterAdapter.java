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
 * A {@link StreamingHttpRequester} that delegates all methods to a different {@link StreamingHttpRequester}.
 */
public abstract class StreamingHttpRequesterAdapter extends StreamingHttpRequester {
    private final StreamingHttpRequester delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpRequester} to delegate all calls to.
     */
    protected StreamingHttpRequesterAdapter(final StreamingHttpRequester delegate) {
        super(delegate.reqRespFactory);
        this.delegate = delegate;
    }

    /**
     * Get the {@link StreamingHttpRequester} that this class delegates to.
     * @return the {@link StreamingHttpRequester} that this class delegates to.
     */
    protected final StreamingHttpRequester delegate() {
        return delegate;
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
        return StreamingHttpRequesterAdapter.class.getSimpleName() + "(" + delegate + ")";
    }
}
