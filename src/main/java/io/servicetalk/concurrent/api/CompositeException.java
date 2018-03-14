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

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.drainSingleConsumerQueueDelayThrow;
import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedMpscQueue;

/**
 * A {@link RuntimeException} that allows to add {@link Throwable} instances at a lower cost than {@link #addSuppressed(Throwable)}.
 * {@link #addAllPendingSuppressed()} will add all pending {@link Throwable}s as {@link #addSuppressed(Throwable)}.
 */
final class CompositeException extends RuntimeException {
    private static final long serialVersionUID = 7827495486030277692L;

    private final Queue<Throwable> suppressed = newUnboundedMpscQueue(4);

    private static final AtomicIntegerFieldUpdater<CompositeException> drainingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(CompositeException.class, "draining");
    @SuppressWarnings("unused") private volatile int draining;

    /**
     * New instance.
     *
     * @param cause of the exception.
     */
    CompositeException(Throwable cause) {
        super(cause);
    }

    /**
     * Add a {@link Throwable} to be added as {@link #addSuppressed(Throwable)} on the next call to {@link #addAllPendingSuppressed()}.
     *
     * @param toAdd {@link Throwable} to finally add as {@link #addSuppressed(Throwable)}.
     */
    void add(Throwable toAdd) {
        if (!suppressed.offer(toAdd)) {
            addSuppressed(toAdd);
        }
    }

    /**
     * Adds all {@link Throwable}s added using {@link #add(Throwable)} to this {@link CompositeException} using {@link #addSuppressed(Throwable)}.
     */
    void addAllPendingSuppressed() {
        drainSingleConsumerQueueDelayThrow(suppressed, this::addSuppressed, drainingUpdater, this);
    }
}
