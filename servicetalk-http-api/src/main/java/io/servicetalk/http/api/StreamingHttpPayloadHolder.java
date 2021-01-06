/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import static io.servicetalk.http.api.HeaderUtils.addChunkedEncoding;
import static io.servicetalk.http.api.HttpDataSourceTransformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A holder of HTTP payload and associated information.
 */
final class StreamingHttpPayloadHolder implements PayloadInfo {
    private final HttpHeaders headers;
    private final BufferAllocator allocator;
    private final DefaultPayloadInfo payloadInfo;
    private final HttpHeadersFactory headersFactory;
    private final int majorHttpVersion;
    @Nullable
    private Publisher<?> messageBody;

    StreamingHttpPayloadHolder(final HttpHeaders headers, final BufferAllocator allocator,
                               @Nullable final Publisher<?> messageBody, final DefaultPayloadInfo messageBodyInfo,
                               final HttpHeadersFactory headersFactory, final HttpProtocolVersion version) {
        assert messageBody != null || !messageBodyInfo.mayHaveTrailers();
        this.headers = requireNonNull(headers);
        this.allocator = requireNonNull(allocator);
        this.payloadInfo = requireNonNull(messageBodyInfo);
        this.headersFactory = requireNonNull(headersFactory);
        this.majorHttpVersion = version.major();
        this.messageBody = messageBody;
    }

    @SuppressWarnings("unchecked")
    Publisher<Buffer> payloadBody() {
        return messageBody == null ? empty() :
                payloadInfo.mayHaveTrailers() || !payloadInfo.onlyEmitsBuffer() ?
                        messageBody.liftSync(HttpTransportBufferFilterOperator.INSTANCE) :
                        (Publisher<Buffer>) messageBody;
    }

    @Deprecated
    Publisher<Object> payloadBodyAndTrailers() {
        if (messageBody != null && payloadInfo.mayHaveTrailers()) {
            final Publisher<?> oldMessageBody = messageBody;
            return defer(() -> {
                Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
                return mergeEnsureTrailers(oldMessageBody.liftSync(new PreserveTrailersOperator(trailersProcessor)),
                        fromSource(trailersProcessor), headersFactory).subscribeShareContext();
            });
        }
        return messageBody();
    }

    @SuppressWarnings("unchecked")
    Publisher<Object> messageBody() {
        return messageBody == null ? empty() : (Publisher<Object>) messageBody;
    }

    void payloadBody(final Publisher<Buffer> payloadBody) {
        if (this.messageBody == null) {
            this.messageBody = requireNonNull(payloadBody);
            payloadInfo.setOnlyEmitsBuffer(true);
        } else if (payloadInfo.mayHaveTrailers()) {
            final Publisher<?> oldMessageBody = messageBody;
            this.messageBody = defer(() -> {
                Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
                return merge(payloadBody.liftSync(new BridgeFlowControlAndDiscardOperator(
                                oldMessageBody.liftSync(new PreserveTrailersBufferOperator(trailersProcessor)))),
                        fromSource(trailersProcessor)).subscribeShareContext();
            });
        } else {
            this.messageBody = payloadBody.liftSync(new BridgeFlowControlAndDiscardOperator(this.messageBody));
            payloadInfo.setOnlyEmitsBuffer(true);
        }
    }

    <T> void payloadBody(final Publisher<T> payloadBody, final HttpSerializer<T> serializer) {
        payloadBody(serializer.serialize(headers, payloadBody, allocator));
    }

    <T> void transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer, HttpSerializer<T> serializer) {
        transformPayloadBody(bufPub -> serializer.serialize(headers, transformer.apply(bufPub), allocator));
    }

    void transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        if (messageBody != null && payloadInfo.mayHaveTrailers()) {
            final Publisher<?> oldMessageBody = messageBody;
            messageBody = defer(() -> {
                Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
                return merge(transformer.apply(oldMessageBody.liftSync(
                        new PreserveTrailersBufferOperator(trailersProcessor))), fromSource(trailersProcessor))
                        .subscribeShareContext();
            });
        } else {
            messageBody = requireNonNull(transformer.apply(payloadBody()));
            payloadInfo.setOnlyEmitsBuffer(true);
        }
    }

    @Deprecated
    void transformRawPayloadBody(UnaryOperator<Publisher<?>> transformer) {
        payloadInfo.setOnlyEmitsBuffer(false); // We lose type information and cannot guarantee emitting buffers.
        payloadInfo.setMayHaveTrailers(true); // We don't know if the user will insert trailers or not, assume they may.
        if (messageBody != null && payloadInfo.mayHaveTrailers()) {
            final Publisher<?> oldMessageBody = messageBody;
            messageBody = defer(() -> {
                Processor<HttpHeaders, HttpHeaders> trailersProcessor = newSingleProcessor();
                return merge(transformer.apply(oldMessageBody.liftSync(
                        new PreserveTrailersOperator(trailersProcessor))), fromSource(trailersProcessor))
                        .subscribeShareContext();
            });
        } else {
            // It is possible the resulting transformed publisher contains a trailers object, but this is not supported
            // by this API. Instead use the designated trailers transform methods.
            messageBody = transformer.apply(messageBody == null ? empty() : messageBody);
        }
    }

    void transformMessageBody(UnaryOperator<Publisher<?>> transformer) {
        payloadInfo.setOnlyEmitsBuffer(false); // We lose type information and cannot guarantee emitting buffers.
        // The transformation does not support changing the presence of trailers in the message body.
        messageBody = transformer.apply(messageBody());
    }

    <T> void transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        transformWithTrailersUnchecked(trailersTransformer, o -> (Buffer) o);
    }

    @Deprecated
    <T> void transformRaw(final TrailersTransformer<T, Object> trailersTransformer) {
        payloadInfo.setOnlyEmitsBuffer(false); // We lose type information and cannot guarantee emitting buffers.
        transformWithTrailersUnchecked(trailersTransformer, identity());
    }

    Single<PayloadAndTrailers> aggregate() {
        payloadInfo.setSafeToAggregate(true);
        return aggregatePayloadAndTrailers(messageBody(), allocator);
    }

    private <T, P> void transformWithTrailersUnchecked(final TrailersTransformer<T, P> trailersTransformer,
                                                       final Function<Object, P> caster) {
        if (majorHttpVersion == 1) {
            // This transform adds trailers, and for http/1.1 we need `transfer-encoding: chunked` not `content-length`.
            addChunkedEncoding(headers);
        }
        payloadInfo.setMayHaveTrailers(true);
        if (messageBody == null) {
            messageBody = from(trailersTransformer.payloadComplete(trailersTransformer.newState(),
                    headersFactory.newEmptyTrailers()));
        } else {
            messageBody = messageBody.scanWith(() -> new ScanWithMapper<Object, Object>() {
                        @Nullable
                        private final T state = trailersTransformer.newState();
                        @Nullable
                        private HttpHeaders trailers;

                        @Override
                        public Object mapOnNext(@Nullable final Object next) {
                            if (next instanceof HttpHeaders) {
                                if (trailers != null) {
                                    throwDuplicateTrailersException(trailers, next);
                                }
                                trailers = (HttpHeaders) next;
                                return trailersTransformer.payloadComplete(state, trailers);
                            } else if (trailers != null) {
                                throwOnNextAfterTrailersException(next);
                            }
                            return trailersTransformer.accept(state, caster.apply(requireNonNull(next)));
                        }

                        @Override
                        public Object mapOnError(final Throwable t) throws Throwable {
                            assert trailers == null;
                            trailers = headersFactory.newEmptyTrailers();
                            return trailersTransformer.catchPayloadFailure(state, t, trailers);
                        }

                        @Override
                        public Object mapOnComplete() {
                            assert trailers == null;
                            trailers = headersFactory.newEmptyTrailers();
                            return trailersTransformer.payloadComplete(state, trailers);
                        }

                        @Override
                        public boolean mapTerminal() {
                            return trailers == null;
                        }
                    });
        }
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
    public boolean onlyEmitsBuffer() {
        return payloadInfo.onlyEmitsBuffer();
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

    private static void throwOnNextAfterTrailersException(@Nullable Object o) {
        throw new IllegalStateException("trailers must be the last onNext signal, but got: " + o);
    }

    /**
     * Merge a {@link Publisher} and a {@link Single} into a single {@link Publisher}.
     * <p>
     * The transport for HTTP client connections does {@code connection.write(requestStream).merge(connection.read())}
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

    private static Publisher<Object> mergeEnsureTrailers(Publisher<?> p, Single<HttpHeaders> s,
                                                         HttpHeadersFactory headersFactory) {
        return from(p, s.map(t -> t == null ? headersFactory.newEmptyTrailers() : t).toPublisher())
                .flatMapMerge(identity(), 2);
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

        private static final class PreserveTrailersBufferSubscriber extends AbstractPreserveTrailersSubscriber<Buffer> {
            PreserveTrailersBufferSubscriber(final Subscriber<? super Buffer> target,
                                             final Processor<HttpHeaders, HttpHeaders> trailersProcessor) {
                super(target, trailersProcessor);
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
        }
    }

    private static final class PreserveTrailersOperator implements PublisherOperator<Object, Object> {
        private final Processor<HttpHeaders, HttpHeaders> trailersProcessor;
        PreserveTrailersOperator(Processor<HttpHeaders, HttpHeaders> trailersProcessor) {
            this.trailersProcessor = trailersProcessor;
        }

        @Override
        public Subscriber<? super Object> apply(
                final Subscriber<? super Object> subscriber) {
            return new PreserveTrailersSubscriber(subscriber, trailersProcessor);
        }

        private static final class PreserveTrailersSubscriber extends AbstractPreserveTrailersSubscriber<Object> {
            PreserveTrailersSubscriber(final Subscriber<? super Object> target,
                                       final Processor<HttpHeaders, HttpHeaders> trailersProcessor) {
                super(target, trailersProcessor);
            }

            @Override
            public void onNext(final Object o) {
                if (o instanceof HttpHeaders) {
                    if (trailers != null) {
                        throwDuplicateTrailersException(trailers, o);
                    }
                    trailers = (HttpHeaders) o;
                    trailersProcessor.onSuccess(trailers);
                    // Trailers must be the last element on the stream, no need to interact with the Subscription.
                } else {
                    target.onNext(o);
                }
            }
        }
    }

    private abstract static class AbstractPreserveTrailersSubscriber<T> implements Subscriber<Object> {
        final Subscriber<? super T> target;
        final Processor<HttpHeaders, HttpHeaders> trailersProcessor;
        @Nullable
        HttpHeaders trailers;

        AbstractPreserveTrailersSubscriber(final Subscriber<? super T> target,
                                           final Processor<HttpHeaders, HttpHeaders> trailersProcessor) {
            this.target = target;
            this.trailersProcessor = trailersProcessor;
        }

        @Override
        public final void onSubscribe(final PublisherSource.Subscription subscription) {
            target.onSubscribe(subscription);
        }

        @Override
        public abstract void onNext(Object o);

        @Override
        public final void onError(final Throwable t) {
            try {
                trailersProcessor.onError(t);
            } finally {
                target.onError(t);
            }
        }

        @Override
        public final void onComplete() {
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
