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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class HttpSerializerUtils {
    private static final HttpDeserializer<Buffer> BUFFER_DESERIALIZER = new HttpDeserializer<Buffer>() {
        @Override
        public Buffer deserialize(final HttpHeaders headers, final Object payload) {
            if (payload instanceof Buffer) {
                return (Buffer) payload;
            }
            // TODO(scott): support FileRegion?
            // We can also add best effort detection if we are running on an EventLoop thread to prevent blocking.
            // It is assumed that when HttpRequests are created with a file they have access to a BufferAllocator
            // to allocate buffers if necessary.
            throw new UnsupportedHttpChunkException(payload);
        }

        @Override
        public BlockingIterable<Buffer> deserialize(final HttpHeaders headers, final BlockingIterable<?> payload) {
            return new HttpBlockingStreamingBufferFilterIterable(payload);
        }

        @Override
        public Publisher<Buffer> deserialize(final HttpHeaders headers, final Publisher<?> payload) {
            return payload.liftSynchronous(new HttpStreamingBufferFilterOperator());
        }
    };

    static Buffer getPayloadBody(HttpRequest request) {
        return request.getPayloadBody(BUFFER_DESERIALIZER);
    }

    static Buffer getPayloadBody(HttpResponse response) {
        return response.getPayloadBody(BUFFER_DESERIALIZER);
    }

    static Publisher<Buffer> getPayloadBody(StreamingHttpRequest request) {
        return request.getPayloadBody(BUFFER_DESERIALIZER);
    }

    static Publisher<Buffer> getPayloadBody(StreamingHttpResponse response) {
        return response.getPayloadBody(BUFFER_DESERIALIZER);
    }

    static BlockingIterable<Buffer> getPayloadBody(BlockingStreamingHttpRequest request) {
        return request.getPayloadBody(BUFFER_DESERIALIZER);
    }

    static BlockingIterable<Buffer> getPayloadBody(BlockingStreamingHttpResponse response) {
        return response.getPayloadBody(BUFFER_DESERIALIZER);
    }

    private static final class HttpBlockingStreamingBufferFilterIterable implements BlockingIterable<Buffer> {
        private final BlockingIterable<?> original;

        HttpBlockingStreamingBufferFilterIterable(final BlockingIterable<?> original) {
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
                this.iterator = Objects.requireNonNull(iterator);
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
                return isNextValid(iterator.next(timeoutNanos, NANOSECONDS));
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
                return nextBuffer != null || hasNext() && isNextValid(iterator.next());
            }

            @Override
            public Buffer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return getAndResetNextBuffer();
            }

            private boolean isNextValid(@Nullable Object next) {
                if (next instanceof Buffer) {
                    nextBuffer = (Buffer) next;
                    return true;
                } else if (next instanceof HttpHeaders) {
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                    return false;
                }
                throw new UnsupportedHttpChunkException(next);
            }

            private Buffer getAndResetNextBuffer() {
                assert nextBuffer != null;
                Buffer next = nextBuffer;
                this.nextBuffer = null;
                return next;
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
                }  catch (Throwable cause) {
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
                }  catch (Throwable cause) {
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
     * <pre>{@code
     *         initial sources: P<Buffer>, Single<Headers>
     *         data stream:
     *         T0, <T1, B>, <T2, B>, <T3, H> -> S<H>
     *                |        |                  |
     *                |        /                  /
     *             V----------'   V--------------'
     *         P<Buffer>, Single<Headers>
     * }</pre>
     * @param <T> The type of user state.
     */
    static final class HttpBuffersAndTrailersOperator<T, I, O> implements PublisherOperator<I, O> {
        private final Supplier<T> stateSupplier;
        private final BiFunction<I, T, O> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
        private final Single<HttpHeaders> inTrailersSingle;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpBuffersAndTrailersOperator(final Supplier<T> stateSupplier,
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

    static final class HttpObjectsAndTrailersOperator<T> implements PublisherOperator<Object, Object> {
        private final Supplier<T> stateSupplier;
        private final BiFunction<Object, T, ?> transformer;
        private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
        private final Single<HttpHeaders> inTrailersSingle;
        private final SingleProcessor<HttpHeaders> outTrailersSingle;

        HttpObjectsAndTrailersOperator(final Supplier<T> stateSupplier,
                                       final BiFunction<Object, T, ?> transformer,
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
        public Subscriber<? super Object> apply(final Subscriber<? super Object> subscriber) {
            return new PayloadAndTrailersSubscriber<>(subscriber, stateSupplier.get(), transformer, trailersTransformer,
                    inTrailersSingle, outTrailersSingle);
        }

        private static final class PayloadAndTrailersSubscriber<T> implements Subscriber<Object> {
            private final Subscriber<? super Object> subscriber;
            @Nullable
            private final T userState;
            private final BiFunction<Object, T, ?> transformer;
            private final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer;
            private final Single<HttpHeaders> inTrailersSingle;
            private final SingleProcessor<HttpHeaders> outTrailersSingle;

            PayloadAndTrailersSubscriber(final Subscriber<? super Object> subscriber,
                                         @Nullable final T userState,
                                         final BiFunction<Object, T, ?> transformer,
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
            public void onNext(final Object obj) {
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

    private static <T> void completeOutTrailerSingle(@Nullable T userState, Single<HttpHeaders> inTrailersSingle,
                                                     BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer,
                                                     SingleProcessor<HttpHeaders> outTrailersSingle,
                                                     Subscriber<?> subscriber) {
        inTrailersSingle.subscribe(new io.servicetalk.concurrent.Single.Subscriber<HttpHeaders>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
            }

            @Override
            public void onSuccess(@Nullable final HttpHeaders result) {
                final HttpHeaders outTrailers;
                try {
                    outTrailers = trailersTransformer.apply(userState, result);
                    subscriber.onComplete();
                } catch (Throwable cause) {
                    outTrailersSingle.onError(cause);
                    return;
                }
                outTrailersSingle.onSuccess(outTrailers);
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

    private static final class HttpStreamingBufferFilterOperator implements PublisherOperator<Object, Buffer> {
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
}
