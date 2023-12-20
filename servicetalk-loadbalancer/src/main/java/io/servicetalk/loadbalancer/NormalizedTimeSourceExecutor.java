/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.Executor;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An {@link Executor} that always starts counting {@link #currentTime(TimeUnit)} from {@code 0}.
 */
final class NormalizedTimeSourceExecutor extends DelegatingExecutor {

    private final long offsetNanos;

    NormalizedTimeSourceExecutor(final Executor delegate) {
        super(delegate);
        offsetNanos = delegate.currentTime(NANOSECONDS);
    }

    @Override
    public long currentTime(final TimeUnit unit) {
        final long elapsedNanos = delegate().currentTime(NANOSECONDS) - offsetNanos;
        return unit.convert(elapsedNanos, NANOSECONDS);
    }
}
