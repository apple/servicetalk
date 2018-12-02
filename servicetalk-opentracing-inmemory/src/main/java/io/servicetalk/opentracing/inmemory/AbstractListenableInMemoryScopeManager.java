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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.ListenableInMemoryScopeManager;

import javax.annotation.Nullable;

/**
 * A {@link ListenableInMemoryScopeManager} that handles the {@link InMemorySpanChangeListener} interaction.
 */
public abstract class AbstractListenableInMemoryScopeManager implements ListenableInMemoryScopeManager {
    private final CopyOnWriteInMemorySpanChangeListenerSet listenerSet = new CopyOnWriteInMemorySpanChangeListenerSet();

    @Override
    public final void addListener(final InMemorySpanChangeListener listener) {
        listenerSet.add(listener);
    }

    @Override
    public final void removeListener(final InMemorySpanChangeListener listener) {
        listenerSet.remove(listener);
    }

    /**
     * Notifies all listeners that the span has changed from {@code oldSpan} to {@code newSpan}.
     * @param oldSpan The old span.
     * @param newSpan The new span.
     */
    protected final void notifySpanChanged(@Nullable InMemorySpan oldSpan, @Nullable InMemorySpan newSpan) {
        listenerSet.spanChanged(oldSpan, newSpan);
    }
}
