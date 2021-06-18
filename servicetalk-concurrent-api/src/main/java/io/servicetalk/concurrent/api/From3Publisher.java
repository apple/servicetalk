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

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

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
        private static final byte INIT = 0;
        private static final byte DELIVERED_V1 = 1;
        private static final byte DELIVERED_V2 = 2;
        private static final byte CANCELLED = 3;
        private static final byte TERMINATED = 4;
        private byte state;
        private final Subscriber<? super T> subscriber;

        private ThreeValueSubscription(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void cancel() {
            if (state != TERMINATED) {
                state = CANCELLED;
            }
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
            if (state == INIT) {
                state = DELIVERED_V1;
                try {
                    subscriber.onNext(v1);
                } catch (Throwable cause) {
                    state = TERMINATED;
                    subscriber.onError(cause);
                    return;
                }
                // We could check CANCELLED here and return, but it isn't required.
                if (n > 2) {
                    deliverV2V3();
                } else if (n == 2) {
                    deliverV2();
                }
            } else if (state == DELIVERED_V1) {
                if (n > 1) {
                    deliverV2V3();
                } else {
                    deliverV2();
                }
            } else if (state == DELIVERED_V2) {
                state = TERMINATED;
                try {
                    subscriber.onNext(v3);
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onComplete();
            }
        }

        private void deliverV2() {
            state = DELIVERED_V2;
            try {
                subscriber.onNext(v2);
            } catch (Throwable cause) {
                state = TERMINATED;
                subscriber.onError(cause);
            }
        }

        private void deliverV2V3() {
            state = TERMINATED;
            try {
                subscriber.onNext(v2);
                // We could check CANCELLED here and return, but it isn't required.
                subscriber.onNext(v3);
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }
    }
}
