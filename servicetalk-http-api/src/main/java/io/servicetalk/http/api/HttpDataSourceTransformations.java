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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;

import java.nio.BufferOverflowException;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class HttpDataSourceTransformations {

    private static final PayloadAndTrailers EMPTY_PAYLOAD_AND_TRAILERS = new PayloadAndTrailers();

    private HttpDataSourceTransformations() {
        // no instances
    }

    static final class ObjectBridgeFlowControlAndDiscardOperator extends
                                                                 AbstractBridgeFlowControlAndDiscardOperator<Object> {
        ObjectBridgeFlowControlAndDiscardOperator(final Publisher<?> discardedPublisher) {
            super(discardedPublisher);
        }
    }

    static final class BridgeFlowControlAndDiscardOperator extends AbstractBridgeFlowControlAndDiscardOperator<Buffer> {
        BridgeFlowControlAndDiscardOperator(final Publisher<?> discardedPublisher) {
            super(discardedPublisher);
        }
    }

    private abstract static class AbstractBridgeFlowControlAndDiscardOperator<T> implements PublisherOperator<T, T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<AbstractBridgeFlowControlAndDiscardOperator> appliedUpdater =
                newUpdater(AbstractBridgeFlowControlAndDiscardOperator.class, "applied");
        private volatile int applied;
        private final Publisher<?> discardedPublisher;

        AbstractBridgeFlowControlAndDiscardOperator(final Publisher<?> discardedPublisher) {
            this.discardedPublisher = requireNonNull(discardedPublisher);
        }

        @Override
        public final Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
            // We only need to subscribe to and drain contents from original Publisher a single time.
            return appliedUpdater.compareAndSet(this, 0, 1) ?
                    new BridgeFlowControlAndDiscardSubscriber<>(subscriber, discardedPublisher) : subscriber;
        }
    }

    private static final class BridgeFlowControlAndDiscardSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> target;
        private final DelayedSubscription bridgedSubscription;
        @Nullable
        private Subscription outerSubscription;

        BridgeFlowControlAndDiscardSubscriber(final Subscriber<? super T> target,
                                              final Publisher<?> discardedPublisher) {
            this.target = target;
            bridgedSubscription = new DelayedSubscription();
            toSource(discardedPublisher).subscribe(new Subscriber<Object>() {
                @Override
                public void onSubscribe(final Subscription s) {
                    bridgedSubscription.delayedSubscription(ConcurrentSubscription.wrap(s));
                }

                @Override
                public void onNext(final Object buffer) {
                }

                @Override
                public void onError(final Throwable t) {
                }

                @Override
                public void onComplete() {
                }
            });
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (checkDuplicateSubscription(outerSubscription, s)) {
                outerSubscription = new Subscription() {
                    @Override
                    public void request(final long n) {
                        try {
                            s.request(n);
                        } finally {
                            bridgedSubscription.request(n);
                        }
                    }

                    @Override
                    public void cancel() {
                        try {
                            s.cancel();
                        } finally {
                            bridgedSubscription.cancel();
                        }
                    }
                };
                target.onSubscribe(outerSubscription);
            }
        }

        @Override
        public void onNext(final T t) {
            target.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            try {
                target.onError(t);
            } finally {
                // TODO(scott): we should consider making this policy more sophisticated and rate limit the amount
                // of data we ingest from the original publisher. For now we apply a "best effort" policy and
                // assume the original publisher is of similar cardinality to the new publisher.
                bridgedSubscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onComplete() {
            try {
                target.onComplete();
            } finally {
                bridgedSubscription.request(Long.MAX_VALUE);
            }
        }
    }

    static final class HttpBufferFilterIterable implements BlockingIterable<Buffer> {
        private final BlockingIterable<?> original;

        HttpBufferFilterIterable(final BlockingIterable<?> original) {
            this.original = original;
        }

        @Override
        public BlockingIterator<Buffer> iterator() {
            return new JustBufferBlockingIterator(original.iterator());
        }

        private static final class JustBufferBlockingIterator implements BlockingIterator<Buffer> {
            private final BlockingIterator<?> iterator;
            @Nullable
            private Buffer nextBuffer;

            JustBufferBlockingIterator(BlockingIterator<?> iterator) {
                this.iterator = requireNonNull(iterator);
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                if (nextBuffer != null) {
                    return true;
                }
                long timeoutNanos = unit.toNanos(timeout);
                final long timeStampA = nanoTime();
                if (!iterator.hasNext(timeoutNanos, NANOSECONDS)) {
                    return false;
                }
                timeoutNanos -= nanoTime() - timeStampA;
                return validateNext(iterator.next(timeoutNanos, NANOSECONDS));
            }

            @Override
            public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                if (!hasNext(timeout, unit)) {
                    throw new NoSuchElementException();
                }
                return getAndResetNextBuffer();
            }

            @Override
            public void close() throws Exception {
                nextBuffer = null;
                iterator.close();
            }

            @Override
            public boolean hasNext() {
                return nextBuffer != null || iterator.hasNext() && validateNext(iterator.next());
            }

            @Override
            public Buffer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return getAndResetNextBuffer();
            }

            private boolean validateNext(@Nullable Object next) {
                if (next instanceof Buffer) {
                    nextBuffer = (Buffer) next;
                    return true;
                }
                final UnsupportedHttpChunkException e = new UnsupportedHttpChunkException(next);
                try {
                    iterator.close();
                } catch (Throwable cause) {
                    e.addSuppressed(cause);
                }
                throw e;
            }

            private Buffer getAndResetNextBuffer() {
                assert nextBuffer != null;
                Buffer next = nextBuffer;
                this.nextBuffer = null;
                return next;
            }
        }
    }

    static final class HttpTransportBufferFilterOperator implements PublisherOperator<Object, Buffer> {
        static final PublisherOperator<Object, Buffer> INSTANCE = new HttpTransportBufferFilterOperator();

        private HttpTransportBufferFilterOperator() {
            // singleton
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Buffer> subscriber) {
            return new JustBufferSubscriber(subscriber);
        }

        private static final class JustBufferSubscriber implements Subscriber<Object> {
            private final Subscriber<? super Buffer> subscriber;

            JustBufferSubscriber(final Subscriber<? super Buffer> target) {
                this.subscriber = target;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(final Object o) {
                if (o instanceof Buffer) {
                    subscriber.onNext((Buffer) o);
                } else if (!(o instanceof HttpHeaders)) {
                    throw new UnsupportedHttpChunkException(o);
                }
                // Trailers must be the last element on the stream, no need to interact with the Subscription.
            }

            @Override
            public void onError(final Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        }
    }

    static final class PayloadAndTrailers {
        Buffer payload = EMPTY_BUFFER;
        @Nullable
        HttpHeaders trailers;
    }

    static Single<PayloadAndTrailers> aggregatePayloadAndTrailers(final DefaultPayloadInfo payloadInfo,
                                                                  final Publisher<?> payloadAndTrailers,
                                                                  final BufferAllocator allocator) {
        if (payloadAndTrailers == empty()) {
            payloadInfo.setEmpty(true).setMayHaveTrailersAndGenericTypeBuffer(false);
            return succeeded(EMPTY_PAYLOAD_AND_TRAILERS);
        }
        return payloadAndTrailers.collect(PayloadAndTrailers::new, (pair, nextItem) -> {
            if (nextItem instanceof Buffer) {
                try {
                    Buffer buffer = (Buffer) nextItem;
                    if (isAlwaysEmpty(pair.payload)) {
                        pair.payload = buffer;
                    } else if (pair.payload instanceof CompositeBuffer) {
                        ((CompositeBuffer) pair.payload).addBuffer(buffer);
                    } else {
                        Buffer oldBuffer = pair.payload;
                        pair.payload = allocator.newCompositeBuffer(MAX_VALUE).addBuffer(oldBuffer).addBuffer(buffer);
                    }
                } catch (IllegalArgumentException cause) {
                    BufferOverflowException ex = new BufferOverflowException();
                    ex.initCause(cause);
                    throw ex;
                }
            } else if (nextItem instanceof HttpHeaders) {
                pair.trailers = (HttpHeaders) nextItem;
            } else {
                throw new UnsupportedHttpChunkException(nextItem);
            }
            return pair;
        }).map(pair -> {
            if (isAlwaysEmpty(pair.payload)) {
                payloadInfo.setEmpty(true);
            }
            if (pair.trailers == null) {
                payloadInfo.setMayHaveTrailersAndGenericTypeBuffer(false);
            }
            return pair;
        });
    }

    static boolean isAlwaysEmpty(final Buffer buffer) {
        return buffer == EMPTY_BUFFER || (buffer.isReadOnly() && buffer.readableBytes() == 0);
    }
}
