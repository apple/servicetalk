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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Cancellable} that cancels multiple {@link Cancellable} instances when it is cancelled.
 */
final class CompositeCancellable implements Cancellable {
    private static final AtomicIntegerFieldUpdater<CompositeCancellable> cancelledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(CompositeCancellable.class, "cancelled");

    private final Cancellable[] others;
    @SuppressWarnings("unused")
    private volatile int cancelled;

    /**
     * New instance.
     *
     * @param others All {@link Cancellable}s to compose.
     */
    private CompositeCancellable(Cancellable... others) {
        assert others.length > 2;
        this.others = requireNonNull(others);
    }

    @Override
    public void cancel() {
        if (cancelledUpdater.compareAndSet(this, 0, 1)) {
            Throwable t = null;
            for (Cancellable other : others) {
                try {
                    if (other != null) {
                        other.cancel();
                    }
                } catch (Throwable tt) {
                    if (t == null) {
                        t = tt;
                    } else {
                        addSuppressed(t, tt);
                    }
                }
            }
            if (t != null) {
                throwException(t);
            }
        }
    }

    /**
     * Creates new instance of {@link Cancellable} which is a composite of {@code toCompose}.
     *
     * @param toCompose All {@link Cancellable} to compose.
     * @return A composite {@link Cancellable} for all {@code toCompose}.
     */
    static Cancellable create(Cancellable... toCompose) {
        switch (toCompose.length) {
            case 0:
                throw new IllegalArgumentException("At least one Cancellable required to compose.");
            case 1:
                return requireNonNull(toCompose[0]);
            case 2:
                Cancellable first = requireNonNull(toCompose[0]);
                Cancellable second = requireNonNull(toCompose[1]);
                return () -> {
                    try {
                        first.cancel();
                    } finally {
                        second.cancel();
                    }
                };
            default:
                return new CompositeCancellable(toCompose);
        }
    }
}
