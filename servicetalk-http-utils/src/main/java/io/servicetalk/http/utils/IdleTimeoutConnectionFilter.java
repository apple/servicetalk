/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.RequestRejectedException;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A connection-level filter that closes idle connections.
 * <p>
 * This filter is an alternative to {@link ServiceTalkSocketOptions#IDLE_TIMEOUT} at L7 layer. It helps to close idle
 * connections that were not used to send any requests for the specified duration without affecting any in-flight
 * requests.
 * <ul>
 *     <li>Connections that have in-flight requests are considered "in-use".</li>
 *     <li>If response payload body was not consumed, the connection is still considered "in-use" and does not start
 *     counting the timer.</li>
 *     <li>A single connection can not process more than {@link Integer#MAX_VALUE} concurrent requests.</li>
 *     <li>If filter is configured with {@link Duration#ZERO} timeout, it disables the timeout.</li>
 * </ul>
 */
public final class IdleTimeoutConnectionFilter implements StreamingHttpConnectionFilterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleTimeoutConnectionFilter.class);

    private static final Cancellable CANCELLED = () -> { };

    private final long timeoutNs;
    @Nullable
    private final Executor timeoutExecutor;

    /**
     * Creates a new instance.
     *
     * @param timeout timeout duration after which an idle connection is closed, if {@link Duration#ZERO} the timeout is
     * disabled
     */
    public IdleTimeoutConnectionFilter(final Duration timeout) {
        this.timeoutNs = ensureNotNegative(timeout).toNanos();
        this.timeoutExecutor = null;
    }

    /**
     * Creates a new instance.
     *
     * @param timeout timeout duration after which an idle connection is closed, if {@link Duration#ZERO} the timeout is
     * disabled
     * @param timeoutExecutor the {@link Executor} to use for scheduling the timer notifications
     */
    public IdleTimeoutConnectionFilter(final Duration timeout, final Executor timeoutExecutor) {
        this.timeoutNs = ensureNotNegative(timeout).toNanos();
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    private static Duration ensureNotNegative(final Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Negative timeout: " + timeout.toNanos() + " ns (expected: >=0)");
        }
        return timeout;
    }

    private static Executor contextExecutor(ExecutionContext<HttpExecutionStrategy> context) {
        return context.executionStrategy().hasOffloads() ? context.executor() : context.ioExecutor();
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        if (timeoutNs == 0) {
            return new DisabledIdleTimeoutConnectionFilter(connection);
        }
        return new IdleTimeoutConnectionFilterImpl(connection, timeoutNs,
                timeoutExecutor != null ? timeoutExecutor : contextExecutor(connection.executionContext()));
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    @Override
    public String toString() {
        return getClass().getName() +
                "{timeoutNs=" + timeoutNs +
                ", timeoutExecutor=" + timeoutExecutor +
                '}';
    }

    private static final class IdleTimeoutConnectionFilterImpl extends StreamingHttpConnectionFilter
            implements Runnable {

        private static final AtomicIntegerFieldUpdater<IdleTimeoutConnectionFilterImpl> requestsUpdater =
                AtomicIntegerFieldUpdater.newUpdater(IdleTimeoutConnectionFilterImpl.class, "requests");
        private static final AtomicReferenceFieldUpdater<IdleTimeoutConnectionFilterImpl, Cancellable>
                timeoutTaskUpdater = AtomicReferenceFieldUpdater.newUpdater(IdleTimeoutConnectionFilterImpl.class,
                Cancellable.class, "timeoutTask");

        private volatile int requests;
        @Nullable
        private volatile Cancellable timeoutTask;

        private final long timeoutNs;
        private final Executor timeoutExecutor;

        // The "volatile" here is not for general visibility but to prevent non-atomic treatment of long:
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.7
        private volatile long lastResponseTime;

        IdleTimeoutConnectionFilterImpl(final FilterableStreamingHttpConnection connection,
                                        final long timeoutNs,
                                        final Executor timeoutExecutor) {
            super(connection);
            this.timeoutNs = timeoutNs;
            this.timeoutExecutor = timeoutExecutor;
            connection.onClose().whenFinally(this::cancelTask).subscribe();
            this.lastResponseTime = nanoTime();
            timeoutTask = this.timeoutExecutor.schedule(this, timeoutNs, NANOSECONDS);
        }

        private long nanoTime() {
            return timeoutExecutor.currentTime(NANOSECONDS);
        }

        private void cancelTask() {
            final Cancellable oldTask = timeoutTaskUpdater.getAndSet(this, CANCELLED);
            if (oldTask != null) {
                oldTask.cancel();
            }
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return defer(() -> {
                final int prevInFlightRequests = requestsUpdater.getAndAccumulate(this, 1,
                        FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                if (prevInFlightRequests < 0) {
                    return failed(new RetryableClosedChannelException(delegate(), timeoutNs));
                }
                if (prevInFlightRequests == MAX_VALUE) {
                    return failed(new RequestRejectedException("Connection " + delegate() +
                            " already processes Integer.MAX_VALUE other requests, it can not process more."));
                }
                return delegate().request(request)
                        .liftSync(new BeforeFinallyHttpOperator(() -> {
                            final int remainingRequests = requestsUpdater.decrementAndGet(this);
                            assert remainingRequests >= 0 : "Unexpected remaining requests value: " + remainingRequests;
                            // It's acceptable to use 2 volatile variables instead of a single object state here. Even
                            // if 2 threads race between updating "requests" and "lastResponseTime", the delay for a new
                            // timer task will be close to "timeoutNs", the difference is negligible.
                            if (remainingRequests == 0) {
                                lastResponseTime = nanoTime();
                            }
                        })).shareContextOnSubscribe();
            });
        }

        private void updateIdleTimeout(final long delayNs) {
            final Cancellable newTask = timeoutExecutor.schedule(this, delayNs, NANOSECONDS);
            if (!timeoutTaskUpdater.compareAndSet(this, null, newTask)) {
                assert timeoutTask == CANCELLED : "Unexpected timeoutTask: " + timeoutTask;
                newTask.cancel();    // Connection was closed, cancel the new task
            }
        }

        @Override
        public void run() {
            final Cancellable oldTask = timeoutTaskUpdater.getAndSet(this, null);
            if (oldTask == CANCELLED) {
                // Connection already closed
                return;
            }
            for (;;) {
                final long requests = this.requests;
                if (requests > 0) {
                    // Reschedule timeout:
                    updateIdleTimeout(timeoutNs);
                    return;
                } else if (requests == 0) {
                    final long nextDelayNs = timeoutNs - (nanoTime() - lastResponseTime);
                    if (nextDelayNs <= 0) {
                        if (requestsUpdater.compareAndSet(this, 0, MIN_VALUE)) {
                            FilterableStreamingHttpConnection connection = delegate();
                            LOGGER.debug("Closing connection {} after {} ms of inactivity",
                                    connection, NANOSECONDS.toMillis(timeoutNs));
                            connection.closeAsync().subscribe();
                            return;
                        }
                    } else {
                        updateIdleTimeout(nextDelayNs);
                        return;
                    }
                } else {
                    // Should never happen. Keep it just in case to prevent infinite loop.
                    LOGGER.warn("{} Unexpected concurrent requests value {}", delegate(), requests);
                    return;
                }
            }
        }
        @Override
        public String toString() {
            return getClass().getSimpleName() + '[' + NANOSECONDS.toMillis(timeoutNs) + " ms](" + delegate() + ')';
        }
    }

    private static final class DisabledIdleTimeoutConnectionFilter extends StreamingHttpConnectionFilter {
        DisabledIdleTimeoutConnectionFilter(final FilterableStreamingHttpConnection delegate) {
            super(delegate);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '(' + delegate() + ')';
        }
    }

    private static final class RetryableClosedChannelException extends ClosedChannelException
            implements RetryableException {
        private static final long serialVersionUID = 5678979395131901139L;
        private final String message;

        RetryableClosedChannelException(final FilterableStreamingHttpConnection connection, final long timeoutNs) {
            this.message = "Connection " + connection + " was closed due to " +
                    NANOSECONDS.toMillis(timeoutNs) + " ms of inactivity";
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
