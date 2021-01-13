/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.DelegatingConnectionContext;

/**
 * {@link HttpConnectionContext} implementation that delegates all calls to a provided {@link HttpConnectionContext}.
 * Any of the methods can be overridden by implementations to change the behavior.
 */
public class DelegatingHttpConnectionContext extends DelegatingConnectionContext implements HttpConnectionContext {

    private final HttpConnectionContext delegate;

    /**
     * New instance.
     *
     * @param delegate {@link HttpConnectionContext} to delegate all calls.
     */
    public DelegatingHttpConnectionContext(final HttpConnectionContext delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    /**
     * Get the {@link HttpConnectionContext} that this class delegates to.
     *
     * @return the {@link HttpConnectionContext} that this class delegates to.
     */
    @Override
    protected final HttpConnectionContext delegate() {
        return delegate;
    }

    @Override
    public HttpExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public HttpProtocolVersion protocol() {
        return delegate.protocol();
    }
}
