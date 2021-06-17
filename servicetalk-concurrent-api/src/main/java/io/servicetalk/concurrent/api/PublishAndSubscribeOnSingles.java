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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.SignalOffloader;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.MergedExecutors.mergeAndOffloadPublish;
import static io.servicetalk.concurrent.api.MergedExecutors.mergeAndOffloadSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;

/**
 * A set of factory methods that provides implementations for the various publish/subscribeOn methods on
 * {@link Single}.
 */
final class PublishAndSubscribeOnSingles {

    private PublishAndSubscribeOnSingles() {
        // No instance.
    }

    static <T> void deliverOnSubscribeAndOnError(SingleSource.Subscriber<? super T> subscriber,
                                                 SignalOffloader signalOffloader, AsyncContextMap contextMap,
                                                 AsyncContextProvider contextProvider, Throwable cause) {
        deliverErrorFromSource(
                signalOffloader.offloadSubscriber(contextProvider.wrapSingleSubscriber(subscriber, contextMap)), cause);
    }

    static <T> Single<T> publishAndSubscribeOn(Single<T> original, Executor executor) {
        return original.executor() == executor || immediate() == executor ?
                original :
                new PublishAndSubscribeOn<>(original, executor);
    }

    @Deprecated
    static <T> Single<T> publishAndSubscribeOnOverride(Single<T> original, Executor executor) {
        return original.executor() == executor ?
                original :
                new PublishAndSubscribeOnOverride<>(original, executor);
    }

    static <T> Single<T> publishOn(Single<T> original, Executor executor) {
        return original.executor() == executor || immediate() == executor ?
                original :
                new PublishOn<>(original, executor);
    }

    @Deprecated
    static <T> Single<T> publishOnOverride(Single<T> original, Executor executor) {
        return original.executor() == executor ?
                original :
                new PublishOnOverride<>(original, executor);
    }

    static <T> Single<T> subscribeOn(Single<T> original, Executor executor) {
        return original.executor() == executor || immediate() == executor ?
                original :
                new SubscribeOn<>(original, executor);
    }

    @Deprecated
    static <T> Single<T> subscribeOnOverride(Single<T> original, Executor executor) {
        return original.executor() == executor ?
                original :
                new SubscribeOnOverride<>(original, executor);
    }

    private abstract static class OffloadingSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
        protected final Executor executor;
        protected final Single<T> original;

        protected OffloadingSingle(final Single<T> original, final Executor executor) {
            this.original = original;
            this.executor = executor;
        }

        @Override
        final Executor executor() {
            return executor;
        }
    }

    private abstract static class OffloadingOverrideSingle<T> extends AbstractSynchronousSingleOperator<T, T> {
        protected final Executor executor;

        protected OffloadingOverrideSingle(final Single<T> original, final Executor executor) {
            super(original);
            this.executor = executor;
        }

        @Override
        final Executor executor() {
            return executor;
        }
    }

    private static final class PublishAndSubscribeOn<T> extends OffloadingSingle<T> {

        PublishAndSubscribeOn(final Single<T> original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // This operator is to make sure that we use the executor to subscribe to the Single that is returned
            // by this operator.
            //
            // Here we offload signals from original to subscriber using signalOffloader.
            // We use executor to create the returned Single which means executor will be used
            // to offload handleSubscribe as well as the Subscription that is sent to the subscriber here.
            //
            // This operator acts as a boundary that changes the Executor from original to the rest of the execution
            // chain. If there is already an Executor defined for original, it will be used to offload signals until
            // they hit this operator.
            original.subscribeWithSharedContext(
                    signalOffloader.offloadSubscriber(
                            contextProvider.wrapSingleSubscriber(subscriber, contextMap)), contextProvider);
        }
    }

    /**
     * This operator is to make sure that we override the {@link Executor} for the entire execution chain. This is the
     * normal mode of operation if we create a {@link Single} with an {@link Executor}, i.e. all operators behave the
     * same way. Hence, we simply use {@link AbstractSynchronousSingleOperator} which does not do any extra offloading,
     * it just overrides the {@link Executor} that will be used to do the offloading.
     */
    private static final class PublishAndSubscribeOnOverride<T> extends OffloadingOverrideSingle<T> {
        PublishAndSubscribeOnOverride(final Single<T> original, final Executor executor) {
            super(original, executor);
        }

        @Override
        public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
            // We are using AbstractSynchronousSingleOperator just to override the Executor. We do not intend to
            // do any extra offloading that is done by a regular Single created with an Executor.
            return subscriber;
        }
    }

    private static class PublishOn<T> extends OffloadingSingle<T> {

        PublishOn(final Single<T> original, final Executor executor) {
            super(original, mergeAndOffloadPublish(original.executor(), executor));
        }

        @Override
        void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // This operator is to make sure that we use the executor to subscribe to the Single that is returned
            // by this operator.
            //
            // Here we offload signals from original to subscriber using signalOffloader.
            //
            // This operator acts as a boundary that changes the Executor from original to the rest of the execution
            // chain. If there is already an Executor defined for original, it will be used to offload signals until
            // they hit this operator.
            original.subscribeWithSharedContext(
                    signalOffloader.offloadSubscriber(
                            contextProvider.wrapSingleSubscriber(subscriber, contextMap)), contextProvider);
        }
    }

    /**
     * This operator is to make sure that we override the {@link Executor} for the entire execution chain. This is the
     * normal mode of operation if we create a {@link Single} with an {@link Executor}, i.e. all operators behave the
     * same way.
     * Hence, we simply use {@link AbstractSynchronousSingleOperator} which does not do any extra offloading, it just
     * overrides the {@link Executor} that will be used to do the offloading.
     */
    private static final class PublishOnOverride<T> extends OffloadingOverrideSingle<T> {

        PublishOnOverride(final Single<T> original, final Executor executor) {
            super(original, mergeAndOffloadPublish(original.executor(), executor));
        }

        @Override
        public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
            // We are using AbstractSynchronousSingleOperator just to override the Executor. We do not intend to
            // do any extra offloading that is done by a regular Single created with an Executor.
            return subscriber;
        }
    }

    private static final class SubscribeOn<T> extends OffloadingSingle<T> {

        SubscribeOn(final Single<T> original, final Executor executor) {
            super(original, mergeAndOffloadSubscribe(original.executor(), executor));
        }

        @Override
        void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // This operator is to make sure that we use the executor to subscribe to the Single that is returned
            // by this operator.
            //
            // Subscription and handleSubscribe are offloaded at subscribe so we do not need to do anything specific
            // here.
            //
            // This operator acts as a boundary that changes the Executor from original to the rest of the execution
            // chain. If there is already an Executor defined for original, it will be used to offload signals until
            // they hit this operator.
            original.subscribeWithSharedContext(subscriber, contextProvider);
        }
    }

    /**
     * This operator is to make sure that we override the {@link Executor} for the entire execution chain. This is the
     * normal mode of operation if we create a {@link Single} with an {@link Executor}, i.e. all operators behave the
     * same way.
     * Hence, we simply use {@link AbstractSynchronousSingleOperator} which does not do any extra offloading, it just
     * overrides the {@link Executor} that will be used to do the offloading.
     */
    private static final class SubscribeOnOverride<T> extends OffloadingOverrideSingle<T> {
        SubscribeOnOverride(final Single<T> original, final Executor executor) {
            super(original, mergeAndOffloadSubscribe(original.executor(), executor));
        }

        @Override
        public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
            // We are using AbstractSynchronousSingleOperator just to override the Executor. We do not intend to
            // do any extra offloading that is done by a regular Single created with an Executor.
            return subscriber;
        }
    }
}
