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
package io.servicetalk.redis.api;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * Represents a value that will be set after some operation completes. If the operation completes with an exception,
 * that exception will be propogated through {@code Deferred}.
 *
 * @param <T> the type of the value.
 */
public final class Deferred<T> {
    private static final Object UNSET = new Object();

    @Nullable
    private volatile Object value = UNSET;

    void onSuccess(@Nullable T value) {
        synchronized (this) {
            this.value = value;
            this.notifyAll();
        }
    }

    void onError(Throwable cause) {
        synchronized (this) {
            this.value = requireNonNull(cause);
            this.notifyAll();
        }
    }

    /**
     * Returns the value of the operation if it has been set, or throws an exception if the operation completed with an
     * exception. Does not block.
     *
     * @return the value.
     * @throws IllegalStateException if the value has not yet been set.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T get() {
        if (value != UNSET) {
            if (value instanceof Throwable) {
                // value is never set back to null once it's been set to non-null, but
                // requireNonNull to satisfy static analysis warnings.
                throwException((Throwable) requireNonNull(value));
            }
            return (T) value;
        }
        throw new IllegalStateException("Not yet set");
    }

    @SuppressWarnings("unchecked")
    @Nullable
    <T2> T2 blockingGet() {
        synchronized (this) {
            while (value == UNSET) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return (T2) get();
        }
    }
}
