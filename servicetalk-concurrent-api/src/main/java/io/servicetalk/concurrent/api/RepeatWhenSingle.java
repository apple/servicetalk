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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.ThrowableUtils.unknownStackTrace;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

final class RepeatWhenSingle<T> extends AbstractNoHandleSubscribePublisher<T> {
    static final Exception END_REPEAT_EXCEPTION = unknownStackTrace(new Exception(), RepeatWhenSingle.class, "<init>");
    private final Single<T> original;
    private final BiIntFunction<? super T, ? extends Completable> shouldRedo;

    RepeatWhenSingle(final Single<T> original, final BiIntFunction<? super T, ? extends Completable> shouldRedo) {
        this.original = original;
        this.shouldRedo = requireNonNull(shouldRedo);
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber,
                         ContextMap contextMap, AsyncContextProvider contextProvider) {
        try {
            subscriber.onSubscribe(new RedoSubscription<>(this, subscriber, contextMap, contextProvider));
        } catch (Throwable cause) {
            handleExceptionFromOnSubscribe(subscriber, cause);
        }
    }

    private static final class RedoSubscription<T> implements Subscription {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<RedoSubscription> outstandingDemandUpdater =
                AtomicLongFieldUpdater.newUpdater(RedoSubscription.class, "outstandingDemand");
        private static final long TERMINATED = Long.MIN_VALUE;
        private static final long CANCELLED = TERMINATED + 1;
        private static final long MIN_INVALID_N = CANCELLED + 1;
        private final RepeatWhenSingle<T> outer;
        private final SequentialCancellable sequentialCancellable = new SequentialCancellable();
        private final Subscriber<? super T> subscriber;
        private final ContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private volatile long outstandingDemand;
        private int redoCount;

        private RedoSubscription(final RepeatWhenSingle<T> outer, final Subscriber<? super T> subscriber,
                                 final ContextMap contextMap, final AsyncContextProvider contextProvider) {
            this.outer = outer;
            this.subscriber = subscriber;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void request(final long n) {
            if (isRequestNValid(n)) {
                final long prev = outstandingDemandUpdater.getAndAccumulate(this, n,
                        FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                if (prev == 0) {
                    outer.original.delegateSubscribe(new RedoSubscriber(), contextMap, contextProvider);
                }
            } else {
                requestNInvalid(n);
            }
        }

        private void requestNInvalid(final long n) {
            for (;;) {
                final long prev = outstandingDemand;
                if (prev == TERMINATED) {
                    break;
                } else if (prev == 0) {
                    if (outstandingDemandUpdater.compareAndSet(this, prev, TERMINATED)) {
                        subscriber.onError(newExceptionForInvalidRequestN(n));
                    }
                } else if (outstandingDemandUpdater.compareAndSet(this, prev, sanitize(n))) {
                    // if cancelled, we may not deliver an error. in this case we don't know if another thread may
                    // interact with the Subscriber and concurrency control would be more complex, since spec doesn't
                    // require delivery after cancel we drop it.
                    break; // hand-off to other thread to deliver the terminal.
                }
            }
        }

        private static long sanitize(final long n) {
            // The value must be negative because 0 is used to determine if there is no demand and a subscribe should
            // be done. It can't overlap with our token TERMINAL or CANCEL values either.
            return n == 0 ? -1 : max(n, MIN_INVALID_N);
        }

        @Override
        public void cancel() {
            for (;;) {
                final long prev = outstandingDemand;
                if (prev < 0 || outstandingDemandUpdater.compareAndSet(this, prev, CANCELLED)) {
                    // prev < 0 means either we are already terminated/cancelled, or there is invalid demand pending.
                    break;
                }
            }
            sequentialCancellable.cancel();
        }

        private final class RedoSubscriber implements SingleSource.Subscriber<T> {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                sequentialCancellable.nextCancellable(cancellable);
            }

            @Override
            public void onSuccess(@Nullable final T result) {
                final Completable redoDecider;
                try {
                    subscriber.onNext(result);
                    redoDecider = requireNonNull(outer.shouldRedo.apply(++redoCount, result));
                } catch (Throwable cause) {
                    onErrorInternal(cause);
                    return;
                }

                redoDecider.subscribeInternal(new CompletableSource.Subscriber() {
                    @Override
                    public void onSubscribe(final Cancellable cancellable) {
                        sequentialCancellable.nextCancellable(cancellable);
                    }

                    @Override
                    public void onComplete() {
                        for (;;) {
                            final long prev = outstandingDemand;
                            assert prev != TERMINATED && prev != 0;
                            if (prev == CANCELLED) {
                                break;
                            } else if (prev < 0) {
                                // This thread owns the subscriber, no concurrency expected, no atomic necessary.
                                onErrorInternal(newExceptionForInvalidRequestN(prev));
                                break;
                            } else if (outstandingDemandUpdater.compareAndSet(RedoSubscription.this,
                                    prev, prev - 1)) {
                                if (prev > 1) {
                                    outer.original.delegateSubscribe(RedoSubscriber.this, contextMap,
                                            contextProvider);
                                }
                                break;
                            }
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        outstandingDemand = TERMINATED;
                        subscriber.onComplete(); // repeat means an error just terminates normally.
                    }
                });
            }

            @Override
            public void onError(final Throwable t) {
                onErrorInternal(t);
            }

            private void onErrorInternal(final Throwable t) {
                outstandingDemand = TERMINATED;
                subscriber.onError(t);
            }
        }
    }
}
