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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpDataSourceTransformations.BridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.HttpBufferTrailersSpliceOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.HttpObjectTrailersSpliceOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.HttpTransportBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTransformations.PayloadAndTrailers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HeaderUtils.addChunkedEncoding;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpDataSourceTransformations.aggregatePayloadAndTrailers;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static java.util.Objects.requireNonNull;

/**
 * A holder of HTTP payload and associated information.
 */
final class StreamingHttpPayloadHolder implements PayloadInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingHttpPayloadHolder.class);

    private final HttpHeaders headers;
    private final BufferAllocator allocator;
    private final DefaultPayloadInfo payloadInfo;
    private final HttpHeadersFactory headersFactory;
    private final HttpProtocolVersion version;
    @Nullable
    private Publisher<?> payloadBody;
    @Nullable
    private Single<HttpHeaders> trailersSingle;

    StreamingHttpPayloadHolder(final HttpHeaders headers, final BufferAllocator allocator,
                               @Nullable final Publisher<?> payloadBody, final DefaultPayloadInfo payloadInfo,
                               final HttpHeadersFactory headersFactory, final HttpProtocolVersion version) {
        this.headers = requireNonNull(headers);
        this.allocator = requireNonNull(allocator);
        this.payloadInfo = requireNonNull(payloadInfo);
        this.headersFactory = requireNonNull(headersFactory);
        this.version = requireNonNull(version);
        if (payloadInfo.mayHaveTrailers()) {
            this.payloadBody = payloadBody != null ? payloadBody : empty();
        } else if (payloadBody != null) {
            this.payloadBody = filterTrailers(payloadBody);
        }
    }

    Publisher<Buffer> payloadBody() {
        if (payloadBody == null) {
            return empty();
        }
        splitTrailersIfRequired();
        return payloadInfo.onlyEmitsBuffer() ? bufferPayload() :
                rawPayload().liftSync(HttpTransportBufferFilterOperator.INSTANCE);
    }

    Publisher<Object> payloadBodyAndTrailers() {
        if (payloadInfo.mayHaveTrailers()) {
            assert payloadBody != null;
            // If trailers are not yet split, the original Publisher will still have them.
            return trailersSingle == null ? rawPayload() : rawPayload().concat(trailersSingle);
        }
        if (isTransferEncodingChunked(headers)) {
            // explicitly added chunked encoding, we should add trailers explicitly.
            return payloadBody == null ? from(headersFactory.newEmptyTrailers()) :
                    rawPayload().concat(succeeded(headersFactory.newEmptyTrailers()));
        }
        return emptyOrRawPayload();
    }

    public void payloadBody(final Publisher<Buffer> payloadBody) {
        updatePayloadBody(payloadBody, false);
    }

    public <T> void payloadBody(final Publisher<T> payloadBody, final HttpSerializer<T> serializer) {
        payloadBody(serializer.serialize(headers, payloadBody, allocator));
    }

    public <T> void transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                         HttpSerializer<T> serializer) {
        updatePayloadBody(serializer.serialize(headers, transformer.apply(payloadBody()), allocator), true);
    }

    public void transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        updatePayloadBody(transformer.apply(payloadBody()), true);
    }

    public void transformRawPayloadBody(UnaryOperator<Publisher<?>> transformer) {
        if (payloadBody != null) {
            splitTrailersIfRequired();
        }
        payloadBody = transformer.apply(emptyOrRawPayload());
        payloadInfo.setSafeToAggregate(false);
    }

    public <T> void transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        transformWithTrailersUnchecked(false, trailersTransformer);
    }

    public <T> void transformRaw(final TrailersTransformer<T, Object> trailersTransformer) {
        transformWithTrailersUnchecked(true, trailersTransformer);
    }

    Single<PayloadAndTrailers> aggregate() {
        payloadInfo.setSafeToAggregate(true);
        return aggregatePayloadAndTrailers(payloadBodyAndTrailers(), allocator);
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
        if (payloadBody != null ? !payloadBody.equals(that.payloadBody) : that.payloadBody != null) {
            return false;
        }
        return trailersSingle != null ? trailersSingle.equals(that.trailersSingle) : that.trailersSingle == null;
    }

    @Override
    public int hashCode() {
        int result = headers.hashCode();
        result = 31 * result + allocator.hashCode();
        result = 31 * result + payloadInfo.hashCode();
        result = 31 * result + headersFactory.hashCode();
        result = 31 * result + (payloadBody != null ? payloadBody.hashCode() : 0);
        result = 31 * result + (trailersSingle != null ? trailersSingle.hashCode() : 0);
        return result;
    }

    private void splitTrailersIfRequired() {
        assert payloadBody != null;
        if (payloadInfo.mayHaveTrailers() && trailersSingle == null) {
            Processor<HttpHeaders, HttpHeaders> trailersAsProcessor = newSingleProcessor();
            trailersSingle = fromSource(trailersAsProcessor);
            if (payloadInfo.onlyEmitsBuffer()) {
                payloadBody = bufferPayload().liftSync(new HttpBufferTrailersSpliceOperator(trailersAsProcessor));
            } else {
                payloadBody = rawPayload().liftSync(new HttpObjectTrailersSpliceOperator(trailersAsProcessor));
            }
        }
    }

    private void updatePayloadBody(Publisher<Buffer> newPayload, boolean isTransform) {
        this.payloadBody = this.payloadBody == null || isTransform ?
                requireNonNull(newPayload) :
                // payloadBody() will split trailers if not yet split
                newPayload.liftSync(new BridgeFlowControlAndDiscardOperator(payloadBody()));
        payloadInfo.setOnlyEmitsBuffer(true);
        if (isTransform) {
            payloadInfo.setSafeToAggregate(false);
        }
    }

    @SuppressWarnings("unchecked")
    private void transformWithTrailersUnchecked(boolean raw, final TrailersTransformer trailersTransformer) {
        if (payloadBody == null) {
            Object state = trailersTransformer.newState();
            trailersSingle = succeeded(trailersTransformer.payloadComplete(state, headersFactory.newEmptyTrailers()));
            payloadBody = empty();
        } else {
            splitTrailersIfRequired();
            if (!payloadInfo.mayHaveTrailers()) {
                trailersSingle = succeeded(headersFactory.newEmptyTrailers());
            }
            assert trailersSingle != null;
            // trailersSingle will always be used in a serial fashion relative to the payloadBody. The RS operators used
            // to provide this sequential subscription will take care of visibility for the state Object when accessed
            // in a trailersSingle operation.
            final TrailerTransformerState trailerTransformerState = new TrailerTransformerState();
            trailersSingle = trailersSingle.liftSync(subscriber -> new SingleSource.Subscriber<HttpHeaders>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    subscriber.onSubscribe(cancellable);
                }

                @Override
                public void onSuccess(@Nullable HttpHeaders result) {
                    assert trailerTransformerState.isStateSet();
                    assert result != null;
                    final HttpHeaders trailersForError = trailerTransformerState.trailersForError();
                    if (trailersForError != null) {
                        // Payload emitted error but user recovered
                        for (Map.Entry<CharSequence, CharSequence> trailer : trailersForError) {
                            result.add(trailer.getKey(), trailer.getValue());
                        }
                    } else {
                        try {
                            result = trailersTransformer.payloadComplete(trailerTransformerState.state(), result);
                        } catch (Throwable t) {
                            subscriber.onError(t);
                            return;
                        }
                    }
                    subscriber.onSuccess(result);
                }

                @Override
                public void onError(final Throwable t) {
                    assert trailerTransformerState.isStateSet();
                    Throwable payloadErrorCause = trailerTransformerState.payloadErrorCause();
                    HttpHeaders trailersForError;
                    if (payloadErrorCause != null) {
                        // Same error for payload and trailers, we recovered from payload error, hence should use the
                        // trailers specified by the user.
                        trailersForError = trailerTransformerState.trailersForError();
                        assert trailersForError != null;
                        if (payloadErrorCause != t) {
                            LOGGER.info("Trailers source emitted error different than payload, Ignoring.", t);
                        }
                    } else {
                        try {
                            trailersForError = trailersTransformer.catchPayloadFailure(trailerTransformerState.state(),
                                    t, headersFactory.newEmptyTrailers());
                        } catch (Throwable throwable) {
                            subscriber.onError(throwable);
                            return;
                        }
                    }
                    subscriber.onSuccess(trailersForError);
                }
            });
            payloadBody = (raw ? rawPayload() : payloadBody()).liftSync(subscriber -> {
                Object state = trailersTransformer.newState();
                trailerTransformerState.reference(state);
                return new PublisherSource.Subscriber<Object>() {
                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(@Nullable final Object object) {
                        assert object != null;
                        subscriber.onNext(trailersTransformer.accept(state, object));
                    }

                    @Override
                    public void onError(final Throwable t) {
                        // If payload fails we will never emit original trailers from the combined payload+trailers
                        // Publisher
                        HttpHeaders trailersForError;
                        try {
                            trailersForError = trailersTransformer.catchPayloadFailure(state, t,
                                    headersFactory.newEmptyTrailers());
                        } catch (Throwable throwable) {
                            subscriber.onError(t);
                            return;
                        }
                        trailerTransformerState.trailersForError(t, trailersForError);
                        subscriber.onComplete();
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                };
            });
        }

        payloadInfo.setOnlyEmitsBuffer(!raw);

        if (!HTTP_1_0.equals(version)) {
            // This transform may add trailers, and if there are trailers present we must send
            // `transfer-encoding: chunked` not `content-length`, so force the API type to non-aggregated to indicate
            // that.
            payloadInfo.setMayHaveTrailers(true);

            // Update the headers to indicate that we will be writing trailers.
            addChunkedEncoding(headers);
        }
    }

    @SuppressWarnings("unchecked")
    private Publisher<Buffer> bufferPayload() {
        assert payloadBody != null;
        return (Publisher<Buffer>) payloadBody;
    }

    @SuppressWarnings("unchecked")
    private Publisher<Object> rawPayload() {
        assert payloadBody != null;
        return (Publisher<Object>) payloadBody;
    }

    private Publisher<Object> emptyOrRawPayload() {
        return payloadBody == null ? empty() : rawPayload();
    }

    @SuppressWarnings("unchecked")
    private static Publisher filterTrailers(final Publisher payloadBody) {
        return payloadBody.filter(o -> !(o instanceof HttpHeaders));
    }

    private static final class TrailerTransformerState {
        private static final Object NULL_STATE = new Object();

        @Nullable
        private Object state;
        @Nullable
        private Throwable payloadErrorCause;
        @Nullable
        private HttpHeaders trailersForError;

        @Nullable
        Object state() {
            return state == NULL_STATE ? null : state;
        }

        void reference(@Nullable Object state) {
            this.state = state == null ? NULL_STATE : state;
        }

        @Nullable
        HttpHeaders trailersForError() {
            return trailersForError;
        }

        void trailersForError(Throwable cause, HttpHeaders trailersForError) {
            payloadErrorCause = requireNonNull(cause);
            this.trailersForError = requireNonNull(trailersForError);
        }

        @Nullable
        Throwable payloadErrorCause() {
            return payloadErrorCause;
        }

        boolean isStateSet() {
            return state != null;
        }
    }
}
