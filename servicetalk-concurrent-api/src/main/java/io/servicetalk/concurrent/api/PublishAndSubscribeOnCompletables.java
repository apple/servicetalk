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

import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.internal.SignalOffloader;

import static io.servicetalk.concurrent.api.MergedExecutors.mergeAndOffloadPublish;
import static io.servicetalk.concurrent.api.MergedExecutors.mergeAndOffloadSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;

/**
 * A set of factory methods that provides implementations for the various publish/subscribeOn methods on
 * {@link Completable}.
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
        return original.executor() == executor ? original : new PublishAndSubscribeOn(original, executor);
    }

    @Deprecated
    static Completable publishAndSubscribeOnOverride(Completable original, Executor executor) {
        return original.executor() == executor ? original : new PublishAndSubscribeOnOverride(original, executor);
    }

    static Completable publishOn(Completable original, Executor executor) {
        return original.executor() == executor ? original : new PublishOn(original, executor);
    }

    @Deprecated
    static Completable publishOnOverride(Completable original, Executor executor) {
        return original.executor() == executor ? original : new PublishOnOverride(original, executor);
    }

    static Completable subscribeOn(Completable original, Executor executor) {
        return original.executor() == executor ? original : new SubscribeOn(original, executor);
    }

    @Deprecated
    static Completable subscribeOnOverride(Completable original, Executor executor) {
        return original.executor() == executor ? original : new SubscribeOnOverride(original, executor);
    }

    private abstract static class OffloadingCompletable extends AbstractNoHandleSubscribeCompletable {
        protected final Executor executor;
        protected final Completable original;

        protected OffloadingCompletable(Completable original, Executor executor) {
            this.original = original;
            this.executor = executor;
        }

        final Executor executor() {
            return executor;
        }
    }

    private abstract static class OffloadingOverrideCompletable extends AbstractSynchronousCompletableOperator {
        protected final Executor executor;

        protected OffloadingOverrideCompletable(Completable original, Executor executor) {
            super(original);
            this.executor = executor;
        }

        final Executor executor() {
            return executor;
        }
    }

    private static final class PublishAndSubscribeOn extends OffloadingCompletable {

        PublishAndSubscribeOn(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // This operator is to make sure that we use the executor to subscribe to the Completable that is returned
            // by this operator.
            //
            // Here we offload signals from original to subscriber using signalOffloader.
            // We use executor to create the returned Completable which means executor will be used
            // to offload handleSubscribe as well as the Subscription that is sent to the subscriber here.
            //
            // This operator acts as a boundary that changes the Executor from original to the rest of the execution
            // chain. If there is already an Executor defined for original, it will be used to offload signals until
            // they hit this operator.
            original.subscribeWithSharedContext(
                    signalOffloader.offloadSubscriber(
                            contextProvider.wrapCompletableSubscriber(subscriber, contextMap)), contextProvider);
        }
    }

    /**
     * This operator is to make sure that we override the {@link Executor} for the entire execution chain. This is the
     * normal mode of operation if we create a {@link Completable} with an {@link Executor}, i.e. all operators behave
     * the same way. Hence, we simply use {@link AbstractSynchronousCompletableOperator} which does not do any extra
     * offloading, it just overrides the {@link Executor} that will be used to do the offloading.
     */
    private static final class PublishAndSubscribeOnOverride extends OffloadingOverrideCompletable {
        PublishAndSubscribeOnOverride(final Completable original, final Executor executor) {
            super(original, executor);
        }

        @Override
        public Subscriber apply(final Subscriber subscriber) {
            // We are using AbstractSynchronousCompletableOperator just to override the Executor. We do not intend
            // to do any extra offloading that is done by a regular Completable created with an Executor.
            return subscriber;
        }
    }

    private static final class PublishOn extends OffloadingCompletable {

        PublishOn(final Completable original, final Executor executor) {
            super(original, mergeAndOffloadPublish(original.executor(), executor));
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // This operator is to make sure that we use the executor to subscribe to the Completable that is returned
            // by this operator.
            //
            // Here we offload signals from original to subscriber using signalOffloader.
            //
            // This operator acts as a boundary that changes the Executor from original to the rest of the execution
            // chain. If there is already an Executor defined for original, it will be used to offload signals until
            // they hit this operator.
            original.subscribeWithSharedContext(
                    signalOffloader.offloadSubscriber(
                            contextProvider.wrapCompletableSubscriber(subscriber, contextMap)), contextProvider);
        }
    }

    /**
     * This operator is to make sure that we override the {@link Executor} for the entire execution chain. This is the
     * normal mode of operation if we create a {@link Completable} with an {@link Executor}, i.e. all operators behave
     * the same way.
     * Hence, we simply use {@link AbstractSynchronousCompletableOperator} which does not do any extra offloading, it
     * just overrides the {@link Executor} that will be used to do the offloading.
     */
    private static final class PublishOnOverride extends OffloadingOverrideCompletable {

        PublishOnOverride(final Completable original, final Executor executor) {
            super(original, mergeAndOffloadPublish(original.executor(), executor));
        }

        @Override
        public Subscriber apply(final Subscriber subscriber) {
            // We are using AbstractSynchronousCompletableOperator just to override the Executor. We do not intend to
            // do any extra offloading that is done by a regular Completable created with an Executor.
            return subscriber;
        }
    }

    private static final class SubscribeOn extends OffloadingCompletable {

        SubscribeOn(final Completable original, final Executor executor) {
            super(original, mergeAndOffloadSubscribe(original.executor(), executor));
        }

        @Override
        void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // This operator is to make sure that we use the executor to subscribe to the Completable that is returned
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
     * normal mode of operation if we create a {@link Completable} with an {@link Executor}, i.e. all operators behave
     * the same way.
     * Hence, we simply use {@link AbstractSynchronousCompletableOperator} which does not do any extra offloading, it
     * just overrides the {@link Executor} that will be used to do the offloading.
     */
    private static final class SubscribeOnOverride extends OffloadingOverrideCompletable {

        SubscribeOnOverride(final Completable original, final Executor executor) {
            super(original, mergeAndOffloadSubscribe(original.executor(), executor));
        }

        @Override
        public Subscriber apply(final Subscriber subscriber) {
            // We are using AbstractSynchronousCompletableOperator just to override the Executor. We do not intend to
            // do any extra offloading that is done by a regular Completable created with an Executor.
            return subscriber;
        }
    }
}
