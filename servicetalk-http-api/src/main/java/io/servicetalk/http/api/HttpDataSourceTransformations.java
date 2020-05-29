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
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class HttpDataSourceTransformations {
    private HttpDataSourceTransformations() {
        // no instances
    }

    static final class BridgeFlowControlAndDiscardOperator implements PublisherOperator<Buffer, Buffer> {
        private final Publisher<Buffer> discardedPublisher;

        BridgeFlowControlAndDiscardOperator(final Publisher<Buffer> discardedPublisher) {
            this.discardedPublisher = requireNonNull(discardedPublisher);
        }

        @Override
        public Subscriber<? super Buffer> apply(final Subscriber<? super Buffer> subscriber) {
            return new BridgeFlowControlAndDiscardSubscriber<>(subscriber, discardedPublisher);
        }
    }

    private static final class BridgeFlowControlAndDiscardSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> target;
        private final DelayedSubscription bridgedSubscription;
        @Nullable
        private Subscription outerSubscription;

        BridgeFlowControlAndDiscardSubscriber(final Subscriber<? super T> target,
                                              final Publisher<Buffer> discardedPublisher) {
            this.target = target;
            bridgedSubscription = new DelayedSubscription();
            toSource(discardedPublisher).subscribe(new Subscriber<Buffer>() {
                @Override
                public void onSubscribe(final Subscription s) {
                    bridgedSubscription.delayedSubscription(ConcurrentSubscription.wrap(s));
                }

                @Override
                public void onNext(final Buffer buffer) {
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

    /**
     * This operator is a hack from a RS perspective and is necessary because of how our APIs decouple the payload
     * {@link Publisher} from the trailers {@link Single}. The hack part is because there is state maintained external
     * the {@link Subscriber} implementation, and so this can't be re-subscribed or repeated.
     */
    static final class HttpBufferTrailersSpliceOperator implements PublisherOperator<Object, Buffer> {
        private final Processor<HttpHeaders, HttpHeaders> outTrailersSingle;

        HttpBufferTrailersSpliceOperator(final Processor<HttpHeaders, HttpHeaders> outTrailersSingle) {
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Buffer> subscriber) {
            return new JustBufferSubscriber(subscriber, outTrailersSingle);
        }

        private static final class JustBufferSubscriber extends AbstractJustBufferSubscriber<Buffer> {
            JustBufferSubscriber(final Subscriber<? super Buffer> target,
                                 final Processor<HttpHeaders, HttpHeaders> outTrailersSingle) {
                super(target, outTrailersSingle);
            }

            @Override
            public void onNext(final Object o) {
                if (o instanceof Buffer) {
                    subscriber.onNext((Buffer) o);
                } else if (o instanceof HttpHeaders) {
                    if (trailers != null) {
                        throwDuplicateTrailersException(trailers, o);
                    }
                    trailers = (HttpHeaders) o;
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                } else if (trailers != null) {
                    throwDuplicateTrailersException(trailers, o);
                } else {
                    trailers = (HttpHeaders) o;
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                }
            }
        }
    }

    static final class HttpObjectTrailersSpliceOperator implements PublisherOperator<Object, Object> {
        private final Processor<HttpHeaders, HttpHeaders> outTrailersSingle;

        HttpObjectTrailersSpliceOperator(final Processor<HttpHeaders, HttpHeaders> outTrailersSingle) {
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Object> subscriber) {
            return new JustBufferSubscriber(subscriber, outTrailersSingle);
        }

        private static final class JustBufferSubscriber extends AbstractJustBufferSubscriber<Object> {
            JustBufferSubscriber(final Subscriber<? super Object> target,
                                 final Processor<HttpHeaders, HttpHeaders> outTrailersSingle) {
                super(target, outTrailersSingle);
            }

            @Override
            public void onNext(final Object o) {
                if (o instanceof HttpHeaders) {
                    if (trailers != null) {
                        throwDuplicateTrailersException(trailers, o);
                    }
                    trailers = (HttpHeaders) o;
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                } else {
                    subscriber.onNext(o);
                }
            }
        }
    }

    private abstract static class AbstractJustBufferSubscriber<T> implements Subscriber<Object> {
        final Subscriber<? super T> subscriber;
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle;
        @Nullable
        HttpHeaders trailers;

        AbstractJustBufferSubscriber(final Subscriber<? super T> target,
                                     final Processor<HttpHeaders, HttpHeaders> outTrailersSingle) {
            this.subscriber = target;
            this.outTrailersSingle = outTrailersSingle;
        }

        @Override
        public final void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(s);
        }

        @Override
        public final void onError(final Throwable t) {
            try {
                subscriber.onError(t);
            } finally {
                outTrailersSingle.onError(t);
            }
        }

        @Override
        public final void onComplete() {
            try {
                subscriber.onComplete();
            } finally {
                // Delay the completion of the trailers single until after the completion of the stream to provide
                // a best effort sequencing. This ordering may break down if they sources are offloaded on different
                // threads, but from this Operator's perspective we make a best effort.
                if (trailers != null) {
                    outTrailersSingle.onSuccess(trailers);
                } else {
                    outTrailersSingle.onError(newTrailersExpectedButNotSeenException());
                }
            }
        }
    }

    static void throwDuplicateTrailersException(HttpHeaders trailers, Object o) {
        throw new IllegalStateException("trailers already set to: " + trailers +
                " but duplicate trailers seen: " + o);
    }

    static RuntimeException newTrailersExpectedButNotSeenException() {
        return new IllegalStateException("trailers were expected, but not seen");
    }

    static final class PayloadAndTrailers {
        @Nullable
        Buffer payload;
        @Nullable
        HttpHeaders trailers;
    }

    static Single<PayloadAndTrailers> aggregatePayloadAndTrailers(Publisher<Object> payloadAndTrailers,
                                                                  BufferAllocator allocator) {
        return payloadAndTrailers.collect(PayloadAndTrailers::new, (pair, nextItem) -> {
            if (nextItem instanceof Buffer) {
                try {
                    Buffer buffer = (Buffer) nextItem;
                    if (pair.payload == null) {
                        pair.payload = buffer;
                    } else if (pair.payload instanceof CompositeBuffer) {
                        ((CompositeBuffer) pair.payload).addBuffer(buffer);
                    } else {
                        Buffer oldBuffer = pair.payload;
                        pair.payload = allocator.newCompositeBuffer(MAX_VALUE).addBuffer(oldBuffer).addBuffer(buffer);
                    }
                } catch (Exception e) {
                    throw new AggregationException("Cannot aggregate payload body", e);
                }
            } else if (nextItem instanceof HttpHeaders) {
                pair.trailers = (HttpHeaders) nextItem;
            } else {
                throw new UnsupportedHttpChunkException(nextItem);
            }
            return pair;
        }).beforeOnSuccess(pair -> {
            if (pair.payload == null) {
                pair.payload = allocator.newBuffer(0, false);
            }
        });
    }

    private static final class AggregationException extends IllegalStateException {
        private static final long serialVersionUID = 1460581701241013343L;

        AggregationException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
