/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Math.min;

final class FromNPublisher<T> extends AbstractSynchronousPublisher<T> {

    private static final Object UNUSED_REF = new Object();

    @Nullable
    private final T v1;
    @Nullable
    private final T v2;
    @Nullable
    private final T v3;

    @SuppressWarnings("unchecked")
    FromNPublisher(@Nullable T v1, @Nullable T v2) {
        this.v1 = (T) UNUSED_REF;
        this.v2 = v1;
        this.v3 = v2;
    }

    FromNPublisher(@Nullable T v1, @Nullable T v2, @Nullable T v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(new NValueSubscription(subscriber));
        } catch (Throwable cause) {
            handleExceptionFromOnSubscribe(subscriber, cause);
        }
    }

    private final class NValueSubscription implements Subscription {
        private static final byte ZERO = 0;
        private static final byte ONE = 1;
        private static final byte TWO = 2;
        private static final byte TERMINATED = 3;
        private byte requested;
        private byte state;
        private final Subscriber<? super T> subscriber;

        private NValueSubscription(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
            if (v1 == UNUSED_REF) {
                // 2-value version - simulate 1 emitted item, start counting from 1.
                requested = 1;
                state = ONE;
            }
        }

        @Override
        public void cancel() {
            state = TERMINATED;
        }

        @Override
        public void request(final long n) {
            if (state() == TERMINATED) {
                return;
            }
            if (!isRequestNValid(n)) {
                state = TERMINATED;
                subscriber.onError(newExceptionForInvalidRequestN(n));
                return;
            }
            if (requested == 3) {
                return;
            }
            requested = (byte) min(3, addWithOverflowProtection(requested, n));
            if (ignoreRequests()) {
                return;
            }
            ignoreRequests(true);
            while (state() < requested) {
                if (state() == ZERO) {
                    deliver(v1, ONE);
                } else if (state() == ONE) {
                    deliver(v2, TWO);
                } else if (state() == TWO) {
                    if (deliver(v3, TERMINATED)) {
                        subscriber.onComplete();
                    }
                    return;
                }
            }
            ignoreRequests(false);
        }

        private boolean deliver(@Nullable final T value, final byte nextState) {
            state = (byte) ((state & 0x10) | nextState);
            try {
                subscriber.onNext(value);
                return true;
            } catch (Throwable cause) {
                state = TERMINATED;
                subscriber.onError(cause);
                return false;
            }
        }

        private byte state() {
            return (byte) (state & 0x0F);
        }

        private boolean ignoreRequests() {
            return (state & 0x10) > 0;
        }

        private void ignoreRequests(final boolean ignore) {
            if (ignore) {
                state |= 0x10;
            } else {
                state &= 0x0F;
            }
        }
    }
}
