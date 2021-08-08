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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.TaskBasedAsyncCompletableOperator.AbstractOffloadedSingleValueSubscriber;
import io.servicetalk.concurrent.api.TaskBasedAsyncCompletableOperator.OffloadedCancellable;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.safeCancel;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnSuccess;
import static java.util.Objects.requireNonNull;

/**
 *  Asynchronous operator for {@link Single} that processes signals with task based offloading.
 *
 * Base class for operators on a {@link Single} that process signals asynchronously hence in order to guarantee safe
 * downstream invocations require to wrap their {@link Subscriber}s with the correct {@link AsyncContext}.
 * Operators that process signals synchronously can use {@link AbstractSynchronousSingleOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @param <T> Type of original {@link Single}.
 *
 * @see AbstractSynchronousSingleOperator
 */
abstract class TaskBasedAsyncSingleOperator<T> extends AbstractNoHandleSubscribeSingle<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskBasedAsyncSingleOperator.class);
    private static final Object NULL_WRAPPER = new Object() {
        @Override
        public String toString() {
            return "NULL_WRAPPER";
        }
    };

    private final Single<T> original;
    private final BooleanSupplier shouldOffload;
    private final Executor executor;

    TaskBasedAsyncSingleOperator(final Single<T> original,
                                 final BooleanSupplier shouldOffload,
                                 final Executor executor) {
        this.original = original;
        this.shouldOffload = shouldOffload;
        this.executor = executor;
    }

    final BooleanSupplier shouldOffload() {
        return shouldOffload;
    }

    final Executor executor() {
        return executor;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {

        original.delegateSubscribe(subscriber, contextMap, contextProvider);
    }

    static final class SingleSubscriberOffloadedTerminals<T> extends AbstractOffloadedSingleValueSubscriber
            implements Subscriber<T> {
        private final Subscriber<T> target;

        SingleSubscriberOffloadedTerminals(final Subscriber<T> target,
                                           final BooleanSupplier shouldOffload, final Executor executor) {
            super(shouldOffload, executor);
            this.target = requireNonNull(target);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            terminal(result == null ? NULL_WRAPPER : result);
        }

        @Override
        public void onError(final Throwable t) {
            terminal(TerminalNotification.error(t));
        }

        @Override
        void terminateOnEnqueueFailure(final Throwable cause) {
            LOGGER.warn("Failed to execute task on the executor {}. " +
                    "Invoking Subscriber (onError()) in the caller thread. Subscriber {}.", executor, target, cause);
            target.onError(cause);
        }

        @Override
        void deliverTerminalToSubscriber(final Object terminal) {
            if (terminal instanceof TerminalNotification) {
                final Throwable error = ((TerminalNotification) terminal).cause();
                assert error != null;
                safeOnError(target, error);
            } else {
                safeOnSuccess(target, uncheckCast(terminal));
            }
        }

        @Override
        void sendOnSubscribe(final Cancellable cancellable) {
            try {
                target.onSubscribe(cancellable);
            } catch (Throwable t) {
                onSubscribeFailed();
                safeOnError(target, t);
                safeCancel(cancellable);
            }
        }

        @Nullable
        @SuppressWarnings("unchecked")
        private T uncheckCast(final Object signal) {
            return signal == NULL_WRAPPER ? null : (T) signal;
        }
    }

    static final class SingleSubscriberOffloadedCancellable<T> implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        private final BooleanSupplier shouldOffload;
        private final Executor executor;

        SingleSubscriberOffloadedCancellable(final Subscriber<? super T> subscriber,
                                             final BooleanSupplier shouldOffload, final Executor executor) {
            this.subscriber = requireNonNull(subscriber);
            this.shouldOffload = shouldOffload;
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(new OffloadedCancellable(cancellable, shouldOffload, executor));
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            subscriber.onSuccess(result);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }
    }
}
