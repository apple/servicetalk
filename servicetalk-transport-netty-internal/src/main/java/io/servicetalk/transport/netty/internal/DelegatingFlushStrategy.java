/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import static java.util.Objects.requireNonNull;

/**
 * A {@link FlushStrategy} implementation that delegates all calls to another {@link FlushStrategy}.
 */
public class DelegatingFlushStrategy implements FlushStrategy {

    private final FlushStrategy delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link FlushStrategy} to delegate all calls.
     */
    public DelegatingFlushStrategy(final FlushStrategy delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public WriteEventsListener apply(final FlushSender sender) {
        return delegate.apply(sender);
    }

    @Override
    public boolean shouldFlushOnUnwritable() {
        return delegate.shouldFlushOnUnwritable();
    }

    /**
     * Returns the delegate {@link FlushStrategy} used.
     *
     * @return The delegate {@link FlushStrategy} used.
     */
    protected FlushStrategy delegate() {
        return delegate;
    }
}
