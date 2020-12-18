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
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

abstract class AbstractMergeCompletableOperator extends AbstractNoHandleSubscribeCompletable
        implements CompletableOperator {

    private final Completable original;

    AbstractMergeCompletableOperator(Completable original, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                               AsyncContextProvider contextProvider) {
        // Offload signals to the passed Subscriber making sure they are not invoked in the thread that
        // asynchronously processes signals. This is because the thread that processes the signals may have different
        // thread safety characteristics than the typical thread interacting with the execution chain.
        //
        // The AsyncContext needs to be preserved when ever we interact with the original Subscriber, so we wrap it here
        // with the original contextMap. Otherwise some other context may leak into this subscriber chain from the other
        // side of the asynchronous boundary.
        final Subscriber operatorSubscriber = signalOffloader.offloadSubscriber(
                contextProvider.wrapCompletableSubscriberAndCancellable(subscriber, contextMap));
        MergeSubscriber mergeSubscriber = apply(operatorSubscriber);
        // Subscriber to use to subscribe to the original source. Since this is an asynchronous operator, it may call
        // Cancellable method from EventLoop (if the asynchronous source created/obtained inside this operator uses
        // EventLoop) which may execute blocking code on EventLoop, eg: beforeCancel(). So, we should offload
        // Cancellable method here.
        //
        // We are introducing offloading on the Subscription, which means the AsyncContext may leak if we don't save
        // and restore the AsyncContext before/after the asynchronous boundary.
        final Subscriber upstreamSubscriber = signalOffloader.offloadCancellable(mergeSubscriber);
        original.delegateSubscribe(upstreamSubscriber, signalOffloader, contextMap, contextProvider);
        doMerge(mergeSubscriber);
    }

    @Override
    public abstract MergeSubscriber apply(Subscriber subscriber);

    /**
     * Called after the {@code original} {@link Completable} is subscribed and provides a way to subscribe to all other
     * {@link Completable}s that are to be merged with the {@code original} {@link Completable}.
     * @param subscriber {@link MergeSubscriber} to be used to merge.
     */
    abstract void doMerge(MergeSubscriber subscriber);

    abstract static class MergeSubscriber implements Subscriber {
        private static final AtomicReferenceFieldUpdater<MergeSubscriber, Object> terminalNotificationUpdater =
                newUpdater(MergeSubscriber.class, Object.class, "terminalNotification");
        private static final Object ON_COMPLETE = new Object();
        private static final Object DELIVERED_DELAYED_ERROR = new Object();
        @SuppressWarnings("unused")
        @Nullable
        private volatile Object terminalNotification;
        private final Subscriber subscriber;
        private final CancellableStack dynamicCancellable;
        private final boolean delayError;

        MergeSubscriber(Subscriber subscriber, boolean delayError) {
            this.subscriber = subscriber;
            this.delayError = delayError;
            dynamicCancellable = new CancellableStack();
            subscriber.onSubscribe(dynamicCancellable);
        }

        @Override
        public final void onSubscribe(Cancellable cancellable) {
            dynamicCancellable.add(cancellable);
        }

        @Override
        public final void onComplete() {
            if (onTerminate()) {
                tryToCompleteSubscriber();
            }
        }

        @Override
        public final void onError(Throwable t) {
            for (;;) {
                Object terminalNotification = this.terminalNotification;
                if (terminalNotification == null) {
                    if (terminalNotificationUpdater.compareAndSet(this, null, t)) {
                        break;
                    } else if (!delayError) {
                        // If we are not delaying error notification, and we fail to set the terminal event then that
                        // means we have already notified the subscriber and should return to avoid duplicate terminal
                        // notifications.
                        return;
                    }
                } else {
                    Throwable tmpT = (Throwable) terminalNotification;
                    tmpT.addSuppressed(t);
                    t = tmpT;
                    break;
                }
            }

            // If we are delaying the error then we need to prevent concurrent termination with the subscribe() thread,
            // and we do this with a two phase atomic set to DELIVERED_DELAYED_ERROR.
            // If we don't delay the error then we never increase the total completion count, so we don't have to worry
            // about concurrent invocation.
            if (!delayError || (onTerminate() &&
                    t == terminalNotificationUpdater.getAndSet(this, DELIVERED_DELAYED_ERROR))) {
                onError0(t);
            }
        }

        final void tryToCompleteSubscriber() {
            if (terminalNotificationUpdater.compareAndSet(this, null, ON_COMPLETE)) {
                subscriber.onComplete();
            } else if (delayError) {
                // This maybe called from the subscribe() thread and also the Subscriber thread. It is possible the
                // merge operation already completed successfully in the Subscriber thread, and then the subscribe()
                // thread observed all merged Completables have completed and so also calls this method.
                Object maybeThrowable = terminalNotificationUpdater.getAndSet(this, DELIVERED_DELAYED_ERROR);
                if (maybeThrowable instanceof Throwable) {
                    onError0((Throwable) maybeThrowable);
                }
            }
        }

        private void onError0(Throwable t) {
            // If we are delaying error, then everything must have terminated by now, and there is no need to cancel
            // anything. However if we are not delaying error then we should cancel all outstanding work.
            if (!delayError) {
                dynamicCancellable.cancel();
            }
            subscriber.onError(t);
        }

        /**
         * Called when a terminal event is received.
         *
         * @return {@code true} if we are done processing after changing state due to receiving a terminal event.
         */
        abstract boolean onTerminate();
    }
}
