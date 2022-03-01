/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

final class DefaultAsyncContextProvider implements AsyncContextProvider {
    static final AsyncContextProvider INSTANCE = new DefaultAsyncContextProvider();

    private static final AsyncContextMapThreadLocal CONTEXT_LOCAL = new AsyncContextMapThreadLocal();

    private DefaultAsyncContextProvider() {
        // singleton
    }

    @Nonnull
    @Override
    public ContextMap context() {
        return CONTEXT_LOCAL.get();
    }

    @Override
    public CompletableSource.Subscriber wrapCancellable(final CompletableSource.Subscriber subscriber,
                                                        final ContextMap context) {
        if (subscriber instanceof ContextPreservingCompletableSubscriber) {
            final ContextPreservingCompletableSubscriber s = (ContextPreservingCompletableSubscriber) subscriber;
            if (s.saved == context) {
                return subscriber instanceof ContextPreservingCompletableSubscriberAndCancellable ? subscriber :
                        new ContextPreservingCompletableSubscriberAndCancellable(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingCancellableCompletableSubscriber &&
                ((ContextPreservingCancellableCompletableSubscriber) subscriber).saved == context) {
            // no need to check for instanceof ContextPreservingCompletableSubscriberAndCancellable, because
            // it extends from ContextPreservingSingleSubscriber.
            return subscriber;
        }
        return new ContextPreservingCancellableCompletableSubscriber(subscriber, context);
    }

    @Override
    public CompletableSource.Subscriber wrapCompletableSubscriber(final CompletableSource.Subscriber subscriber,
                                                                  final ContextMap context) {
        if (subscriber instanceof ContextPreservingCancellableCompletableSubscriber) {
            final ContextPreservingCancellableCompletableSubscriber s =
                    (ContextPreservingCancellableCompletableSubscriber) subscriber;
            if (s.saved == context) {
                // replace current wrapper with wrapper that includes Subscriber and Cancellable
                return new ContextPreservingCompletableSubscriberAndCancellable(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingCompletableSubscriber &&
                ((ContextPreservingCompletableSubscriber) subscriber).saved == context) {
            // no need to check for instanceof ContextPreservingCompletableSubscriberAndCancellable, because
            // it extends from ContextPreservingCompletableSubscriber.
            return subscriber;
        }
        return new ContextPreservingCompletableSubscriber(subscriber, context);
    }

    @Override
    public CompletableSource.Subscriber wrapCompletableSubscriberAndCancellable(
            final CompletableSource.Subscriber subscriber, final ContextMap context) {
        if (subscriber instanceof ContextPreservingCompletableSubscriber) {
            final ContextPreservingCompletableSubscriber s = (ContextPreservingCompletableSubscriber) subscriber;
            if (s.saved == context) {
                return subscriber instanceof ContextPreservingCompletableSubscriberAndCancellable ? subscriber :
                        new ContextPreservingCompletableSubscriberAndCancellable(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingCancellableCompletableSubscriber) {
            final ContextPreservingCancellableCompletableSubscriber s =
                    (ContextPreservingCancellableCompletableSubscriber) subscriber;
            if (s.saved == context) {
                return new ContextPreservingCompletableSubscriberAndCancellable(s.subscriber, context);
            }
        }
        return new ContextPreservingCompletableSubscriberAndCancellable(subscriber, context);
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapCancellable(final SingleSource.Subscriber<T> subscriber,
                                                          final ContextMap context) {
        if (subscriber instanceof ContextPreservingSingleSubscriber) {
            final ContextPreservingSingleSubscriber<T> s = (ContextPreservingSingleSubscriber<T>) subscriber;
            if (s.saved == context) {
                return subscriber instanceof ContextPreservingSingleSubscriberAndCancellable ? subscriber :
                        new ContextPreservingSingleSubscriberAndCancellable<>(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingCancellableSingleSubscriber &&
                ((ContextPreservingCancellableSingleSubscriber<T>) subscriber).saved == context) {
            // no need to check for instanceof ContextPreservingSingleSubscriberAndCancellable, because
            // it extends from ContextPreservingSingleSubscriber.
            return subscriber;
        }
        return new ContextPreservingCancellableSingleSubscriber<>(subscriber, context);
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapSingleSubscriber(final SingleSource.Subscriber<T> subscriber,
                                                               final ContextMap context) {
        if (subscriber instanceof ContextPreservingCancellableSingleSubscriber) {
            final ContextPreservingCancellableSingleSubscriber<T> s =
                    (ContextPreservingCancellableSingleSubscriber<T>) subscriber;
            if (s.saved == context) {
                return new ContextPreservingSingleSubscriberAndCancellable<>(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingSingleSubscriber &&
                ((ContextPreservingSingleSubscriber<T>) subscriber).saved == context) {
            // no need to check for instanceof ContextPreservingSingleSubscriberAndCancellable, because
            // it extends from ContextPreservingSingleSubscriber.
            return subscriber;
        }
        return new ContextPreservingSingleSubscriber<>(subscriber, context);
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapSingleSubscriberAndCancellable(
            final SingleSource.Subscriber<T> subscriber, final ContextMap context) {
        if (subscriber instanceof ContextPreservingSingleSubscriber) {
            final ContextPreservingSingleSubscriber<T> s = (ContextPreservingSingleSubscriber<T>) subscriber;
            if (s.saved == context) {
                return subscriber instanceof ContextPreservingSingleSubscriberAndCancellable ? subscriber :
                        new ContextPreservingSingleSubscriberAndCancellable<>(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingCancellableSingleSubscriber) {
            final ContextPreservingCancellableSingleSubscriber<T> s =
                    (ContextPreservingCancellableSingleSubscriber<T>) subscriber;
            if (s.saved == context) {
                return new ContextPreservingSingleSubscriberAndCancellable<>(s.subscriber, context);
            }
        }
        return new ContextPreservingSingleSubscriberAndCancellable<>(subscriber, context);
    }

    @Override
    public <T> Subscriber<T> wrapSubscription(final Subscriber<T> subscriber, final ContextMap context) {
        if (subscriber instanceof ContextPreservingSubscriber) {
            final ContextPreservingSubscriber<T> s = (ContextPreservingSubscriber<T>) subscriber;
            if (s.saved == context) {
                return subscriber instanceof ContextPreservingSubscriberAndSubscription ? subscriber :
                        new ContextPreservingSubscriberAndSubscription<>(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingSubscriptionSubscriber &&
                ((ContextPreservingSubscriptionSubscriber<T>) subscriber).saved == context) {
            // no need to check for instanceof ContextPreservingSubscriberAndSubscription, because
            // it extends from ContextPreservingSubscriptionSubscriber.
            return subscriber;
        }
        return new ContextPreservingSubscriptionSubscriber<>(subscriber, context);
    }

    @Override
    public <T> Subscriber<T> wrapPublisherSubscriber(final Subscriber<T> subscriber, final ContextMap context) {
        if (subscriber instanceof ContextPreservingSubscriptionSubscriber) {
            final ContextPreservingSubscriptionSubscriber<T> s =
                    (ContextPreservingSubscriptionSubscriber<T>) subscriber;
            if (s.saved == context) {
                return new ContextPreservingSubscriberAndSubscription<>(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingSubscriber &&
                ((ContextPreservingSubscriber<T>) subscriber).saved == context) {
            // no need to check for instanceof ContextPreservingSubscriberAndSubscription, because
            // it extends from ContextPreservingSubscriptionSubscriber.
            return subscriber;
        }
        return new ContextPreservingSubscriber<>(subscriber, context);
    }

    @Override
    public <T> Subscriber<T> wrapPublisherSubscriberAndSubscription(final Subscriber<T> subscriber,
                                                                    final ContextMap context) {
        if (subscriber instanceof ContextPreservingSubscriber) {
            final ContextPreservingSubscriber<T> s = (ContextPreservingSubscriber<T>) subscriber;
            if (s.saved == context) {
                return subscriber instanceof ContextPreservingSubscriberAndSubscription ? subscriber :
                        new ContextPreservingSubscriberAndSubscription<>(s.subscriber, context);
            }
        } else if (subscriber instanceof ContextPreservingSubscriptionSubscriber) {
            final ContextPreservingSubscriptionSubscriber<T> s =
                    (ContextPreservingSubscriptionSubscriber<T>) subscriber;
            if (s.saved == context) {
                return new ContextPreservingSubscriberAndSubscription<>(s.subscriber, context);
            }
        }
        return new ContextPreservingSubscriberAndSubscription<>(subscriber, context);
    }

    @Override
    public Executor wrapJdkExecutor(final Executor executor) {
        return ContextPreservingExecutor.of(executor);
    }

    @Override
    public ExecutorService wrapJdkExecutorService(final ExecutorService executor) {
        return ContextPreservingExecutorService.of(executor);
    }

    @Override
    public io.servicetalk.concurrent.api.Executor wrapExecutor(final io.servicetalk.concurrent.api.Executor executor) {
        return ContextPreservingStExecutor.of(executor);
    }

    @Override
    public ScheduledExecutorService wrapJdkScheduledExecutorService(final ScheduledExecutorService executor) {
        return ContextPreservingScheduledExecutorService.of(executor);
    }

    @Override
    public <T> CompletableFuture<T> wrapCompletableFuture(final CompletableFuture<T> future, final ContextMap context) {
        return ContextPreservingCompletableFuture.newContextPreservingFuture(future, context);
    }

    @Override
    public Runnable wrapRunnable(final Runnable runnable, final ContextMap context) {
        return new ContextPreservingRunnable(runnable, context);
    }

    @Override
    public <V> Callable<V> wrapCallable(final Callable<V> callable, final ContextMap context) {
        return new ContextPreservingCallable<>(callable, context);
    }

    @Override
    public <T> Consumer<T> wrapConsumer(final Consumer<T> consumer, final ContextMap context) {
        return new ContextPreservingConsumer<>(consumer, context);
    }

    @Override
    public <T, U> Function<T, U> wrapFunction(final Function<T, U> func, final ContextMap context) {
        return new ContextPreservingFunction<>(func, context);
    }

    @Override
    public <T, U> BiConsumer<T, U> wrapBiConsumer(final BiConsumer<T, U> consumer, final ContextMap context) {
        return new ContextPreservingBiConsumer<>(consumer, context);
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrapBiFunction(final BiFunction<T, U, V> func, final ContextMap context) {
        return new ContextPreservingBiFunction<>(func, context);
    }
}
