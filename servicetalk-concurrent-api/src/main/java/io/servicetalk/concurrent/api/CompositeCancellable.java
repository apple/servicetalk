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
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Cancellable} that cancels multiple {@link Cancellable} instances when it is cancelled.
 */
final class CompositeCancellable implements Cancellable {
    @Nullable
    private final Cancellable[] others;
    @Nullable
    private final Cancellable first;
    @Nullable
    private final Cancellable second;
    @SuppressWarnings("unused")
    private volatile int cancelled;

    private static final AtomicIntegerFieldUpdater<CompositeCancellable> cancelledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(CompositeCancellable.class, "cancelled");

    /**
     * New instance.
     *
     * @param others All {@link Cancellable}s to compose.
     */
    private CompositeCancellable(Cancellable... others) {
        if (others.length == 2) {
            first = requireNonNull(others[0]);
            second = requireNonNull(others[1]);
            this.others = null;
        } else {
            this.others = others;
            this.first = null;
            this.second = null;
        }
    }

    @Override
    public void cancel() {
        if (cancelledUpdater.compareAndSet(this, 0, 1)) {
            if (others == null) {
                try {
                    //noinspection ConstantConditions
                    first.cancel();
                } finally {
                    //noinspection ConstantConditions
                    second.cancel();
                }
            } else {
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
                            t.addSuppressed(tt);
                        }
                    }
                }
                if (t != null) {
                    throwException(t);
                }
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
                return toCompose[0];
            default:
                return new CompositeCancellable(toCompose);
        }
    }
}
