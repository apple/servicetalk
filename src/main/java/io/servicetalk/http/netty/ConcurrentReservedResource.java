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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Facilitate state management for {@link LoadBalancer} managed resources that allow {@link LoadBalancer} reservation
 * and concurrent requests.
 * <p>
 *  Largely modeled after {@code io.servicetalk.redis.netty.LoadBalancedRedisConnection}.
 */
class ConcurrentReservedResource {

    private static final AtomicIntegerFieldUpdater<ConcurrentReservedResource> pendingRequestsUpdater =
            newUpdater(ConcurrentReservedResource.class, "pendingRequests");

    private static final int STATE_QUIT = -2;
    private static final int STATE_RESERVED = -1;
    private static final int STATE_IDLE = 0;

    /*
     * Following semantics:
     * STATE_RESERVED if this is reserved.
     * STATE_QUIT if quit command issued.
     * STATE_IDLE if connection is not used.
     * pending request count if none of the above states.
     */
    @SuppressWarnings("unused")
    private volatile int pendingRequests;

    private final LatestValueSubscriber<Integer> maxConcurrencyHolder;

    ConcurrentReservedResource(final Publisher<Integer> maxConcurrencySettingStream,
                               final ListenableAsyncCloseable closeable) {
        maxConcurrencyHolder = new LatestValueSubscriber<>();
        maxConcurrencySettingStream.subscribe(maxConcurrencyHolder);
        closeable.onClose().subscribe(new Completable.Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // No op
            }

            @Override
            public void onComplete() {
                pendingRequests = STATE_QUIT;
            }

            @Override
            public void onError(Throwable ignored) {
                pendingRequests = STATE_QUIT;
            }
        });
    }

    /**
     * Expected to be called before reserving a connection, needs to be followed by a {@link #release()} to return the
     * connection to the load balancer.
     * @return {@code true} if this connection is available and reserved for the caller
     */
    boolean tryReserve() {
        return pendingRequestsUpdater.compareAndSet(this, STATE_IDLE, STATE_RESERVED);
    }

    /**
     * This reserves this connection for a (concurrent) request, needs to be followed by {@link #requestFinished()}
     * to return the connection to the load balancer.
     * <p>
     * Allows up to {@link #maxConcurrencyHolder} concurrent requests, eg when pipelining.
     * @return {@code true} if this connection is available and reserved for performing a single request
     */
    boolean tryRequest() {
        final int lastSeenValue = maxConcurrencyHolder.getLastSeenValue(STATE_IDLE);
        for (;;) {
            final int currentPending = pendingRequests;
            if (currentPending < STATE_IDLE || currentPending >= lastSeenValue) {
                return false;
            }
            if (pendingRequestsUpdater.compareAndSet(this, currentPending, currentPending + 1)) {
                return true;
            }
        }
    }

    /**
     * Expected to be called from a {@link Publisher#doBeforeFinally(Runnable)} after a {@link #tryReserve()}.
     */
    void requestFinished() {
        pendingRequestsUpdater.accumulateAndGet(this, -1, FlowControlUtil::addWithOverflowProtectionIfPositive);
    }

    /**
     * Returns a reserved connection from {@link #tryReserve()} back to the load balancer.
     * @return a {@link Completable} for the release
     */
    Completable release() {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                // Ownership is maintained by the caller.
                if (pendingRequestsUpdater.compareAndSet(ConcurrentReservedResource.this, STATE_RESERVED, STATE_IDLE)) {
                    subscriber.onComplete();
                } else {
                    subscriber.onError(new IllegalStateException("Resource " + this +
                            (pendingRequests == STATE_QUIT ? " is closed." : " was not reserved.")));
                }
            }
        };
    }
}
