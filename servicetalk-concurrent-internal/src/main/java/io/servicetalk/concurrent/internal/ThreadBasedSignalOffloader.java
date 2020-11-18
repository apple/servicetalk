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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static java.lang.System.arraycopy;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;

/**
 * An implementation of {@link SignalOffloader} that uses a single thread to offload all signals.
 */
final class ThreadBasedSignalOffloader implements SignalOffloader, Runnable {

    private static final int INDEX_INIT = -1;
    private static final int INDEX_OFFLOADER_TERMINATED = -2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadBasedSignalOffloader.class);

    private static final String UNKNOWN_EXECUTOR_THREAD_NAME = "signal-offloader-<unknown>";

    private static final AtomicIntegerFieldUpdater<ThreadBasedSignalOffloader> lastEntityIndexUpdater =
            newUpdater(ThreadBasedSignalOffloader.class, "lastEntityIndex");

    private final Executor executor;
    private final int publisherSignalQueueInitialCapacity;

    // Why not use a Queue?
    // Typical usecase for this offloader is where entities are added as the execution chain is subscribed and then
    // different events happen on these entities. Since, events are typically much more than the number of entities,
    // which are proportional to the number of asynchronous operators in the execution chain, this approach means we
    // do not have to mutate the queue once subscribe has traversed through all operators in the execution chain.
    //
    // If we use a queue, then we can selectively run the entities which have pending signals but we enqueue/dequeue
    // from the queue on every signal.
    // This approach iterates over all entities for each run and skip entities which have previously terminated.
    private OffloadedEntity[] offloadedEntities;
    private volatile int lastEntityIndex = INDEX_INIT;
    @Nullable
    private volatile Thread executorThread;

    /**
     * New instance.
     *
     * @param executor A {@link Executor} to use for offloading signals.
     */
    ThreadBasedSignalOffloader(Executor executor) {
        this(executor, 2, 2);
    }

    /**
     * New instance.
     *
     * @param executor A {@link Consumer} representing an executor and hence provides the memory visibility guarantee:
     * {@link Runnable} submitted to this consumer happens-before invoking {@link Runnable#run()} on that
     * {@link Runnable}.
     * @param expectedOffloadingEntities An approximation of the number of entities offloaded.
     * @param publisherSignalQueueInitialCapacity Initial capacity for the queue of signals to a {@link Subscriber}.
     */
    ThreadBasedSignalOffloader(final Executor executor, final int expectedOffloadingEntities,
                               final int publisherSignalQueueInitialCapacity) {
        this.executor = requireNonNull(executor);
        this.publisherSignalQueueInitialCapacity = publisherSignalQueueInitialCapacity;
        offloadedEntities = new OffloadedEntity[expectedOffloadingEntities];
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscriber(Subscriber<? super T> subscriber) {
        return addOffloadedEntity(new OffloadedSubscriber<>(this, subscriber));
    }

    @Override
    public <T> SingleSource.Subscriber<? super T> offloadSubscriber(SingleSource.Subscriber<? super T> subscriber) {
        return addOffloadedEntity(new OffloadedSingleSubscriber<>(this, subscriber));
    }

    @Override
    public CompletableSource.Subscriber offloadSubscriber(CompletableSource.Subscriber subscriber) {
        return addOffloadedEntity(new OffloadedCompletableSubscriber(this, subscriber));
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscription(Subscriber<? super T> subscriber) {
        return addOffloadedEntity(new OffloadedSubscription<>(this, subscriber));
    }

    @Override
    public <T> SingleSource.Subscriber<? super T> offloadCancellable(SingleSource.Subscriber<? super T> subscriber) {
        return addOffloadedEntity(new OffloadedSingleCancellable<>(this, subscriber));
    }

    @Override
    public CompletableSource.Subscriber offloadCancellable(CompletableSource.Subscriber subscriber) {
        return addOffloadedEntity(new OffloadedCompletableCancellable(this, subscriber));
    }

    @Override
    public <T> void offloadSubscribe(
            Subscriber<? super T> subscriber, Consumer<Subscriber<? super T>> handleSubscribe) {
        try {
            addOffloadedEntity(new OffloadedSignalEntity<>(handleSubscribe, subscriber), true);
        } catch (EnqueueForOffloadingFailed e) {
            // Since we failed to enqueue for offloading, we are sure that Subscriber has not been signalled and hence
            // safe to send the error.
            deliverErrorFromSource(subscriber, e.getCause());
        }
    }

    @Override
    public <T> void offloadSubscribe(SingleSource.Subscriber<? super T> subscriber,
                                     Consumer<SingleSource.Subscriber<? super T>> handleSubscribe) {
        try {
            addOffloadedEntity(new OffloadedSignalEntity<>(handleSubscribe, subscriber), true);
        } catch (EnqueueForOffloadingFailed e) {
            // Since we failed to enqueue for offloading, we are sure that Subscriber has not been signalled and hence
            // safe to send the error.
            deliverErrorFromSource(subscriber, e.getCause());
        }
    }

    @Override
    public void offloadSubscribe(CompletableSource.Subscriber subscriber,
                                 Consumer<CompletableSource.Subscriber> handleSubscribe) {
        try {
            addOffloadedEntity(new OffloadedSignalEntity<>(handleSubscribe, subscriber), true);
        } catch (EnqueueForOffloadingFailed e) {
            // Since we failed to enqueue for offloading, we are sure that Subscriber has not been signalled and hence
            // safe to send the error.
            deliverErrorFromSource(subscriber, e.getCause());
        }
    }

    @Override
    public <T> void offloadSignal(T signal, Consumer<T> signalConsumer) {
        // Since this is an independent offload, it lacks context of what it is trying to offload. We simply always
        // offload here. If this offloading is also not required then one can use the immediate Executor.
        addOffloadedEntity(new OffloadedSignalEntity<>(signalConsumer, signal));
    }

    @Override
    public void run() {
        assert executorThread == null; // This only runs once.
        final Thread executorThread = currentThread();
        this.executorThread = executorThread;

        // There is a basic invariant here that none of the entities should start terminating before all entities are
        // added, which is how the execution chain is processed:
        //
        //    - Send subscribe() through the execution chain from the last operator to source.
        //    - Send onSubscribe() through the execution chain from the source to the last operator.
        //
        // Since, nothing can terminate without a Subscription which is sent through onSubscribe(),
        // we will have all entities registered for offloading before we start terminating.
        //
        // There is one additional angle:
        //
        // handleSubscribe itself uses offloading and it terminates immediately.
        // If it happens so that offloading of handleSubscribe() is the only registered entity and it finished without
        // adding any more entities that will lead to issues. Today we add an offload for Subscription before we offload
        // handleSubscribe so this case is not possible.
        for (;;) {
            // Volatile read will ensure "happens-before" between addition of entity and read by the run() method
            // for this index.
            final int lastIndex = lastEntityIndex;
            assert lastIndex >= 0;

            final OffloadedEntity[] entities = offloadedEntities;
            int terminatedEntities = 0;
            for (int i = 0; i <= lastIndex; i++) {
                OffloadedEntity entity = entities[i];
                // terminated state is only touched from within sendSignals so there are no concurrent mutation,
                // hence no need for atomic operation.
                if (!entity.isTerminated()) {
                    // sendSignals() never throws.
                    entity.sendSignals();
                    if (entity.isTerminated()) {
                        terminatedEntities++;
                    }
                } else {
                    terminatedEntities++;
                }
            }
            if (terminatedEntities == lastEntityIndex + 1) {
                lastEntityIndex = INDEX_OFFLOADER_TERMINATED; // No more entities can be offloaded.
                return;
            } else if (lastIndex == lastEntityIndex) {
                // If more entities are added, do not park, just run the loop again.

                // park-unpark does not guarantee visibility between the entities and the run loop, so we depend on the
                // following:
                //   -- Any changes to offloadedEntities are made visible by the write and read of volatile field
                // lastEntityIndex
                //   -- Any changes to an offloaded entity is made visible by the write and read of the field "state" in
                // that entity.
                // By the above we make sure that whatever change happens to the entity before notifying the run loop,
                // is made visible when the run loop signals that entity.
                park(executorThread);
            }
        }
    }

    void notifyExecutor() {
        notifyExecutor(executorThread);
    }

    void notifyExecutor(@Nullable Thread executorThread) {
        // unpark is a noop if the passed thread is null.
        unpark(executorThread);
    }

    private <T extends OffloadedEntity> T addOffloadedEntity(T offloadedEntity) {
        return addOffloadedEntity(offloadedEntity, false);
    }

    private <T extends OffloadedEntity> T addOffloadedEntity(T offloadedEntity, boolean wrapEnqueueFailure) {
        final int lastIndex = lastEntityIndex;
        if (lastIndex == INDEX_OFFLOADER_TERMINATED) {
            IllegalStateException iae = new IllegalStateException("Signal offloader: " +
                    executorThreadName() + " has already terminated.");
            throw wrapEnqueueFailure ? new EnqueueForOffloadingFailed(iae) : iae;
        }

        final int nextIndex = lastIndex + 1;
        if (nextIndex == offloadedEntities.length) {
            OffloadedEntity[] nextValue = new OffloadedEntity[offloadedEntities.length * 2];
            arraycopy(offloadedEntities, 0, nextValue, 0, offloadedEntities.length);
            // Till we update the lastEntityIndex below, the new value of the array or the entity isn't visible to the
            // run loop so there is no chance of accessing an index which has a null value.
            offloadedEntities = nextValue;
        }
        offloadedEntities[nextIndex] = offloadedEntity;
        // Volatile write will ensure "happens-before" between addition of entity and read by the run() method for
        // this index.
        if (!lastEntityIndexUpdater.compareAndSet(this, lastIndex, nextIndex)) {
            if (lastEntityIndex == INDEX_OFFLOADER_TERMINATED) {
                IllegalStateException iae = new IllegalStateException("Signal offloader: " +
                        executorThreadName() + " has already terminated.");
                throw wrapEnqueueFailure ? new EnqueueForOffloadingFailed(iae) : iae;
            }
            // concurrent registration of entities is not allowed by this class as new offload entities are added in
            // the subscribe path which are not called concurrently.
            IllegalArgumentException iae = new IllegalArgumentException("Entity " + offloadedEntity +
                    " added concurrently for offloading signals.");
            throw wrapEnqueueFailure ? new EnqueueForOffloadingFailed(iae) : iae;
        }
        if (nextIndex == 0) {
            if (wrapEnqueueFailure) {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException re) {
                    throw new EnqueueForOffloadingFailed(re);
                }
            } else {
                executor.execute(this);
            }
        } else {
            final Thread executorThread = this.executorThread;
            // If we are in the executor thread, it can only be when we are adding entities while sending offloaded
            // signals. In such a case we are assured that run-loop will pick this new entity before getting parked.
            // So, we do not need to notify the executor. Notifying will mean that the next park() call will immediately
            // return and drain entities again which is not be required.
            if (currentThread() != executorThread) {
                notifyExecutor(executorThread);
            }
        }
        return offloadedEntity;
    }

    private String executorThreadName() {
        final Thread executorThread = this.executorThread;
        return executorThread == null ? UNKNOWN_EXECUTOR_THREAD_NAME : executorThread.getName();
    }

    private interface OffloadedEntity {

        /**
         * Send any pending signals to the offloaded entity.
         * <b>This method should not throw.</b>
         */
        void sendSignals();

        boolean isTerminated();
    }

    private static final class OffloadedSignalEntity<T> implements OffloadedEntity {
        private final Consumer<T> signalConsumer;
        private final T signal;
        private boolean terminated;

        OffloadedSignalEntity(Consumer<T> signalConsumer, T signal) {
            this.signalConsumer = signalConsumer;
            this.signal = signal;
        }

        @Override
        public void sendSignals() {
            terminated = true;
            // No concurrent executor signals so no need to protect against noop sendSignals0
            try {
                signalConsumer.accept(signal);
            } catch (Throwable throwable) {
                LOGGER.error("Ignored unexpected exception offloading signal: {} to consumer: {}", signal,
                        signalConsumer, throwable);
            }
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }
    }

    private abstract static class AbstractOffloadedEntity implements OffloadedEntity {

        private static final AtomicIntegerFieldUpdater<AbstractOffloadedEntity> notifyUpdater =
                newUpdater(AbstractOffloadedEntity.class, "notify");
        private boolean terminated; // only accessed from the drain thread.
        private final ThreadBasedSignalOffloader offloader;

        @SuppressWarnings("unused")
        private volatile int notify;

        AbstractOffloadedEntity(ThreadBasedSignalOffloader offloader) {
            this.offloader = offloader;
        }

        /**
         * Send all pending signals for this entity and call {@link #setTerminated()} if it does not need to be invoked
         * anymore.
         */
        @Override
        public final void sendSignals() {
            // As with the CAS in notifyExecutor(), this CAS makes sure that writes to all normal fields in this
            // offloaded entity happens-before calling notifyExecutor() and hence sendSignals0().
            // A plain write to the volatile field will not provide guarantees for the load of fields so we need a
            // LoadStore barrier (ref: http://gee.cs.oswego.edu/dl/jmm/cookbook.html).
            //
            // The two CASes together provides a StoreLoad barrier which provides a full-fence between the writes
            // inside an entity and the reading of the same state inside sendSignals0()
            if (notifyUpdater.compareAndSet(this, 1, 0)) {
                sendSignals0();
            }
        }

        @Override
        public final boolean isTerminated() {
            return terminated;
        }

        abstract void sendSignals0();

        final void notifyExecutor() {
            // We CAS this volatile field in order to make sure that all changes made to the entity by the signals
            // happens-before signalling the run-loop and hence are made visible to the run-loop without adding explicit
            // visibility guarantees.
            // CAS adds a LoadStore barrier:  Load1; LoadStore; Store2 such that writes to normal variables can not be
            // reordered with the write to this volatile.
            // (ref: http://gee.cs.oswego.edu/dl/jmm/cookbook.html)
            // ========================================================================================================
            // use of atomic conditional update operations CompareAndSwap (CAS) or LoadLinked/StoreConditional (LL/SC)
            // that have the semantics of performing a volatile load followed by a volatile store.
            // ========================================================================================================
            if (notifyUpdater.compareAndSet(this, 0, 1)) {
                offloader.notifyExecutor();
            }
        }

        final void setTerminated() {
            terminated = true;
        }
    }

    private static final class OffloadedSubscriber<T> extends AbstractOffloadedEntity implements Subscriber<T> {

        private static final Object NULL_ON_NEXT = new Object();
        private final ThreadBasedSignalOffloader offloader;
        private final Subscriber<? super T> original;
        private final Queue<Object> signals;
        @Nullable
        private Subscription subscription;
        private boolean cancelled;

        OffloadedSubscriber(ThreadBasedSignalOffloader offloader, Subscriber<? super T> original) {
            super(offloader);
            this.offloader = offloader;
            this.original = original;
            // Queue is bounded by request-n
            signals = newUnboundedSpscQueue(offloader.publisherSignalQueueInitialCapacity);
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            Subscription cancelInterceptingSubscription = new Subscription() {
                @Override
                public void request(long n) {
                    s.request(n);
                }

                @Override
                public void cancel() {
                    // No concurrent writes, so not atomic. Also never flips back to false once true
                    cancelled = true;
                    notifyExecutor();
                    s.cancel();
                }
            };

            offerSignal(cancelInterceptingSubscription);
        }

        @Override
        public void onNext(T t) {
            offerSignal(t == null ? NULL_ON_NEXT : t);
        }

        @Override
        public void onError(Throwable t) {
            offerSignal(error(t));
        }

        @Override
        public void onComplete() {
            offerSignal(complete());
        }

        @Override
        public void sendSignals0() {
            for (;;) {
                final Object signal = signals.poll();
                if (signal == null) {
                    // cancel is best effort, so check it after draining the signals. Otherwise we incur a volatile read
                    // per iteration.
                    if (cancelled) {
                        setTerminated();
                    }
                    return;
                }
                if (signal instanceof Subscription) {
                    Subscription subscription = (Subscription) signal;
                    try {
                        original.onSubscribe(subscription);
                    } catch (Throwable throwable) {
                        LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, Subscription: {}.",
                                original, subscription, throwable);
                        setTerminated();
                        sendCancel(subscription, null);
                        sendOnErrorToOriginal(throwable);
                    }
                } else if (signal instanceof TerminalNotification) {
                    setTerminated();
                    TerminalNotification terminalNotification = (TerminalNotification) signal;
                    if (terminalNotification.cause() != null) {
                        sendOnErrorToOriginal(terminalNotification.cause());
                    } else {
                        sendOnCompleteToOriginal();
                    }
                } else {
                    try {
                        // We do not expect uncheckedCast to fail as onNext makes sure we do not get a wrong type.
                        original.onNext(signal == NULL_ON_NEXT ? null : uncheckedCast(signal));
                    } catch (Throwable throwable) {
                        setTerminated();
                        sendOnErrorToOriginal(throwable);
                        assert subscription != null;
                        // Spec https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#2.13
                        // 2.13 states that a Subscriber MUST consider its Subscription cancelled if it throws from
                        // any of its methods.
                        // Even though calling Subscription here means that we may be invoking it concurrently,
                        // not doing so would mean the original source never knows that we have already terminated
                        // the Subscriber and keep producing data which is wasteful.
                        // Since Subscription is practically cancelled for the original Subscriber, we can assume that
                        // it is not used.
                        sendCancel(subscription, throwable);
                    }
                }
            }
        }

        private void sendCancel(Subscription subscription, @Nullable Throwable optionalOriginalCause) {
            try {
                subscription.cancel();
            } catch (Throwable throwable) {
                if (optionalOriginalCause != null) {
                    optionalOriginalCause.addSuppressed(throwable);
                }
                LOGGER.error("Ignored unexpected exception terminating subscriber: {}.", original,
                        throwable);
            }
        }

        private void sendOnErrorToOriginal(final Throwable throwable) {
            try {
                original.onError(throwable);
            } catch (Throwable t) {
                throwable.addSuppressed(t);
                LOGGER.error("Ignored unexpected exception terminating subscriber: {}.", original,
                        throwable);
            }
        }

        private void sendOnCompleteToOriginal() {
            try {
                original.onComplete();
            } catch (Throwable t) {
                LOGGER.error("Ignored unexpected exception terminating subscriber: {}.", original, t);
            }
        }

        private void offerSignal(Object signal) {
            if (!signals.offer(signal)) {
                throw new QueueFullException(offloader.executorThreadName() + "-" + original.getClass().getName());
            }
            notifyExecutor();
        }
    }

    private abstract static class AbstractOffloadedSingleValueSubscriber extends AbstractOffloadedEntity {
        // null is allowed, so null can not be a marker for value not received.
        private static final Object INITIAL_VALUE = new Object();
        private static final Object CANCELLED = new Object();
        private static final Cancellable CANCELLABLE_SENT = () -> {
            // Ignore.
        };
        private static final AtomicReferenceFieldUpdater<AbstractOffloadedSingleValueSubscriber, Object> resultUpdater =
                AtomicReferenceFieldUpdater.newUpdater(AbstractOffloadedSingleValueSubscriber.class, Object.class,
                        "result");

        @Nullable
        private Cancellable cancellable;
        @Nullable
        private volatile Object result = INITIAL_VALUE;

        AbstractOffloadedSingleValueSubscriber(ThreadBasedSignalOffloader offloader) {
            super(offloader);
        }

        public void onSubscribe(final Cancellable cancellable) {
            requireNonNull(cancellable);
            if (this.cancellable != null) {
                // This is a violation of spec: https://github.com/reactive-streams/reactive-streams-jvm#2.5
                // so we call cancel() on the calling thread which is otherwise avoided.
                // Since, this isn't something that is expected, we do this to reduce complexity.
                cancellable.cancel();
                return;
            }
            this.cancellable = () -> {
                // Ignore cancel if we have already got the result.
                if (resultUpdater.compareAndSet(AbstractOffloadedSingleValueSubscriber.this, INITIAL_VALUE,
                        CANCELLED)) {
                    notifyExecutor();
                }
                // Since this is an intermediary and not an operator, we do not swallow the signal but just forward it
                // and let the original source/operator make the decision of swallowing cancel if required.
                cancellable.cancel();
            };
            notifyExecutor();
        }

        @Override
        public void sendSignals0() {
            final Cancellable c = cancellable;
            if (c != null && c != CANCELLABLE_SENT) {
                // There would be no concurrency here as `onSubscribe` should only be called once and if there is
                // duplicate calls to onSubscribe, it should not see a null value.
                cancellable = CANCELLABLE_SENT;
                sendOnSubscribe(c);
            }
            final Object result = this.result;
            if (result == INITIAL_VALUE) {
                return;
            }
            setTerminated();
            if (result instanceof TerminalNotification) {
                Throwable cause = ((TerminalNotification) result).cause();
                assert cause != null;
                sendError(cause);
            } else if (result != CANCELLED) {
                sendSuccess(result);
            }
        }

        abstract void sendOnSubscribe(Cancellable c);

        abstract void sendSuccess(@Nullable Object signal);

        abstract void sendError(Throwable t);

        final void result(@Nullable Object result) {
            // Unconditionally set result to prefer result over cancellation if we have not already terminated.
            this.result = result;
            notifyExecutor();
        }
    }

    private static final class OffloadedSingleSubscriber<T> extends AbstractOffloadedSingleValueSubscriber
            implements SingleSource.Subscriber<T> {

        private final SingleSource.Subscriber<? super T> original;

        OffloadedSingleSubscriber(ThreadBasedSignalOffloader offloader, SingleSource.Subscriber<? super T> original) {
            super(offloader);
            this.original = original;
        }

        @Override
        public void onSuccess(@Nullable T t) {
            result(t);
        }

        @Override
        public void onError(Throwable t) {
            result(error(t));
        }

        @Override
        void sendOnSubscribe(Cancellable c) {
            try {
                original.onSubscribe(c);
            } catch (Throwable throwable) {
                LOGGER.error("Unexpected exception sending onSubscribe {} to subscriber: {}.", c, original, throwable);
                // Cancel the Subscription as per Rule 2.13
                // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#2.13
                // we may be violating the no concurrency on the Subscription rule, but the Subscriber/Subscription is
                // invalid and we make a best effort to cleanup
                try {
                    c.cancel();
                } catch (Throwable t) {
                    throwable.addSuppressed(t);
                    LOGGER.error("Ignored unexpected exception sending cancel to cancellable: {}.", c, throwable);
                }
                sendError(throwable);
            }
        }

        @Override
        void sendSuccess(@Nullable Object result) {
            try {
                // We do not expect uncheckedCast to fail as onSuccess makes sure we do not get a wrong type.
                original.onSuccess(uncheckedCast(result));
            } catch (Throwable throwable) {
                LOGGER.error("Ignored unexpected exception sending result {} to subscriber: {}.",
                        result, original, throwable);
            }
        }

        @Override
        void sendError(Throwable t) {
            try {
                original.onError(t);
            } catch (Throwable throwable) {
                t.addSuppressed(throwable);
                LOGGER.error("Ignored unexpected exception sending error to subscriber: {}", original, t);
            }
        }
    }

    private static final class OffloadedCompletableSubscriber extends AbstractOffloadedSingleValueSubscriber
            implements CompletableSource.Subscriber {

        // sendSignals0() assumes a TerminalNotification is for error, so we use a special marker for success.
        private static final Object COMPLETE_SIGNAL = new Object();
        private final CompletableSource.Subscriber original;

        OffloadedCompletableSubscriber(ThreadBasedSignalOffloader offloader, CompletableSource.Subscriber original) {
            super(offloader);
            this.original = original;
        }

        @Override
        public void onComplete() {
            result(COMPLETE_SIGNAL);
        }

        @Override
        public void onError(Throwable t) {
            result(error(t));
        }

        @Override
        void sendOnSubscribe(Cancellable c) {
            try {
                original.onSubscribe(c);
            } catch (Throwable throwable) {
                LOGGER.error("Unexpected exception sending onSubscribe {} to subscriber: {}.", c, original, throwable);
                // Cancel the Subscription as per Rule 2.13
                // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#2.13
                // We may be violating the no concurrency on the Subscription rule, but the Subscriber/Subscription is
                // invalid and we make a best effort to cleanup
                try {
                    c.cancel();
                } catch (Throwable t) {
                    throwable.addSuppressed(t);
                    LOGGER.error("Ignored unexpected exception sending cancel to cancellable: {}.", c, throwable);
                }
                sendError(throwable);
            }
        }

        @Override
        void sendSuccess(@Nullable Object signal) {
            try {
                original.onComplete();
            } catch (Throwable throwable) {
                LOGGER.error("Ignored unexpected exception sending completion to subscriber: {}.", original, throwable);
            }
        }

        @Override
        void sendError(Throwable t) {
            try {
                original.onError(t);
            } catch (Throwable throwable) {
                t.addSuppressed(throwable);
                LOGGER.error("Ignored unexpected exception sending error to subscriber: {}", original, t);
            }
        }
    }

    private static final class OffloadedSubscription<T> extends AbstractOffloadedEntity implements Subscriber<T> {

        private static final AtomicLongFieldUpdater<OffloadedSubscription> requestedUpdater =
                AtomicLongFieldUpdater.newUpdater(OffloadedSubscription.class, "requested");
        public static final int CANCELLED = -1;
        public static final int TERMINATED = -2;
        private final Subscriber<? super T> original;

        /**
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm#2.11">RS rule 2.11</a> ensures that
         * receiving of all Subscriber signals happen before processing of the signals. This means the thread using the
         * Subscription sees a store to this field hence providing visibility for this field to the thread
         * invoking the Subscription. We make sure through notifyExecutor -> sendSignal routine that the thread using
         * the Subscription correctly publishes changes to the run-loop. Hence making Subscription visible to the
         * run-loop.
         */
        @Nullable
        private Subscription subscription;

        @SuppressWarnings("unused")
        private volatile long requested;

        OffloadedSubscription(ThreadBasedSignalOffloader offloader, Subscriber<? super T> original) {
            super(offloader);
            this.original = original;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            original.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    boolean notify;
                    if (isRequestNValid(n)) {
                        notify = requestedUpdater.getAndAccumulate(OffloadedSubscription.this, n,
                                FlowControlUtils::addWithOverflowProtectionIfNotNegative) >= 0;
                    } else {
                        // We can not call the original subscription here as that would mean we call it from the caller
                        // thread and hence break the assumption that it is always called in an Executor thread.
                        // Since, we use special negative values to indicate cancellation/termination/no pending
                        // requests, just use an unused value.
                        notify = requestedUpdater.getAndSet(OffloadedSubscription.this,
                                n < TERMINATED ? n : Long.MIN_VALUE) >= 0;
                    }
                    // Ignore notify if we had already terminated (requested < 0)
                    if (notify) {
                        notifyExecutor();
                    }
                }

                @Override
                public void cancel() {
                    requested(CANCELLED);
                }
            });
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            requested(TERMINATED);
            original.onError(t);
        }

        @Override
        public void onComplete() {
            requested(TERMINATED);
            original.onComplete();
        }

        @Override
        public void sendSignals0() {
            // Subscription can be null if this entity has been added to offloadedEntities array but not yet received
            // onSubscribe(). Some other entity can trigger the run loop and we can be here.
            final Subscription s = subscription;
            if (s == null) {
                return;
            }
            for (;;) {
                long toRequest = requestedUpdater.getAndSet(this, 0);
                if (toRequest > 0) {
                    s.request(toRequest);
                } else if (toRequest == 0) {
                    return;
                } else if (toRequest == TERMINATED) {
                    setTerminated();
                    return;
                } else if (toRequest == CANCELLED) {
                    setTerminated();
                    s.cancel();
                    return;
                } else {
                    setTerminated();
                    s.request(toRequest);
                    return;
                }
            }
        }

        private void requested(long newValue) {
            requested = newValue;
            notifyExecutor();
        }
    }

    private abstract static class AbstractOffloadedCancellable extends AbstractOffloadedEntity implements Cancellable {

        private static final byte UNAVAILABLE = 0;
        private static final byte CANCELLED = 1;
        private static final byte DONE = 2;

        private byte result;
        /**
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm#2.11">RS rule 2.11</a> ensures that
         * receiving of all Subscriber signals happen before processing of the signals. This means the thread using the
         * Cancellable sees a store to this field hence providing visibility for this field to the thread
         * invoking the Cancellable. We make sure through notifyExecutor -> sendSignal routine that the thread using
         * the Cancellable correctly publishes changes to the run-loop. Hence making Cancellable visible to the
         * run-loop.
         */
        @Nullable
        private Cancellable cancellable;

        AbstractOffloadedCancellable(ThreadBasedSignalOffloader offloader) {
            super(offloader);
        }

        final void cancellable(Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        @Override
        public final void cancel() {
            final byte oldResult = result;
            result = CANCELLED;
            // Why don't we do it atomically?
            // Intention here is to avoid notifying executor if there are repeated cancels (which are expected to be
            // NOOPs). Since Cancellable should not be shared across threads without external synchronization, for that
            // case we are assured that there is no concurrency.
            // There can still be concurrency with setDone() but then we do not care as one of cancel() and setDone()
            // will notify.
            if (oldResult == UNAVAILABLE) {
                notifyExecutor();
            }
        }

        @Override
        public final void sendSignals0() {
            final Cancellable c = cancellable;
            if (result == UNAVAILABLE || c == null) {
                return;
            }
            setTerminated();
            if (result == CANCELLED) {
                try {
                    c.cancel();
                } catch (Throwable throwable) {
                    LOGGER.error("Ignored unexpected exception sending cancel to Cancellable: {}", c, throwable);
                }
            }
        }

        final void setDone() {
            final byte oldResult = result;
            result = DONE;
            // Why don't we do it atomically?
            // Intention here is to avoid notifying executor if we are already canceled.
            // This can be concurrent with cancel() but one of them would notify.
            if (oldResult == UNAVAILABLE) {
                notifyExecutor();
            }
        }
    }

    private static final class OffloadedSingleCancellable<T> extends AbstractOffloadedCancellable
            implements SingleSource.Subscriber<T> {

        private final SingleSource.Subscriber<? super T> original;

        private OffloadedSingleCancellable(ThreadBasedSignalOffloader offloader,
                                           SingleSource.Subscriber<? super T> original) {
            super(offloader);
            this.original = original;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            cancellable(cancellable);
            original.onSubscribe(this);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            setDone();
            original.onSuccess(result);
        }

        @Override
        public void onError(Throwable t) {
            setDone();
            original.onError(t);
        }
    }

    private static final class OffloadedCompletableCancellable extends AbstractOffloadedCancellable
            implements CompletableSource.Subscriber {

        private final CompletableSource.Subscriber original;

        OffloadedCompletableCancellable(ThreadBasedSignalOffloader offloader, CompletableSource.Subscriber original) {
            super(offloader);
            this.original = original;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            cancellable(cancellable);
            original.onSubscribe(this);
        }

        @Override
        public void onComplete() {
            setDone();
            original.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            setDone();
            original.onError(t);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(@Nullable Object signal) {
        return (T) signal;
    }

    private static final class EnqueueForOffloadingFailed extends RuntimeException {
        private static final long serialVersionUID = 7000860459929007810L;

        EnqueueForOffloadingFailed(final Exception cause) {
            super(cause);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // This exception is never sent to user code.
            return this;
        }
    }
}
