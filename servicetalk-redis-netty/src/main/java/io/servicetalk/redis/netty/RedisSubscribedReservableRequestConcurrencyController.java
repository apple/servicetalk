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
package io.servicetalk.redis.netty;

import io.servicetalk.client.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.client.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.internal.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.internal.RequestConcurrencyController.Result.RejectedTemporary;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class RedisSubscribedReservableRequestConcurrencyController implements ReservableRequestConcurrencyController {
    private static final AtomicIntegerFieldUpdater<RedisSubscribedReservableRequestConcurrencyController>
        requestSeenUpdater = newUpdater(RedisSubscribedReservableRequestConcurrencyController.class, "requestState");

    private static final int STATE_QUIT = -2;
    private static final int STATE_RESERVED = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_REQUEST_ONLY = 1;

    @SuppressWarnings("unused")
    private volatile int requestState;

    @Override
    public boolean tryReserve() {
        return requestSeenUpdater.compareAndSet(this, STATE_IDLE, STATE_RESERVED);
    }

    @Override
    public Completable releaseAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                if (requestSeenUpdater.compareAndSet(RedisSubscribedReservableRequestConcurrencyController.this,
                        STATE_RESERVED, STATE_QUIT)) {
                    subscriber.onComplete();
                } else {
                    subscriber.onError(new IllegalStateException("Resource " + this +
                            (requestState == STATE_QUIT ? " is closed." : " was not reserved.")));
                }
            }
        };
    }

    @Override
    public Result tryRequest() {
        if (requestState == STATE_QUIT) {
            return RejectedPermanently;
        }
        if (requestState == STATE_REQUEST_ONLY ||
                requestSeenUpdater.compareAndSet(this, STATE_IDLE, STATE_REQUEST_ONLY)) {
            return Accepted;
        }
        return RejectedTemporary;
    }

    @Override
    public void requestFinished() {
        // When a connection is used for "subscribe mode" [1] the wire protocol changes and we don't allow the
        // connection to be re-used after this time. This is a special case where once a single subscribe is seen
        // (any command on a connection in "subscribe mode") we mark it ineligible for reservation, but we don't
        // count/limit requests.
        // [1] https://redis.io/commands/subscribe
    }
}
