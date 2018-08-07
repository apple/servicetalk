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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

abstract class AbstractMergeCompletableOperator extends AbstractNoHandleSubscribeCompletable
        implements CompletableOperator {

    private final Completable original;

    AbstractMergeCompletableOperator(Completable original, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader) {
        // Offload signals to the passed Subscriber making sure they are not invoked in the thread that
        // asynchronously processes signals. This is because the thread that processes the signals may have different
        // thread safety characteristics than the typical thread interacting with the execution chain
        final Subscriber operatorSubscriber = signalOffloader.offloadSubscriber(subscriber);
        MergeSubscriber mergeSubscriber = apply(operatorSubscriber);
        // Subscriber to use to subscribe to the original source. Since this is an asynchronous operator, it may call
        // Cancellable method from EventLoop (if the asynchronous source created/obtained inside this operator uses
        // EventLoop) which may execute blocking code on EventLoop, eg: doBeforeCancel(). So, we should offload
        // Cancellable method here.
        final Subscriber upstreamSubscriber = signalOffloader.offloadCancellable(mergeSubscriber);
        original.subscribe(upstreamSubscriber, signalOffloader);
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
        static final AtomicIntegerFieldUpdater<MergeSubscriber> completedCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MergeSubscriber.class, "completedCount");
        private static final AtomicReferenceFieldUpdater<MergeSubscriber, Object> terminalNotificationUpdater =
                AtomicReferenceFieldUpdater.newUpdater(MergeSubscriber.class, Object.class, "terminalNotification");
        private static final Object ON_COMPLETE = new Object();

        @SuppressWarnings("unused")
        volatile int completedCount;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Object terminalNotification;
        private final Subscriber subscriber;
        private final DynamicCompositeCancellable dynamicCancellable;
        private final boolean delayError;

        MergeSubscriber(Subscriber subscriber, boolean delayError) {
            this.subscriber = subscriber;
            this.delayError = delayError;
            dynamicCancellable = new QueueDynamicCompositeCancellable();
            subscriber.onSubscribe(dynamicCancellable);
        }

        MergeSubscriber(Subscriber subscriber, int completedCount, boolean delayError) {
            this.subscriber = subscriber;
            this.delayError = delayError;
            this.completedCount = completedCount;
            dynamicCancellable = new QueueDynamicCompositeCancellable();
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
                        // If we are not delaying error notification, and we fail to set the terminal event then that means we
                        // have already notified the subscriber and should return to avoid duplicate terminal notifications.
                        return;
                    }
                } else {
                    Throwable tmpT = (Throwable) terminalNotification;
                    tmpT.addSuppressed(t);
                    t = tmpT;
                    break;
                }
            }
            if (!delayError || onTerminate()) {
                onError0(t);
            }
        }

        final void tryToCompleteSubscriber() {
            if (terminalNotificationUpdater.compareAndSet(this, null, ON_COMPLETE)) {
                subscriber.onComplete();
            } else if (delayError) {
                Throwable t = (Throwable) terminalNotification;
                assert t != null;
                onError0(t);
            }
        }

        private void onError0(Throwable t) {
            // If we are delaying error, then everything must have terminated by now, and there is no need to cancel anything.
            // However if we are not delaying error then we should cancel all outstanding work.
            if (!delayError) {
                dynamicCancellable.cancel();
            }
            subscriber.onError(t);
        }

        /**
         * Called when a terminal event is received.
         * @return {@code true} if we are done processing after changing state due to receiving a terminal event.
         */
        abstract boolean onTerminate();

        /**
         * Determine if we are currently done.
         * @return {@code true} if we are currently done.
         */
        abstract boolean isDone();
    }
}
