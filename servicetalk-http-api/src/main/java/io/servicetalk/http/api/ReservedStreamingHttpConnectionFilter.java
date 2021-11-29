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

/**
 * A {@link ReservedStreamingHttpConnectionFilter} that delegates all methods to a different
 * {@link ReservedStreamingHttpConnectionFilter}.
 */
public class ReservedStreamingHttpConnectionFilter extends StreamingHttpConnectionFilter
        implements FilterableReservedStreamingHttpConnection {

    private final FilterableReservedStreamingHttpConnection delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link FilterableReservedStreamingHttpConnection} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final FilterableReservedStreamingHttpConnection delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public Completable releaseAsync() {
        return delegate.releaseAsync();
    }

    /**
     * Get the {@link FilterableReservedStreamingHttpConnection} this method delegates to.
     *
     * @return the {@link FilterableReservedStreamingHttpConnection} this method delegates to.
     */
    @Override
    protected final FilterableReservedStreamingHttpConnection delegate() {
        return delegate;
    }
}
