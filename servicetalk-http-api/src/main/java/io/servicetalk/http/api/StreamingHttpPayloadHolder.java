/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.ScanWithMapper;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpDataSourceTransformations.BridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.HttpTransportBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.ObjectBridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.PayloadAndTrailers;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpDataSourceTransformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A holder of HTTP payload and associated information.
 */
final class StreamingHttpPayloadHolder implements PayloadInfo {

    private static final Publisher<Buffer> EMPTY = empty();

    private final HttpHeaders headers;
    private final BufferAllocator allocator;
    private final DefaultPayloadInfo payloadInfo;
    private final HttpHeadersFactory headersFactory;
    @Nullable
    private Publisher<?> messageBody;

    StreamingHttpPayloadHolder(final HttpHeaders headers, final BufferAllocator allocator,
                               @Nullable final Publisher<?> messageBody, final DefaultPayloadInfo messageBodyInfo,
                               final HttpHeadersFactory headersFactory) {
        assert messageBody != null || !messageBodyInfo.mayHaveTrailers();
        this.headers = requireNonNull(headers);
        this.allocator = requireNonNull(allocator);
        this.payloadInfo = requireNonNull(messageBodyInfo);
        this.headersFactory = requireNonNull(headersFactory);
        this.messageBody = messageBody;
        payloadInfo.setEmpty(messageBody == null || messageBody == empty());
    }

    @SuppressWarnings("unchecked")
    Publisher<Buffer> payloadBody() {
        return messageBody == null ? empty() : !payloadInfo.isGenericTypeBuffer() || payloadInfo.mayHaveTrailers() ?
                messageBody.liftSync(HttpTransportBufferFilterOperator.INSTANCE) : (Publisher<Buffer>) messageBody;
    }

    @SuppressWarnings("unchecked")
    Publisher<Object> messageBody() {
        return messageBody == null ? empty() : (Publisher<Object>) messageBody;
    }

    void payloadBody(final Publisher<Buffer> payloadBody) {
        payloadInfo.setEmpty(payloadBody == EMPTY);
        if (messageBody == null) {
            messageBody = requireNonNull(payloadBody);
            payloadInfo.setGenericTypeBuffer(true);
        } else if (payloadInfo.mayHaveTrailers()) {
            final Publisher<?> oldMessageBody = messageBody;
            messageBody = defer(() -> {
                Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
                return merge(payloadBody.liftSync(new BridgeFlowControlAndDiscardOperator(
                                oldMessageBody.liftSync(new PreserveTrailersBufferOperator(trailersProcessor)))),
                        fromSource(trailersProcessor)).subscribeShareContext();
            });
        } else {
            messageBody = payloadBody.liftSync(new BridgeFlowControlAndDiscardOperator(messageBody));
            payloadInfo.setGenericTypeBuffer(true);
        }
    }

    void messageBody(Publisher<?> msgBody) {
        payloadInfo.setEmpty(messageBody == EMPTY);
        if (messageBody == null) {
            messageBody = requireNonNull(msgBody);
        } else { // discard old trailers
            messageBody = msgBody.liftSync(new ObjectBridgeFlowControlAndDiscardOperator(messageBody));
        }
        payloadInfo.setMayHaveTrailersAndGenericTypeBuffer(true);
    }

    <T> void payloadBody(final Publisher<T> payloadBody, final HttpStreamingSerializer<T> serializer) {
        payloadBody(serializer.serialize(headers, payloadBody, allocator));
        // Because #serialize(...) method may apply operators, check the original payloadBody again:
        payloadInfo.setEmpty(payloadBody == empty());
    }

    <T> void transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                  HttpStreamingSerializer<T> serializer) {
        transformPayloadBody(bufPub -> serializer.serialize(headers, transformer.apply(bufPub), allocator));
    }

    void transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        if (payloadInfo.mayHaveTrailers()) {
            assert messageBody != null;
            payloadInfo.setEmpty(false);    // transformer may add payload content
            final Publisher<?> oldMessageBody = messageBody;
            messageBody = defer(() -> {
                final Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
                final Publisher<Buffer> transformedPayloadBody = transformer.apply(oldMessageBody.liftSync(
                        new PreserveTrailersBufferOperator(trailersProcessor)));
                payloadInfo.setEmpty(transformedPayloadBody == EMPTY);
                return merge(transformedPayloadBody, fromSource(trailersProcessor)).subscribeShareContext();
            });
        } else {
            final Publisher<Buffer> transformedPayloadBody = transformer.apply(payloadBody());
            messageBody = requireNonNull(transformedPayloadBody);
            payloadInfo.setEmpty(transformedPayloadBody == EMPTY).setGenericTypeBuffer(true);
        }
    }

    void transformMessageBody(UnaryOperator<Publisher<?>> transformer) {
        // The transformation does not support changing the presence of trailers in the message body or the type of
        // Publisher, or its content (e.g. payloadInfo assumed not impacted).
        messageBody = transformer.apply(messageBody());
    }

    <T, S> void transform(final TrailersTransformer<T, S> trailersTransformer,
                          final HttpStreamingDeserializer<S> serializer) {
        transform(trailersTransformer, body -> defer(() -> {
            final Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
            final Publisher<Buffer> transformedPayloadBody = body.liftSync(
                    new PreserveTrailersBufferOperator(trailersProcessor));
            return merge(serializer.deserialize(headers, transformedPayloadBody, allocator),
                    fromSource(trailersProcessor)).scanWith(() ->
                            new TrailersMapper<>(trailersTransformer, headersFactory))
                    .subscribeShareContext();
        }));
    }

    <T> void transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        transform(trailersTransformer,
                body -> body.scanWith(() -> new TrailersMapper<>(trailersTransformer, headersFactory)));
    }

    private <T, S> void transform(final TrailersTransformer<T, S> trailersTransformer,
                                  final Function<Publisher<?>, Publisher<?>> internalTransformer) {
        if (messageBody == null) {
            messageBody = defer(() ->
                    from(trailersTransformer.payloadComplete(trailersTransformer.newState(),
                            headersFactory.newEmptyTrailers())).subscribeShareContext());
        } else {
            payloadInfo.setEmpty(false); // transformer may add payload content
            messageBody = internalTransformer.apply(messageBody);
        }
        payloadInfo.setMayHaveTrailersAndGenericTypeBuffer(true);
    }

    Single<PayloadAndTrailers> aggregate() {
        payloadInfo.setSafeToAggregate(true);
        return aggregatePayloadAndTrailers(payloadInfo, messageBody(), allocator);
    }

    @Override
    public boolean isEmpty() {
        return payloadInfo.isEmpty();
    }

    @Override
    public boolean isSafeToAggregate() {
        return payloadInfo.isSafeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return payloadInfo.mayHaveTrailers();
    }

    @Override
    public boolean isGenericTypeBuffer() {
        return payloadInfo.isGenericTypeBuffer();
    }

    BufferAllocator allocator() {
        return allocator;
    }

    HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final StreamingHttpPayloadHolder that = (StreamingHttpPayloadHolder) o;
        if (!headers.equals(that.headers)) {
            return false;
        }
        if (!allocator.equals(that.allocator)) {
            return false;
        }
        if (!payloadInfo.equals(that.payloadInfo)) {
            return false;
        }
        if (!headersFactory.equals(that.headersFactory)) {
            return false;
        }
        return Objects.equals(messageBody, that.messageBody);
    }

    @Override
    public int hashCode() {
        int result = headers.hashCode();
        result = 31 * result + allocator.hashCode();
        result = 31 * result + payloadInfo.hashCode();
        result = 31 * result + headersFactory.hashCode();
        result = 31 * result + Objects.hashCode(messageBody);
        return result;
    }

    private static void throwDuplicateTrailersException(HttpHeaders trailers, Object o) {
        throw new IllegalStateException("trailers already set to: " + trailers +
                " but duplicate trailers seen: " + o);
    }

    private static void throwOnNextAfterTrailersException(HttpHeaders trailers, @Nullable Object o) {
        throw new IllegalStateException("trailers must be the last onNext signal, but got: " + o + " after: " +
                trailers);
    }

    /**
     * Merge a {@link Publisher} and a {@link Single} into a single {@link Publisher}.
     * <p>
     * The HTTP client connection does {@code connection.write(requestStream).mergeDelayError(connection.read())}
     * in order to propagate any errors that may occur on the write (see NonPipelinedStreamingHttpConnection and
     * NettyPipelinedConnection). This means that the read and write stream must complete (successfully) before
     * the {@link Publisher#concat(Single)} operator will execute and trailers can be processed. However in some cases
     * (e.g. gRPC) trailer processing may result in pre-mature termination of the stream (e.g. trailers indicate error).
     * So the {@link Publisher#concat(Single)} sequencing won't work on the client.
     * @param p The {@link Publisher} to merge.
     * @param s The {@link Single} to merge.
     * @return The result of the merge operation.
     */
    private static Publisher<?> merge(Publisher<?> p, Single<HttpHeaders> s) {
        // We filter null from the Single in case the publisher completes and we didn't find trailers.
        return from(p, s.toPublisher().filter(Objects::nonNull)).flatMapMerge(identity(), 2);
    }

    private static final class TrailersMapper<T, S> implements ScanWithMapper<Object, Object> {
        private final TrailersTransformer<T, S> trailersTransformer;
        private final HttpHeadersFactory headersFactory;
        @Nullable
        private final T state;
        @Nullable
        private HttpHeaders trailers;

        private TrailersMapper(final TrailersTransformer<T, S> trailersTransformer,
                               final HttpHeadersFactory headersFactory) {
            this.trailersTransformer = requireNonNull(trailersTransformer);
            this.headersFactory = headersFactory;
            state = trailersTransformer.newState();
        }

        @Override
        public Object mapOnNext(@Nullable final Object next) {
            if (next instanceof HttpHeaders) {
                if (trailers != null) {
                    throwDuplicateTrailersException(trailers, next);
                }
                trailers = (HttpHeaders) next;
                return trailersTransformer.payloadComplete(state, trailers);
            } else if (trailers != null) {
                throwOnNextAfterTrailersException(trailers, next);
            }
            @SuppressWarnings("unchecked")
            final S nextS = (S) requireNonNull(next);
            return trailersTransformer.accept(state, nextS);
        }

        @Override
        public Object mapOnError(final Throwable t) throws Throwable {
            return trailersTransformer.catchPayloadFailure(state, t, headersFactory.newEmptyTrailers());
        }

        @Override
        public Object mapOnComplete() {
            return trailersTransformer.payloadComplete(state, headersFactory.newEmptyTrailers());
        }

        @Override
        public boolean mapTerminal() {
            return trailers == null;
        }
    }

    private static final class PreserveTrailersBufferOperator implements PublisherOperator<Object, Buffer> {
        private final Processor<HttpHeaders, HttpHeaders> trailersProcessor;

        private PreserveTrailersBufferOperator(Processor<HttpHeaders, HttpHeaders> trailersProcessor) {
            this.trailersProcessor = trailersProcessor;
        }

        @Override
        public Subscriber<? super Object> apply(
                final Subscriber<? super Buffer> subscriber) {
            return new PreserveTrailersBufferSubscriber(subscriber, trailersProcessor);
        }

        private static final class PreserveTrailersBufferSubscriber implements Subscriber<Object> {
            private final Subscriber<? super Buffer> target;
            private final Processor<HttpHeaders, HttpHeaders> trailersProcessor;
            @Nullable
            private HttpHeaders trailers;

            PreserveTrailersBufferSubscriber(final Subscriber<? super Buffer> target,
                                             final Processor<HttpHeaders, HttpHeaders> trailersProcessor) {
                this.target = target;
                this.trailersProcessor = trailersProcessor;
            }

            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                target.onSubscribe(subscription);
            }

            @Override
            public void onNext(final Object o) {
                if (o instanceof Buffer) {
                    target.onNext((Buffer) o);
                } else if (o instanceof HttpHeaders) {
                    if (trailers != null) {
                        throwDuplicateTrailersException(trailers, o);
                    }
                    trailers = (HttpHeaders) o;
                    trailersProcessor.onSuccess(trailers);
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                } else {
                    throw new UnsupportedHttpChunkException(o);
                }
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    trailersProcessor.onError(t);
                } finally {
                    target.onError(t);
                }
            }

            @Override
            public void onComplete() {
                try {
                    if (trailers == null) {
                        // We didn't find any trailers, terminate with null which will be filtered above in merge(..).
                        trailersProcessor.onSuccess(null);
                    }
                } finally {
                    target.onComplete();
                }
            }
        }
    }
}
