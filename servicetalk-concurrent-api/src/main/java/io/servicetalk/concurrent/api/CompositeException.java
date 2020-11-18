/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A {@link RuntimeException} that allows to add {@link Throwable} instances at a lower cost than
 * {@link #addSuppressed(Throwable)}. {@link #transferPendingToSuppressed()} will add all pending {@link Throwable}s as
 * {@link #addSuppressed(Throwable)}.
 */
final class CompositeException extends RuntimeException {
    private static final int DEFAULT_MAX_EXCEPTIONS = 32;
    private static final long serialVersionUID = 7827495486030277692L;
    private static final AtomicIntegerFieldUpdater<CompositeException> sizeUpdater =
            newUpdater(CompositeException.class, "size");
    private final Queue<Throwable> suppressed = new ConcurrentLinkedQueue<>();
    private final int maxExceptions;
    private volatile int size;

    /**
     * New instance.
     *
     * @param cause of the exception.
     * @param maxExceptions the limit on the number of {@link #add(Throwable)} that will be queued.
     */
    CompositeException(Throwable cause, int maxExceptions) {
        super(cause);
        if (maxExceptions <= 0) {
            throw new IllegalArgumentException("maxExceptions: " + maxExceptions + " (expected >0)");
        }
        this.maxExceptions = maxExceptions;
    }

    /**
     * Add a {@link Throwable} to be added as {@link #addSuppressed(Throwable)} on the next call to
     * {@link #transferPendingToSuppressed()}.
     *
     * @param toAdd {@link Throwable} to finally add as {@link #addSuppressed(Throwable)}.
     */
    void add(Throwable toAdd) {
        // optimistically increment, recover after the fact if necessary.
        final int newSize = sizeUpdater.incrementAndGet(this);
        if (newSize < 0) {
            size = Integer.MAX_VALUE;
        } else if (newSize <= maxExceptions) {
            suppressed.offer(toAdd);
            // if addAllPendingSuppressed has already been called don't bother trying to synchronize/drain the queue
            // as it is assumed the exception will be thrown after that method is called.
        } else {
            sizeUpdater.decrementAndGet(this);
        }
    }

    /**
     * Adds all {@link Throwable}s added using {@link #add(Throwable)} to this {@link CompositeException} using
     * {@link #addSuppressed(Throwable)}.
     * <p>
     * It is assumed that {@link #add(Throwable)} won't be called after this method.
     */
    void transferPendingToSuppressed() {
        size = Integer.MAX_VALUE; // Disable adding further exceptions
        Throwable delayedCause = null;
        Throwable next;
        while ((next = suppressed.poll()) != null) {
            try {
                addSuppressed(next);
            } catch (Throwable cause) {
                delayedCause = catchUnexpected(delayedCause, cause);
            }
        }
        if (delayedCause != null) {
            throwException(delayedCause);
        }
    }

    static int maxDelayedErrors(boolean delayError) {
        return delayError ? DEFAULT_MAX_EXCEPTIONS : 0;
    }
}
