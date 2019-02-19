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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleProcessor;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class HttpDataSourceTranformations {
    private HttpDataSourceTranformations() {
        // no instances
    }

    static final class SerializeBridgeFlowControlAndDiscardOperator<T> implements PublisherOperator<T, T> {
        private final Publisher<Buffer> discardedPublisher;

        SerializeBridgeFlowControlAndDiscardOperator(final Publisher<Buffer> discardedPublisher) {
            this.discardedPublisher = requireNonNull(discardedPublisher);
        }

        @Override
        public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
            return new BridgeFlowControlAndDiscardSubscriber<>(subscriber, discardedPublisher);
        }
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
            discardedPublisher.subscribe(new Subscriber<Buffer>() {
                @Override
                public void onSubscribe(final Subscription s) {
                    bridgedSubscription.setDelayedSubscription(ConcurrentSubscription.wrap(s));
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
                if (!hasNext(timeoutNanos, NANOSECONDS)) {
                    return false;
                }
                timeoutNanos -= (nanoTime() - timeStampA);
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

    static final class HttpBufferFilterOperator implements PublisherOperator<Object, Buffer> {
        static final PublisherOperator<Object, Buffer> INSTANCE = new HttpBufferFilterOperator();

        private HttpBufferFilterOperator() {
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
                } else {
                    throw new UnsupportedHttpChunkException(o);
                }
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

    static final class HttpBuffersAndTrailersIterable<T> implements BlockingIterable<Buffer> {
        private final BlockingIterable<Buffer> iterable;
        private final Supplier<T> stateSupplier;
        private final BiFunction<Buffer, T, Buffer> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans;
        private final Single<HttpHeaders> inTrailersSingle;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpBuffersAndTrailersIterable(final BlockingIterable<Buffer> iterable,
                                       final Supplier<T> stateSupplier,
                                       final BiFunction<Buffer, T, Buffer> transformer,
                                       final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans,
                                       final Single<HttpHeaders> inTrailersSingle,
                                       final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.iterable = requireNonNull(iterable);
            this.stateSupplier = requireNonNull(stateSupplier);
            this.transformer = requireNonNull(transformer);
            this.trailersTrans = requireNonNull(trailersTrans);
            this.inTrailersSingle = requireNonNull(inTrailersSingle);
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public BlockingIterator<Buffer> iterator() {
            return new PayloadAndTrailersIterator<>(iterable.iterator(),
                    stateSupplier.get(), transformer, trailersTrans, inTrailersSingle.toFuture(), outTrailersSingle);
        }

        private static final class PayloadAndTrailersIterator<T> implements BlockingIterator<Buffer> {
            private final BlockingIterator<Buffer> iterator;
            @Nullable
            private final T userState;
            private final BiFunction<Buffer, T, Buffer> transformer;
            private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans;
            private final Future<HttpHeaders> inTrailersFuture;
            private final SingleProcessor<HttpHeaders> outTrailersSingle;

            PayloadAndTrailersIterator(BlockingIterator<Buffer> iterator,
                                       @Nullable T userState,
                                       BiFunction<Buffer, T, Buffer> transformer,
                                       BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans,
                                       Future<HttpHeaders> inTrailersFuture,
                                       SingleProcessor<HttpHeaders> outTrailersSingle) {
                this.iterator = requireNonNull(iterator);
                this.userState = userState;
                this.transformer = transformer;
                this.trailersTrans = trailersTrans;
                this.inTrailersFuture = inTrailersFuture;
                this.outTrailersSingle = outTrailersSingle;
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                try {
                    return iterator.hasNext(timeout, unit);
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }

            @Nullable
            @Override
            public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                try {
                    long timeoutNanos = unit.toNanos(timeout);
                    final long timeStampA = nanoTime();
                    Buffer next = transformer.apply(iterator.next(timeoutNanos, NANOSECONDS), userState);
                    final long timeStampB = nanoTime();
                    timeoutNanos -= timeStampB - timeStampA;
                    if (!iterator.hasNext(timeoutNanos, NANOSECONDS)) {
                        timeoutNanos -= nanoTime() - timeStampB;
                        outTrailersSingle.onSuccess(trailersTrans.apply(userState,
                                inTrailersFuture.get(timeoutNanos, NANOSECONDS)));
                    }

                    return next;
                } catch (InterruptedException | ExecutionException e) {
                    CompletionException completionException = new CompletionException(e);
                    outTrailersSingle.onError(completionException);
                    throw completionException;
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }

            @Override
            public void close() throws Exception {
                try {
                    iterator.close();
                } finally {
                    outTrailersSingle.onError(new IOException("iterator closed"));
                }
            }

            @Override
            public boolean hasNext() {
                try {
                    return iterator.hasNext();
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }

            @Override
            public Buffer next() {
                try {
                    Buffer next = transformer.apply(iterator.next(), userState);
                    if (!iterator.hasNext()) {
                        final HttpHeaders trailersOut;
                        try {
                            trailersOut = trailersTrans.apply(userState, inTrailersFuture.get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new CompletionException(e);
                        }
                        outTrailersSingle.onSuccess(trailersOut);
                    }
                    return next;
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }
        }
    }

    static final class HttpObjectsAndTrailersIterable<T> implements BlockingIterable<Object> {
        private final BlockingIterable<?> iterable;
        private final Supplier<T> stateSupplier;
        private final BiFunction<Object, T, ?> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans;
        private final Single<HttpHeaders> inTrailersSingle;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpObjectsAndTrailersIterable(final BlockingIterable<?> iterable,
                                       final Supplier<T> stateSupplier,
                                       final BiFunction<Object, T, ?> transformer,
                                       final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans,
                                       final Single<HttpHeaders> inTrailersSingle,
                                       final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.iterable = requireNonNull(iterable);
            this.stateSupplier = requireNonNull(stateSupplier);
            this.transformer = requireNonNull(transformer);
            this.trailersTrans = requireNonNull(trailersTrans);
            this.inTrailersSingle = requireNonNull(inTrailersSingle);
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public BlockingIterator<Object> iterator() {
            return new PayloadAndTrailersIterator<>(iterable.iterator(),
                    stateSupplier.get(), transformer, trailersTrans, inTrailersSingle.toFuture(), outTrailersSingle);
        }

        private static final class PayloadAndTrailersIterator<T> implements BlockingIterator<Object> {
            private final BlockingIterator<?> iterator;
            @Nullable
            private final T userState;
            private final BiFunction<Object, T, ?> transformer;
            private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans;
            private final Future<HttpHeaders> inTrailersFuture;
            private final SingleProcessor<HttpHeaders> outTrailersSingle;

            PayloadAndTrailersIterator(BlockingIterator<?> iterator,
                                       @Nullable T userState,
                                       BiFunction<Object, T, ?> transformer,
                                       BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans,
                                       Future<HttpHeaders> inTrailersFuture,
                                       SingleProcessor<HttpHeaders> outTrailersSingle) {
                this.iterator = requireNonNull(iterator);
                this.userState = userState;
                this.transformer = transformer;
                this.trailersTrans = trailersTrans;
                this.inTrailersFuture = inTrailersFuture;
                this.outTrailersSingle = outTrailersSingle;
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                try {
                    return iterator.hasNext(timeout, unit);
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }

            @Nullable
            @Override
            public Object next(final long timeout, final TimeUnit unit) throws TimeoutException {
                try {
                    long timeoutNanos = unit.toNanos(timeout);
                    final long timeStampA = nanoTime();
                    Object next = transformer.apply(iterator.next(timeoutNanos, NANOSECONDS), userState);
                    final long timeStampB = nanoTime();
                    timeoutNanos -= timeStampB - timeStampA;
                    if (!iterator.hasNext(timeoutNanos, NANOSECONDS)) {
                        timeoutNanos -= nanoTime() - timeStampB;
                        outTrailersSingle.onSuccess(trailersTrans.apply(userState,
                                inTrailersFuture.get(timeoutNanos, NANOSECONDS)));
                    }

                    return next;
                } catch (InterruptedException | ExecutionException e) {
                    CompletionException completionException = new CompletionException(e);
                    outTrailersSingle.onError(completionException);
                    throw completionException;
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }

            @Override
            public void close() throws Exception {
                try {
                    iterator.close();
                } finally {
                    outTrailersSingle.onError(new IOException("iterator closed"));
                }
            }

            @Override
            public boolean hasNext() {
                try {
                    return iterator.hasNext();
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }

            @Override
            public Object next() {
                try {
                    Object next = transformer.apply(iterator.next(), userState);
                    if (!iterator.hasNext()) {
                        final HttpHeaders trailersOut;
                        try {
                            trailersOut = trailersTrans.apply(userState, inTrailersFuture.get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new CompletionException(e);
                        }
                        outTrailersSingle.onSuccess(trailersOut);
                    }
                    return next;
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    throw cause;
                }
            }
        }
    }

    /**
     * This operator is a hack from a RS perspective and is necessary because of how our APIs decouple the payload
     * {@link Publisher} from the trailers {@link Single}. The hack part is because there is state maintained external
     * the {@link Subscriber} implementation, and so this can't be re-subscribed or repeated.
     */
    static final class HttpBufferTrailersSpliceOperator implements PublisherOperator<Object, Buffer> {
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpBufferTrailersSpliceOperator(final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Buffer> subscriber) {
            return new JustBufferSubscriber(subscriber, outTrailersSingle);
        }

        private static final class JustBufferSubscriber extends AbstractJustBufferSubscriber<Buffer> {
            JustBufferSubscriber(final Subscriber<? super Buffer> target,
                                 final SingleProcessor<HttpHeaders> outTrailersSingle) {
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
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpObjectTrailersSpliceOperator(final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Object> subscriber) {
            return new JustBufferSubscriber(subscriber, outTrailersSingle);
        }

        private static final class JustBufferSubscriber extends AbstractJustBufferSubscriber<Object> {
            JustBufferSubscriber(final Subscriber<? super Object> target,
                                 final SingleProcessor<HttpHeaders> outTrailersSingle) {
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
        final SingleProcessor<HttpHeaders> outTrailersSingle;
        @Nullable
        HttpHeaders trailers;

        AbstractJustBufferSubscriber(final Subscriber<? super T> target,
                                     final SingleProcessor<HttpHeaders> outTrailersSingle) {
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

    static final class HttpRawBuffersAndTrailersOperator<T> implements PublisherOperator<Object, Buffer> {
        private final Supplier<T> stateSupplier;
        private final BiFunction<Buffer, T, Buffer> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpRawBuffersAndTrailersOperator(final Supplier<T> stateSupplier,
                                          final BiFunction<Buffer, T, Buffer> transformer,
                                          final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                          final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.stateSupplier = requireNonNull(stateSupplier);
            this.transformer = requireNonNull(transformer);
            this.trailersTransformer = requireNonNull(trailersTransformer);
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Buffer> subscriber) {
            return new PayloadAndTrailersSubscriber<>(subscriber, stateSupplier.get(), transformer, trailersTransformer,
                    outTrailersSingle);
        }

        private static final class PayloadAndTrailersSubscriber<T> implements Subscriber<Object> {
            private final Subscriber<? super Buffer> subscriber;
            @Nullable
            private final T userState;
            private final BiFunction<Buffer, T, Buffer> transformer;
            private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
            private final SingleProcessor<HttpHeaders> outTrailersSingle;
            @Nullable
            private HttpHeaders trailers;

            private PayloadAndTrailersSubscriber(final Subscriber<? super Buffer> subscriber,
                                                 @Nullable final T userState,
                                                 final BiFunction<Buffer, T, Buffer> transformer,
                                                 final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                                 final SingleProcessor<HttpHeaders> outTrailersSingle) {
                this.subscriber = subscriber;
                this.userState = userState;
                this.transformer = transformer;
                this.trailersTransformer = trailersTransformer;
                this.outTrailersSingle = outTrailersSingle;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(final Object obj) {
                if (obj instanceof Buffer) {
                    subscriber.onNext(transformer.apply((Buffer) obj, userState));
                } else if (obj instanceof HttpHeaders) {
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                    if (trailers != null) {
                        throwDuplicateTrailersException(trailers, obj);
                    }
                    trailers = (HttpHeaders) obj;
                } else if (trailers != null) {
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                    throwDuplicateTrailersException(trailers, obj);
                } else {
                    trailers = (HttpHeaders) obj;
                }
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    subscriber.onError(t);
                } finally {
                    outTrailersSingle.onError(t);
                }
            }

            @Override
            public void onComplete() {
                completeOutTrailerSingle(userState, trailers, trailersTransformer, outTrailersSingle, subscriber);
            }
        }
    }

    static final class HttpRawObjectsAndTrailersOperator<T> implements PublisherOperator<Object, Object> {
        private final Supplier<T> stateSupplier;
        private final BiFunction<Object, T, ?> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpRawObjectsAndTrailersOperator(final Supplier<T> stateSupplier,
                                          final BiFunction<Object, T, ?> transformer,
                                          final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                          final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.stateSupplier = requireNonNull(stateSupplier);
            this.transformer = requireNonNull(transformer);
            this.trailersTransformer = requireNonNull(trailersTransformer);
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super Object> apply(final Subscriber<? super Object> subscriber) {
            return new PayloadAndTrailersSubscriber<>(subscriber, stateSupplier.get(), transformer, trailersTransformer,
                    outTrailersSingle);
        }

        private static final class PayloadAndTrailersSubscriber<T> implements Subscriber<Object> {
            private final Subscriber<? super Object> subscriber;
            @Nullable
            private final T userState;
            private final BiFunction<Object, T, ?> transformer;
            private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
            private final SingleProcessor<HttpHeaders> outTrailersSingle;
            @Nullable
            private HttpHeaders trailers;

            private PayloadAndTrailersSubscriber(final Subscriber<? super Object> subscriber,
                                                 @Nullable final T userState,
                                                 final BiFunction<Object, T, ?> transformer,
                                                 final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                                 final SingleProcessor<HttpHeaders> outTrailersSingle) {
                this.subscriber = subscriber;
                this.userState = userState;
                this.transformer = transformer;
                this.trailersTransformer = trailersTransformer;
                this.outTrailersSingle = outTrailersSingle;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(final Object obj) {
                if (obj instanceof HttpHeaders) {
                    if (trailers != null) {
                        throwDuplicateTrailersException(trailers, obj);
                    }
                    this.trailers = (HttpHeaders) obj;
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                } else {
                    subscriber.onNext(transformer.apply(obj, userState));
                }
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    subscriber.onError(t);
                } finally {
                    outTrailersSingle.onError(t);
                }
            }

            @Override
            public void onComplete() {
                completeOutTrailerSingle(userState, trailers, trailersTransformer, outTrailersSingle, subscriber);
            }
        }
    }

    /**
     * This operator is a hack from a RS perspective and is necessary because of how our APIs decouple the payload
     * {@link Publisher} from the trailers {@link Single}. The hack part is because there is state maintained external
     * the {@link Subscriber} implementation, and so this can't be re-subscribed or repeated.
     * <pre>{@code
     *         initial sources: P<Buffer>, Single<Headers>
     *         data stream:
     *         B, B, B, H
     *            |     |
     *            |     |
     *            V     `--------V
     *         P<Buffer>, Single<Headers>
     * }</pre>
     * @param <T> The type of user state.
     */
    static final class HttpPayloadAndTrailersFromSingleOperator<T, I, O> implements PublisherOperator<I, O> {
        private final Supplier<T> stateSupplier;
        private final BiFunction<I, T, O> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
        private final Single<HttpHeaders> inTrailersSingle;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpPayloadAndTrailersFromSingleOperator(final Supplier<T> stateSupplier,
                                                 final BiFunction<I, T, O> transformer,
                                                 final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                                 final Single<HttpHeaders> inTrailersSingle,
                                                 final SingleProcessor<HttpHeaders> outTrailersSingle) {
            this.stateSupplier = requireNonNull(stateSupplier);
            this.transformer = requireNonNull(transformer);
            this.trailersTransformer = requireNonNull(trailersTransformer);
            this.inTrailersSingle = requireNonNull(inTrailersSingle);
            this.outTrailersSingle = requireNonNull(outTrailersSingle);
        }

        @Override
        public Subscriber<? super I> apply(final Subscriber<? super O> subscriber) {
            return new PayloadAndTrailersSubscriber<>(subscriber, stateSupplier.get(), transformer, trailersTransformer,
                    inTrailersSingle, outTrailersSingle);
        }

        private static final class PayloadAndTrailersSubscriber<T, I, O> implements Subscriber<I> {
            private final Subscriber<? super O> subscriber;
            @Nullable
            private final T userState;
            private final BiFunction<I, T, O> transformer;
            private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
            private final Single<HttpHeaders> inTrailersSingle;
            private final SingleProcessor<HttpHeaders> outTrailersSingle;

            PayloadAndTrailersSubscriber(final Subscriber<? super O> subscriber,
                                         @Nullable final T userState,
                                         final BiFunction<I, T, O> transformer,
                                         final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                         final Single<HttpHeaders> inTrailersSingle,
                                         final SingleProcessor<HttpHeaders> outTrailersSingle) {
                this.subscriber = subscriber;
                this.userState = userState;
                this.transformer = transformer;
                this.trailersTransformer = trailersTransformer;
                this.inTrailersSingle = inTrailersSingle;
                this.outTrailersSingle = outTrailersSingle;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(final I obj) {
                subscriber.onNext(transformer.apply(obj, userState));
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    subscriber.onError(t);
                } finally {
                    outTrailersSingle.onError(t);
                }
            }

            @Override
            public void onComplete() {
                completeOutTrailerSingle(userState, inTrailersSingle, trailersTransformer, outTrailersSingle,
                        subscriber);
            }
        }
    }

    static final class PayloadAndTrailers {
        final CompositeBuffer compositeBuffer;
        @Nullable
        HttpHeaders trailers;

        PayloadAndTrailers(CompositeBuffer compositeBuffer) {
            this.compositeBuffer = compositeBuffer;
        }
    }

    static Single<PayloadAndTrailers> aggregatePayloadAndTrailers(Publisher<Object> payloadAndTrailers,
                                                                  BufferAllocator allocator) {
        return payloadAndTrailers.reduce(() ->
                        new PayloadAndTrailers(allocator.newCompositeBuffer(MAX_VALUE)),
                (pair, obj) -> {
                    if (obj instanceof Buffer) {
                        pair.compositeBuffer.addBuffer((Buffer) obj);
                    } else if (obj instanceof HttpHeaders) {
                        pair.trailers = (HttpHeaders) obj;
                    } else {
                        throw new UnsupportedHttpChunkException(obj);
                    }
                    return pair;
                });
    }

    static UnaryOperator<BlockingIterable<Buffer>> consumeOldPayloadBody(final BlockingIterable<Buffer> payloadBody) {
        // Ignore content of original Publisher (payloadBody). Due to the blocking APIs we will consume the payload in
        // a blocking fashion, this default behavior can be overriden via the method overload and may change to be in
        // parallel later.
        return old -> {
            consumeBlockingIterator(old.iterator());
            return payloadBody;
        };
    }

    static <T> Function<BlockingIterable<Buffer>, BlockingIterable<T>> consumeOldPayloadBodySerialized(
            final BlockingIterable<T> payloadBody) {
        // Ignore content of original Publisher (payloadBody). Due to the blocking APIs we will consume the payload in
        // a blocking fashion, this default behavior can be overriden via the method overload and may change to be in
        // parallel later.
        return old -> {
            consumeBlockingIterator(old.iterator());
            return payloadBody;
        };
    }

    private static <T> void consumeBlockingIterator(BlockingIterator<T> itr) {
        try {
            itr.forEachRemaining(buffer -> { });
        } finally {
            try {
                itr.close();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static <T> void completeOutTrailerSingle(@Nullable T userState, Single<HttpHeaders> inTrailersSingle,
                                                     BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                                     SingleProcessor<HttpHeaders> outTrailersSingle,
                                                     Subscriber<?> subscriber) {
        inTrailersSingle.subscribe(new SingleSource.Subscriber<HttpHeaders>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
            }

            @Override
            public void onSuccess(@Nullable final HttpHeaders inHeaders) {
                completeOutTrailerSingle(userState, inHeaders, trailersTransformer, outTrailersSingle, subscriber);
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    subscriber.onComplete();
                } finally {
                    outTrailersSingle.onError(t);
                }
            }
        });
    }

    private static <T> void completeOutTrailerSingle(@Nullable T userState, @Nullable HttpHeaders inHeaders,
                                                     BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                                     SingleProcessor<HttpHeaders> outTrailersSingle,
                                                     Subscriber<?> subscriber) {
        if (inHeaders == null) {
            try {
                subscriber.onComplete();
            } finally {
                outTrailersSingle.onError(newTrailersExpectedButNotSeenException());
            }
        } else {
            final HttpHeaders outTrailers;
            try {
                outTrailers = trailersTransformer.apply(userState, inHeaders);
                subscriber.onComplete();
            } catch (Throwable cause) {
                outTrailersSingle.onError(cause);
                return;
            }
            outTrailersSingle.onSuccess(outTrailers);
        }
    }
}
