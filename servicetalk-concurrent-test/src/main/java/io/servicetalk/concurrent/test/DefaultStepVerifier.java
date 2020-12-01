/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.test;

import io.servicetalk.concurrent.Executor;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.lang.System.nanoTime;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

abstract class DefaultStepVerifier<S, T> implements StepVerifier {
    private final S source;
    private final Queue<Event<T>> events;
    private final String exceptionClassNamePrefix;

    DefaultStepVerifier(final S source, final Queue<Event<T>> events, final String exceptionClassNamePrefix) {
        this.source = source;
        this.events = events;
        this.exceptionClassNamePrefix = exceptionClassNamePrefix;
    }

    abstract T doSubscribe(S source);

    abstract T doSubscribe(S source, Duration duration, Executor executor);

    @Override
    public final Duration verify() throws AssertionError {
        return verify(() -> doSubscribe(source), copyEventQueue());
    }

    private Duration verify(Supplier<T> tSupplier, Queue<Event<T>> events) throws AssertionError {
        final long startTimeNs = nanoTime();
        T subscriber = tSupplier.get();
        Event<T> event;
        while ((event = events.poll()) != null) {
            try {
                event.verify(subscriber);
            } catch (Throwable cause) {
                throw event.newException(cause.getMessage(), cause, exceptionClassNamePrefix);
            }
        }

        return ofNanos(nanoTime() - startTimeNs);
    }

    @Override
    public final Duration verify(final Duration duration) throws AssertionError {
        return verify(duration, copyEventQueue());
    }

    private Duration verify(final Duration duration, Queue<Event<T>> events) throws AssertionError {
        final long startTime = nanoTime();
        final long timeoutNanos = duration.toNanos();
        long waitTime = timeoutNanos;
        T subscriber = doSubscribe(source);
        Event<T> event;
        while ((event = events.poll()) != null) {
            try {
                if (waitTime <= 0) {
                    throw new TimeoutException("timeout after " + duration);
                }
                event.verify(subscriber, waitTime, NANOSECONDS);
                waitTime = timeoutNanos - (nanoTime() - startTime);
            } catch (Throwable cause) {
                throw event.newException(cause.getMessage(), cause, exceptionClassNamePrefix);
            }
        }

        return ofNanos(nanoTime() - timeoutNanos);
    }

    @Override
    public final Duration verify(Duration duration, Executor executor) throws AssertionError {
        return verify(() -> doSubscribe(source, duration, executor), copyEventQueue());
    }

    private Queue<Event<T>> copyEventQueue() {
        return new ArrayDeque<>(events);
    }
}
