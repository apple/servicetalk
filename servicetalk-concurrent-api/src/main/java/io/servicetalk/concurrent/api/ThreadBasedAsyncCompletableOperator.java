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
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.safeCancel;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.System.arraycopy;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;

/**
 *  Asynchronous operator for @link Completable} that processes signals with thread based offloading.
 *
 * <p>This implementation uses <i>thread based</i> offloading. Signals are delivered on a single thread owned by the
 * provided {@link Executor} invoked via the {@link Executor#execute(Runnable)} which is retained from subscribe until
 * the terminal signal so that applications may assume a consistent thread will be used for all signals.
 * */
abstract class ThreadBasedAsyncCompletableOperator extends AbstractAsynchronousCompletableOperator {

    private static final int INDEX_INIT = -1;
    private static final int INDEX_OFFLOADER_TERMINATED = -2;

    private static final String UNKNOWN_EXECUTOR_THREAD_NAME = "signal-offloader-<unknown>";

    private static final AtomicIntegerFieldUpdater<ThreadBasedAsyncCompletableOperator> lastEntityIndexUpdater =
            newUpdater(ThreadBasedAsyncCompletableOperator.class, "lastEntityIndex");

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

    ThreadBasedAsyncCompletableOperator(Completable original, Executor executor) {
        this(original, executor, 2);
    }

    /**
     * New instance.
     *
     * @param executor A {@link Consumer} representing an executor and hence provides the memory visibility guarantee:
     * {@link Runnable} submitted to this consumer happens-before invoking {@link Runnable#run()} on that
     * {@link Runnable}.
     * @param expectedOffloadingEntities An approximation of the number of entities offloaded.
     */
    ThreadBasedAsyncCompletableOperator(Completable original, Executor executor,
                                        final int expectedOffloadingEntities) {
        super(original, executor);
        offloadedEntities = new OffloadedEntity[expectedOffloadingEntities];
    }

    @Override
    void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                               AsyncContextProvider contextProvider) {
        // Offload signals to the passed Subscriber making sure they are not invoked in the thread that
        // asynchronously processes signals. This is because the thread that processes the signals may have different
        // thread safety characteristics than the typical thread interacting with the execution chain.
        //
        // The AsyncContext needs to be preserved when ever we interact with the original Subscriber, so we wrap it here
        // with the original contextMap. Otherwise some other context may leak into this subscriber chain from the other
        // side of the asynchronous boundary.
        final Subscriber operatorSubscriber = new OffloadedCompletableSubscriber(this,
                contextProvider.wrapCompletableSubscriberAndCancellable(subscriber, contextMap));
        // Subscriber to use to subscribe to the original source. Since this is an asynchronous operator, it may call
        // Cancellable method from EventLoop (if the asynchronous source created/obtained inside this operator uses
        // EventLoop) which may execute blocking code on EventLoop, eg: beforeCancel(). So, we should offload
        // Cancellable method here.
        //
        // We are introducing offloading on the Subscription, which means the AsyncContext may leak if we don't save
        // and restore the AsyncContext before/after the asynchronous boundary.
        final Subscriber upstreamSubscriber = new OffloadedCompletableCancellable(this,
                apply(operatorSubscriber));
        original.delegateSubscribe(upstreamSubscriber, signalOffloader, contextMap, contextProvider);
    }

    private void deliverSignals() {
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

    @SuppressWarnings("unused")
    <T extends OffloadedEntity> T addOffloadedEntity(T offloadedEntity) {
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
                    executor.execute(this::deliverSignals);
                } catch (RejectedExecutionException re) {
                    throw new EnqueueForOffloadingFailed(re);
                }
            } else {
                executor.execute(this::deliverSignals);
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
         * <strong>This method should not throw.</strong>
         */
        void sendSignals();

        boolean isTerminated();
    }

    private abstract static class AbstractOffloadedEntity implements OffloadedEntity {

        private static final AtomicIntegerFieldUpdater<AbstractOffloadedEntity> notifyUpdater =
                newUpdater(AbstractOffloadedEntity.class, "notify");
        private boolean terminated; // only accessed from the drain thread.
        private final ThreadBasedAsyncCompletableOperator offloader;

        @SuppressWarnings("unused")
        private volatile int notify;

        AbstractOffloadedEntity(ThreadBasedAsyncCompletableOperator offloader) {
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

        AbstractOffloadedSingleValueSubscriber(ThreadBasedAsyncCompletableOperator offloader) {
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

    protected static final class OffloadedCompletableSubscriber extends AbstractOffloadedSingleValueSubscriber
            implements CompletableSource.Subscriber {

        // sendSignals0() assumes a TerminalNotification is for error, so we use a special marker for success.
        private static final Object COMPLETE_SIGNAL = new Object();
        private final CompletableSource.Subscriber original;

        OffloadedCompletableSubscriber(ThreadBasedAsyncCompletableOperator offloader,
                                       CompletableSource.Subscriber original) {
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
                sendError(throwable);
                // Cancel the Subscription as per Rule 2.13
                // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2.13
                // We may be violating the no concurrency on the Subscription rule, but the Subscriber/Subscription is
                // invalid and we make a best effort to cleanup
                safeCancel(c);
            }
        }

        @Override
        void sendSuccess(@Nullable Object signal) {
            safeOnComplete(original);
        }

        @Override
        void sendError(Throwable t) {
            safeOnError(original, t);
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

        AbstractOffloadedCancellable(ThreadBasedAsyncCompletableOperator offloader) {
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
                safeCancel(c);
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

    protected static final class OffloadedCompletableCancellable extends AbstractOffloadedCancellable
            implements CompletableSource.Subscriber {

        private final CompletableSource.Subscriber original;

        OffloadedCompletableCancellable(ThreadBasedAsyncCompletableOperator offloader,
                                        CompletableSource.Subscriber original) {
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
