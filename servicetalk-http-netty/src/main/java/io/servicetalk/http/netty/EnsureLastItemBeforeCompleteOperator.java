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

import io.servicetalk.concurrent.api.PublisherOperator;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Objects.requireNonNull;

final class EnsureLastItemBeforeCompleteOperator<T> implements PublisherOperator<T, T> {
    private final Predicate<T> terminalPredicate;
    private final Supplier<T> lastTerminalSupplier;

    EnsureLastItemBeforeCompleteOperator(Predicate<T> terminalPredicate,
                                         Supplier<T> lastTerminalSupplier) {
        this.terminalPredicate = requireNonNull(terminalPredicate);
        this.lastTerminalSupplier = requireNonNull(lastTerminalSupplier);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new RequireLastItemSubscriber<>(this, subscriber);
    }

    private static final class RequireLastItemSubscriber<T> implements Subscriber<T> {
        private static final long WAITING_TO_SEND_LAST_ITEM = Long.MIN_VALUE;
        private static final long TERMINATED = Long.MIN_VALUE + 1;
        private static final AtomicLongFieldUpdater<RequireLastItemSubscriber> requestNUpdater =
                AtomicLongFieldUpdater.newUpdater(RequireLastItemSubscriber.class, "requestN");
        private final EnsureLastItemBeforeCompleteOperator<T> lastItemOperator;
        private final Subscriber<? super T> target;
        private boolean seenTerminal;
        @SuppressWarnings("unused")
        private volatile long requestN;

        RequireLastItemSubscriber(EnsureLastItemBeforeCompleteOperator<T> lastItemOperator,
                                  Subscriber<? super T> target) {
            this.lastItemOperator = lastItemOperator;
            this.target = target;
        }

        @Override
        public void onSubscribe(Subscription s) {
            target.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    if (isRequestNValid(n)) {
                        for (;;) {
                            final long currentRequestN = requestN;
                            if (currentRequestN == WAITING_TO_SEND_LAST_ITEM) {
                                if (requestNUpdater.compareAndSet(RequireLastItemSubscriber.this,
                                        WAITING_TO_SEND_LAST_ITEM, TERMINATED)) {
                                    insertOnNextAndComplete();
                                }
                                // else - if we failed the CAS then the terminal was already delivered and the state
                                // is now TERMINATED, so no need to check again.
                                // We could forward the requestN to the upstream Subscription but there is a risk we
                                // may violate 2.3 of the RS spec [1]. So we just bail out early because the Subscriber
                                // is already terminated and this is expected to be a noop.
                                // [1] https://github.com/reactive-streams/reactive-streams-jvm#2.3
                                return;
                            } else if (currentRequestN == TERMINATED) {
                                // We could forward the requestN to the upstream Subscription but there is a risk we
                                // may violate 2.3 of the RS spec [1]. So we just bail out early because the Subscriber
                                // is already terminated and this is expected to be a noop.
                                // [1] https://github.com/reactive-streams/reactive-streams-jvm#2.3
                                return;
                            } else if (requestNUpdater.compareAndSet(RequireLastItemSubscriber.this,
                                    currentRequestN, addWithOverflowProtection(currentRequestN, n))) {
                                break;
                            }
                        }
                    }
                    s.request(n);
                }

                @Override
                public void cancel() {
                    // We maybe producing onNext to the downstream subscriber as a result of a upstream onComplete.
                    // In this scenario we should not be interacting with the upstream Subscription [1].
                    // Here is the scenario, assume terminalPredicate never returns true.
                    // request(1)
                    // onComplete()
                    // onNext(lastItemOperator.lastTerminalSupplier.get()) // synthesized by this operator
                    // cancel() // from within onNext ... this is called from onComplete!
                    // [1] https://github.com/reactive-streams/reactive-streams-jvm#2.3
                    if (requestN != TERMINATED) {
                        s.cancel();
                    }
                }
            });
        }

        @Override
        public void onNext(T t) {
            // Update state before delivering onNext in case requestN ends up calling onComplete.
            requestNUpdater.decrementAndGet(this);
            seenTerminal |= lastItemOperator.terminalPredicate.test(t);
            target.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }

        @Override
        public void onComplete() {
            if (seenTerminal) {
                target.onComplete();
            } else
                // We first try to mark the requestN as "waiting" and the current value is > 0 then we also try to
                // terminate. This way if there is no demand now, we notify the Subscription thread to terminate when
                // more demand comes.
                if (requestNUpdater.getAndSet(this, WAITING_TO_SEND_LAST_ITEM) > 0 &&
                        requestNUpdater.compareAndSet(this, WAITING_TO_SEND_LAST_ITEM, TERMINATED)) {
                    insertOnNextAndComplete();
                }
        }

        private void insertOnNextAndComplete() {
            try {
                // Note that we actually want to catch exceptions from onNext because we are synthesizing an onNext
                // when the outer Subscriber has already been completed. Otherwise if we let the exception propagate
                // here the target will never be terminated.
                target.onNext(lastItemOperator.lastTerminalSupplier.get());
            } catch (Throwable cause) {
                target.onError(new IllegalStateException("unsupported exception", cause));
                return;
            }
            target.onComplete();
        }
    }
}
