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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_EMITTING;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_IDLE;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.drainSingleConsumerCollectionDelayThrow;

/**
 * A {@link Cancellable} that contains other {@link Cancellable}s. <p>
 *     {@link Cancellable#cancel()} is propagated to all active {@link Cancellable}s. Any new {@link Cancellable} added after that will be immediately cancelled.
 */
final class MapDynamicCompositeCancellable implements DynamicCompositeCancellable {

    private static final AtomicIntegerFieldUpdater<MapDynamicCompositeCancellable> cancelledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(MapDynamicCompositeCancellable.class, "cancelled");
    @SuppressWarnings("unused")
    private volatile int cancelled;

    private static final AtomicIntegerFieldUpdater<MapDynamicCompositeCancellable> drainingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(MapDynamicCompositeCancellable.class, "draining");
    @SuppressWarnings("unused")
    private volatile int draining;

    private final ConcurrentMap<Cancellable, Cancellable> cancellables;

    /**
     * Create a new instance.
     */
    MapDynamicCompositeCancellable() {
        this(8);
    }

    /**
     * Create a new instance.
     * @param initialSize The initial size of the internal {@link Map}.
     */
    MapDynamicCompositeCancellable(int initialSize) {
        cancellables = new ConcurrentHashMap<>(initialSize);
    }

    @Override
    public void cancel() {
        if (cancelledUpdater.compareAndSet(this, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
            cancelAll();
        }
    }

    @Override
    public boolean add(Cancellable toAdd) {
        if (cancelled()) {
            toAdd.cancel();
            return false;
        } else {
            cancellables.putIfAbsent(toAdd, toAdd);
            if (cancelled()) {
                cancelAll();
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean remove(Cancellable toRemove) {
        return cancellables.remove(toRemove) != null;
    }

    @Override
    public boolean cancelled() {
        return cancelled != 0;
    }

    private void cancelAll() {
        drainSingleConsumerCollectionDelayThrow(cancellables.values(), Cancellable::cancel, drainingUpdater, this);
    }
}
