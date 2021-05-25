/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.internal.SignalOffloader;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.NoopOffloader.NOOP_OFFLOADER;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;

/**
 * A set of factory methods that provides implementations for the various publish/subscribeOn methods on
 * {@link Completable}.
 *
 * <p>This implementation uses <i>task based</i> offloading. Signals are delivered on a thread owned by the provided
 * {@link Executor} invoked via the {@link Executor#execute(Runnable)} method independently for each signal.
 * No assumption should be made by applications that a consistent thread will be used for subsequent signals.
 */
final class PublishAndSubscribeOnCompletables {

    private PublishAndSubscribeOnCompletables() {
        // No instance.
    }

    static void deliverOnSubscribeAndOnError(Subscriber subscriber, SignalOffloader signalOffloader,
                                             AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                                             Throwable cause) {
        deliverErrorFromSource(
                signalOffloader.offloadSubscriber(contextProvider.wrapCompletableSubscriber(subscriber, contextMap)),
                cause);
    }

    static Completable publishAndSubscribeOn(Completable original, Executor executor) {
        return original.executor() == executor || executor == immediate() ?
                original :
                new PublishAndSubscribeOn(original, executor);
    }

    static Completable publishOn(Completable original, Executor executor) {
        return original.executor() == executor || executor == immediate() ?
                original :
                new PublishOn(original, executor);
    }

    static Completable subscribeOn(Completable original, Executor executor) {
        return original.executor() == executor || executor == immediate() ?
                original :
                new SubscribeOn(original, executor);
    }

    /**
     * An asynchronous operator that coordinates the offloading to an executor for asynchronous execution.
     */
    private abstract static class TaskBasedOffloadingCompletableOperator extends TaskBasedAsyncCompletableOperator {

        TaskBasedOffloadingCompletableOperator(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        public final Subscriber apply(final Subscriber subscriber) {
            // We only do offloading
            return subscriber;
        }
    }

    /**
     * Completable that invokes the following methods on the provided executor
     *
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     */
    private static final class PublishAndSubscribeOn extends TaskBasedOffloadingCompletableOperator {

        PublishAndSubscribeOn(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            try {
                executor().execute(() ->
                        super.handleSubscribe(subscriber, NOOP_OFFLOADER, contextMap, contextProvider));
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
                deliverErrorFromSource(subscriber, throwable);
            }
        }
    }

    /**
     * Completable that invokes the following methods on the provided executor
     *
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     * </ul>
     */
    private static final class PublishOn extends TaskBasedOffloadingCompletableOperator {

        PublishOn(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            Subscriber wrapped = contextProvider.wrapCompletableSubscriber(subscriber, contextMap);
            Subscriber offloaded = new OffloadedCompletableSubscriber(wrapped, executor());

            original.delegateSubscribe(offloaded, NOOP_OFFLOADER, contextMap, contextProvider);
        }
    }

    /**
     * Completable that invokes on the provided executor the following methods:
     *
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     */
    private static final class SubscribeOn extends TaskBasedOffloadingCompletableOperator {

        SubscribeOn(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            Subscriber wrapped = contextProvider.wrapCompletableSubscriberAndCancellable(subscriber, contextMap);
            Subscriber offloaded = new OffloadedCancellableCompletableSubscriber(wrapped, executor());
            try {
                executor().execute(() ->
                        original.delegateSubscribe(offloaded, NOOP_OFFLOADER, contextMap, contextProvider));
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
                deliverErrorFromSource(subscriber, throwable);
            }
        }
    }

    /**
     * An asynchronous operator that coordinates the offloading to an executor for asynchronous execution.
     */
    private abstract static class ThreadBasedOffloadingCompletableOperator extends ThreadBasedAsyncCompletableOperator {

        ThreadBasedOffloadingCompletableOperator(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        public final Subscriber apply(final Subscriber subscriber) {
            // We only do offloading
            return subscriber;
        }
    }

    /**
     * Completable that invokes the following methods on the provided executor
     *
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     */
    private static final class PublishAndSubscribeOnThread extends ThreadBasedOffloadingCompletableOperator {

        PublishAndSubscribeOnThread(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            try {
                executor().execute(() ->
                        super.handleSubscribe(subscriber, NOOP_OFFLOADER, contextMap, contextProvider));
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
                deliverErrorFromSource(subscriber, throwable);
            }
        }
    }

    /**
     * Completable that invokes the following methods on the provided executor
     *
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     * </ul>
     */
    private static final class PublishOnThread extends ThreadBasedOffloadingCompletableOperator {

        PublishOnThread(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            Subscriber wrapped = contextProvider.wrapCompletableSubscriber(subscriber, contextMap);
            Subscriber offloaded = new OffloadedCompletableSubscriber(this, wrapped);

            original.delegateSubscribe(offloaded, NOOP_OFFLOADER, contextMap, contextProvider);
        }
    }

    /**
     * Completable that invokes on the provided executor the following methods:
     *
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     */
    private static final class SubscribeOnThread extends ThreadBasedOffloadingCompletableOperator {

        SubscribeOnThread(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            Subscriber wrapped = contextProvider.wrapCompletableSubscriberAndCancellable(subscriber, contextMap);
            Subscriber offloaded = new OffloadedCompletableCancellable(this, wrapped);
            try {
                executor().execute(() ->
                        original.delegateSubscribe(offloaded, NOOP_OFFLOADER, contextMap, contextProvider));
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
                deliverErrorFromSource(subscriber, throwable);
            }
        }
    }
}
