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

final class From3Publisher<T> extends AbstractSynchronousPublisher<T> {
    @Nullable
    private final T v1;
    @Nullable
    private final T v2;
    @Nullable
    private final T v3;

    From3Publisher(@Nullable T v1, @Nullable T v2, @Nullable T v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(new ThreeValueSubscription(subscriber));
        } catch (Throwable cause) {
            handleExceptionFromOnSubscribe(subscriber, cause);
        }
    }

    private final class ThreeValueSubscription implements Subscription {
        private final int TERMINATED = -1;
        private long requested;
        private int state;
        private final Subscriber<? super T> subscriber;

        private ThreeValueSubscription(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void cancel() {
            state = TERMINATED;
        }

        @Override
        public void request(final long n) {
            if (state == TERMINATED) {
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
            requested = min(3, addWithOverflowProtection(requested, n));
            while (state < requested) {
                if (state == 0) {
                    deliver(v1, 1);
                } else if (state == 1) {
                    deliver(v2, 2);
                } else if (state == 2) {
                    if (deliver(v3, 3)) {
                        subscriber.onComplete();
                    }
                } else {
                    // Cancelled
                    break;
                }
            }
        }

        private boolean deliver(@Nullable T value, int newState) {
            state = newState;
            try {
                subscriber.onNext(value);
                return true;
            } catch (Throwable cause) {
                state = TERMINATED;
                subscriber.onError(cause);
            }
            return false;
        }
    }
}
