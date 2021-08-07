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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;
import io.servicetalk.serializer.utils.FixedLengthStreamingSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.DefaultPayloadInfo.forTransportReceive;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.serializer.utils.StringSerializer.stringSerializer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamingHttpPayloadHolderTest {

    private enum UpdateMode {
        None,
        Set,
        SetWithSerializer,
        Transform,
        TransformWithTrailer,
        TransformWithSerializer,
        TransformWithErrorInTrailer
    }

    private enum SourceType {
        None,
        BufferOnly,
        Trailers
    }

    private static final StreamingSerializerDeserializer<String> UTF8_DESERIALIZER =
            new FixedLengthStreamingSerializer<>(stringSerializer(UTF_8), String::length);
    private HttpHeaders headers;
    private HttpHeadersFactory headersFactory;

    @Nullable
    private TestPublisher<Object> payloadSource;
    private final TestPublisher<Object> updatedPayloadSource = new TestPublisher<>();
    private final TestPublisherSubscriber<Buffer> bufferPayloadSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Object> payloadAndTrailersSubscriber = new TestPublisherSubscriber<>();
    private final TransformFunctions transformFunctions = new TransformFunctions();
    private final TransformFunctions secondTransformFunctions = new TransformFunctions();
    private StreamingHttpPayloadHolder payloadHolder;
    private UpdateMode updateMode;
    private boolean doubleTransform;
    private SourceType sourceType;
    private boolean skipAfterVerification;

    private void setUp(SourceType sourceType, UpdateMode updateMode, boolean doubleTransform) {
        this.sourceType = sourceType;
        this.updateMode = updateMode;
        this.doubleTransform = doubleTransform;
        headers = mock(HttpHeaders.class);
        headersFactory = new DefaultHttpHeadersFactory(false, false);
        if (sourceType == SourceType.Trailers) {
            when(headers.valuesIterator(TRANSFER_ENCODING)).then(__ -> singletonList(CHUNKED).iterator());
        } else {
            when(headers.valuesIterator(TRANSFER_ENCODING)).then(__ -> emptyIterator());
        }
        payloadSource = sourceType == SourceType.None ? null : new TestPublisher<>();
        final DefaultPayloadInfo payloadInfo = forTransportReceive(false, HTTP_1_1, headers);
        if (payloadSource == null) {
            payloadInfo.setMayHaveTrailers(false);
        }
        payloadHolder = new StreamingHttpPayloadHolder(headers, DEFAULT_ALLOCATOR, payloadSource, payloadInfo,
                headersFactory);

        if (sourceType == SourceType.Trailers) {
            assertThat("Unexpected payload info trailer indication.", payloadHolder.mayHaveTrailers(), is(true));
        }

        final boolean sourceTypeTrailers = sourceType == SourceType.Trailers;
        switch (updateMode) {
            case Set:
                payloadHolder.payloadBody(updatedPayloadSource.map(b -> (Buffer) b));
                assertThat(payloadHolder.isGenericTypeBuffer(), is(not(sourceTypeTrailers)));
                assertThat(payloadHolder.mayHaveTrailers(), is(sourceTypeTrailers));
                break;
            case SetWithSerializer:
                payloadHolder.payloadBody(updatedPayloadSource.map(b -> ((Buffer) b).toString(UTF_8)),
                                          appSerializerUtf8FixLen());
                assertThat(payloadHolder.isGenericTypeBuffer(), is(not(sourceTypeTrailers)));
                assertThat(payloadHolder.mayHaveTrailers(), is(sourceTypeTrailers));
                break;
            case Transform:
            case TransformWithTrailer:
            case TransformWithSerializer:
            case TransformWithErrorInTrailer:
                transformFunctions.setupFor(updateMode, payloadHolder, sourceTypeTrailers);
                if (doubleTransform) {
                    secondTransformFunctions.setupFor(updateMode, payloadHolder, sourceTypeTrailers);
                }
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("unused")
    private static Collection<Arguments> data() {
        List<Arguments> params = new ArrayList<>();
        for (SourceType sourceType : SourceType.values()) {
            for (UpdateMode updateMode : UpdateMode.values()) {
                params.add(Arguments.of(sourceType, updateMode, false));
                if (updateMode == UpdateMode.Transform || updateMode == UpdateMode.TransformWithTrailer ||
                        updateMode == UpdateMode.TransformWithSerializer) {
                    params.add(Arguments.of(sourceType, updateMode, true));
                }
            }
        }
        return params;
    }

    @AfterEach
    void tearDown() {
        if (!skipAfterVerification && updateMode == UpdateMode.Transform) {
            transformFunctions.verifyMocks(updateMode, sourceType, headersFactory, canControlPayload());
            if (doubleTransform) {
                secondTransformFunctions.verifyMocks(updateMode, sourceType, headersFactory, canControlPayload());
            }
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: source type: {0}, update mode = {1}, double transform? {2}")
    @MethodSource("data")
    void getPayload(SourceType sourceType, UpdateMode updateMode, boolean doubleTransform) throws Exception {
        setUp(sourceType, updateMode, doubleTransform);
        Publisher<Buffer> payload = payloadHolder.payloadBody();
        toSource(payload).subscribe(bufferPayloadSubscriber);
        simulateAndVerifyPayloadRead(bufferPayloadSubscriber);
        simulateAndVerifyPayloadComplete(bufferPayloadSubscriber);
    }

    @ParameterizedTest(name = "{displayName} {index}: source type: {0}, update mode = {1}, double transform? {2}")
    @MethodSource("data")
    void getMessageBody(SourceType sourceType, UpdateMode updateMode, boolean doubleTransform) throws Exception {
        setUp(sourceType, updateMode, doubleTransform);
        Publisher<Object> bodyAndTrailers = payloadHolder.messageBody();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);
        simulateAndVerifyTrailerReadIfApplicable();
    }

    @ParameterizedTest(name = "{displayName} {index}: source type: {0}, update mode = {1}, double transform? {2}")
    @MethodSource("data")
    void sourceEmitsTrailersUnconditionally(SourceType sourceType,
                                            UpdateMode updateMode,
                                            boolean doubleTransform) throws Exception {
        setUp(sourceType, updateMode, doubleTransform);
        checkSkipTest(() -> {
            assumeTrue(sourceType != SourceType.None, () -> "Ignored source type: " + sourceType);
            assumeTrue(sourceType != SourceType.BufferOnly, () -> "Ignored source type: " + sourceType);
        });
        assert payloadSource != null;
        Publisher<Object> bodyAndTrailers = payloadHolder.messageBody();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);
        payloadAndTrailersSubscriber.awaitSubscription().request(2);
        payloadSource.onNext(mock(HttpHeaders.class));
        if (sourceType == SourceType.Trailers &&
                (updateMode == UpdateMode.Set || updateMode == UpdateMode.SetWithSerializer)) {
            payloadSource.onComplete(); // Original source should complete for us to emit trailers
        }
        if (updateMode != UpdateMode.TransformWithErrorInTrailer) {
            getPayloadSource().onComplete();
        }

        verifyTrailersReceived();
    }

    @ParameterizedTest(name = "{displayName} {index}: source type: {0}, update mode = {1}, double transform? {2}")
    @MethodSource("data")
    void transformedWithTrailersPayloadEmitsError(SourceType sourceType,
                                                  UpdateMode updateMode,
                                                  boolean doubleTransform) throws Throwable {
        setUp(sourceType, updateMode, doubleTransform);
        checkSkipTest(() -> {
            assumeTrue(sourceType != SourceType.None, () -> "Ignored source type: " + sourceType);
            assumeTrue(updateMode == UpdateMode.TransformWithTrailer, () -> "Ignored update mode: " + updateMode);
        });
        assert payloadSource != null;

        throwPayloadErrorFromTransformer(updateMode, transformFunctions.trailerTransformer);
        if (doubleTransform) {
            throwPayloadErrorFromTransformer(updateMode, secondTransformFunctions.trailerTransformer);
        }

        Publisher<Object> bodyAndTrailers = payloadHolder.messageBody();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);

        // We need to request 1 more so the transformer will be invoked.
        payloadAndTrailersSubscriber.awaitSubscription().request(1);
        getPayloadSource().onError(DELIBERATE_EXCEPTION);
        assertThat(payloadAndTrailersSubscriber.awaitOnError(), equalTo(DELIBERATE_EXCEPTION));
        if (updateMode == UpdateMode.TransformWithTrailer) {
            verify(transformFunctions.trailerTransformer).catchPayloadFailure(any(),
                    eq(DELIBERATE_EXCEPTION), any());
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: source type: {0}, update mode = {1}, double transform? {2}")
    @MethodSource("data")
    void transformedWithTrailersPayloadEmitsErrorAndSwallowed(SourceType sourceType,
                                                              UpdateMode updateMode,
                                                              boolean doubleTransform) throws Throwable {
        setUp(sourceType, updateMode, doubleTransform);
        checkSkipTest(() -> {
            assumeTrue(sourceType != SourceType.None, () -> "Ignored source type: " + sourceType);
            assumeTrue(updateMode == UpdateMode.TransformWithTrailer, () -> "Ignored update mode: " + updateMode);
        });
        assert payloadSource != null;

        Publisher<Object> bodyAndTrailers = payloadHolder.messageBody();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);

        // We are swallowing error so let trailers be emitted with terminal.
        payloadAndTrailersSubscriber.awaitSubscription().request(1);
        if (sourceType == SourceType.Trailers) {
            payloadSource.onNext(mock(HttpHeaders.class));
        }
        swallowPayloadErrorInTransformer(updateMode, transformFunctions.trailerTransformer);
        if (doubleTransform) {
            swallowPayloadErrorInTransformer(updateMode, secondTransformFunctions.trailerTransformer);
        }
        payloadSource.onError(DELIBERATE_EXCEPTION);
        List<Object> items = payloadAndTrailersSubscriber.takeOnNext(1);
        assertThat("Unexpected trailer", items, hasSize(1));
        assertThat("Unexpected trailer", items.get(0), is(instanceOf(HttpHeaders.class)));
        if (sourceType == SourceType.Trailers) { // already emitted trailers, propagate error
            assertThat(payloadAndTrailersSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            payloadAndTrailersSubscriber.awaitOnComplete();
            if (updateMode == UpdateMode.TransformWithTrailer) {
                verify(transformFunctions.trailerTransformer).catchPayloadFailure(any(),
                        eq(DELIBERATE_EXCEPTION), any());
            }
        }
    }

    private void simulateAndVerifyPayloadRead(final TestPublisherSubscriber<?> subscriber) throws Exception {
        if (!canControlPayload()) {
            return;
        }
        final BufferAllocator alloc = payloadHolder.allocator();
        Buffer buf = DEFAULT_ALLOCATOR.fromAscii("foo");
        subscriber.awaitSubscription().request(1);
        getPayloadSource().onNext(buf);
        Object rawActual = subscriber.takeOnNext(1).get(0);

        String actual;
        if (updateMode == UpdateMode.SetWithSerializer || updateMode == UpdateMode.TransformWithSerializer) {
            actual = UTF8_DESERIALIZER.deserialize(Publisher.from((Buffer) rawActual), alloc)
                    .firstOrError().toFuture().get();
            if (doubleTransform) {
                actual = UTF8_DESERIALIZER.deserialize(Publisher.from(alloc.fromUtf8(actual)), alloc)
                        .firstOrError().toFuture().get();
            }
        } else if (subscriber == bufferPayloadSubscriber || subscriber == payloadAndTrailersSubscriber) {
            actual = ((Buffer) rawActual).toString(UTF_8);
        } else {
            actual = rawActual.toString();
        }
        assertThat("Unexpected payload", actual, is("foo"));
    }

    private void simulateAndVerifyTrailerReadIfApplicable() {
        if (canNotTestForTrailers()) {
            simulateAndVerifyPayloadComplete(payloadAndTrailersSubscriber);
            return;
        }
        payloadAndTrailersSubscriber.awaitSubscription().request(1);
        if (sourceType == SourceType.Trailers) {
            assert payloadSource != null;
            payloadSource.onNext(mock(HttpHeaders.class));
            if (updateMode != UpdateMode.TransformWithErrorInTrailer) {
                // If trailers with error, the publisher is already terminated by the error
                payloadSource.onComplete();
            }
            tryCompletePayloadSource();
        } else {
            if (payloadSource != null) {
                payloadSource.onComplete();
            }
            if (canControlPayload()) {
                tryCompletePayloadSource();
            }
        }
        verifyTrailersReceived();
    }

    private void tryCompletePayloadSource() {
        TestPublisher<Object> tp = getPayloadSource();
        if (tp != payloadSource) {
            tp.onComplete(); // Force trailer emission
        }
    }

    private void verifyTrailersReceived() {
        if (updateMode == UpdateMode.TransformWithErrorInTrailer) {
            payloadAndTrailersSubscriber.awaitOnError();
        } else {
            List<Object> items = payloadAndTrailersSubscriber.takeOnNext(1);
            assertThat("Unexpected trailer", items, hasSize(1));
            assertThat("Unexpected trailer", items.get(0), is(instanceOf(HttpHeaders.class)));
            payloadAndTrailersSubscriber.awaitOnComplete();
        }
        if (updateMode == UpdateMode.TransformWithTrailer || updateMode == UpdateMode.TransformWithErrorInTrailer) {
            verify(transformFunctions.trailerTransformer).payloadComplete(any(), any());
        }
    }

    private void simulateAndVerifyPayloadComplete(final TestPublisherSubscriber<?> subscriber) {
        if (canControlPayload()) {
            getPayloadSource().onComplete();
        }
        if (updateMode == UpdateMode.TransformWithTrailer || updateMode == UpdateMode.TransformWithErrorInTrailer) {
            subscriber.awaitSubscription().request(1);
        }
        if (payloadSource != null && (updateMode == UpdateMode.Set || updateMode == UpdateMode.SetWithSerializer)) {
            // A set operation was done with a prior Publisher, we need to complete the prior Publisher.
            payloadSource.onComplete();
        }
        if (updateMode == UpdateMode.TransformWithErrorInTrailer) {
            subscriber.awaitOnError();
        } else {
            subscriber.awaitOnComplete();
        }
    }

    private TestPublisher<Object> getPayloadSource() {
        if (!canControlPayload()) {
            return new TestPublisher<>(); // Just to avoid null checks
        }
        if (updateMode == UpdateMode.Set || updateMode == UpdateMode.SetWithSerializer) {
            return updatedPayloadSource;
        }
        assert payloadSource != null;
        return payloadSource;
    }

    private boolean canNotTestForTrailers() {
        return sourceType != SourceType.Trailers && updateMode != UpdateMode.TransformWithTrailer;
    }

    private boolean canControlPayload() {
        return sourceType != SourceType.None || updateMode == UpdateMode.Set ||
                updateMode == UpdateMode.SetWithSerializer;
    }

    private void checkSkipTest(Runnable r) {
        try {
            r.run();
        } catch (Throwable cause) {
            skipAfterVerification = true;
            throw cause;
        }
    }

    static void throwPayloadErrorFromTransformer(final UpdateMode updateMode,
                                                 final TrailersTransformer<String, ?> trailersTransformer)
            throws Throwable {
        if (updateMode == UpdateMode.TransformWithTrailer) {
            when(trailersTransformer.catchPayloadFailure(any(), eq(DELIBERATE_EXCEPTION), any()))
                    .thenAnswer(invocation -> {
                        throw DELIBERATE_EXCEPTION;
                    });
        }
    }

    void swallowPayloadErrorInTransformer(final UpdateMode updateMode,
                                          final TrailersTransformer<String, ?> trailersTransformer) throws Throwable {
        if (updateMode == UpdateMode.TransformWithTrailer) {
            when(trailersTransformer.catchPayloadFailure(any(), eq(DELIBERATE_EXCEPTION), any()))
                    .thenAnswer(invocation -> invocation.getArgument(2));
        }
    }

    private static final class TransformFunctions {
        @SuppressWarnings("unchecked")
        private final Function<Publisher<Buffer>, Publisher<String>> stringTransformer = mock(Function.class);
        @SuppressWarnings("unchecked")
        private final UnaryOperator<Publisher<Buffer>> transformer = mock(UnaryOperator.class);
        @SuppressWarnings("unchecked")
        private final TrailersTransformer<String, Buffer> trailerTransformer = mock(TrailersTransformer.class);

        @SuppressWarnings("unchecked")
        TransformFunctions() {
            when(transformer.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(stringTransformer.apply(any())).thenAnswer(invocation ->
                    ((Publisher<Buffer>) invocation.getArgument(0))
                            .map(buffer -> buffer.toString(UTF_8)));
            when(trailerTransformer.accept(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        }

        void setupFor(UpdateMode updateMode, StreamingHttpPayloadHolder payloadHolder, boolean sourceTypeTrailers) {
            switch (updateMode) {
                case Transform:
                    payloadHolder.transformPayloadBody(transformer);
                    assertThat(payloadHolder.isGenericTypeBuffer(), is(not(sourceTypeTrailers)));
                    assertThat(payloadHolder.mayHaveTrailers(), is(sourceTypeTrailers));
                    break;
                case TransformWithTrailer:
                    when(trailerTransformer.payloadComplete(any(), any()))
                            .thenAnswer(invocation -> invocation.getArgument(1));
                    payloadHolder.transform(trailerTransformer);
                    assertThat(payloadHolder.mayHaveTrailers(), is(true));
                    assertThat(payloadHolder.isGenericTypeBuffer(), is(false));
                    break;
                case TransformWithErrorInTrailer:
                    when(trailerTransformer.payloadComplete(any(), any())).thenThrow(DELIBERATE_EXCEPTION);
                    payloadHolder.transform(trailerTransformer);
                    assertThat(payloadHolder.mayHaveTrailers(), is(true));
                    assertThat(payloadHolder.isGenericTypeBuffer(), is(false));
                    break;
                case TransformWithSerializer:
                    payloadHolder.transformPayloadBody(stringTransformer, appSerializerUtf8FixLen());
                    assertThat(payloadHolder.isGenericTypeBuffer(), is(not(sourceTypeTrailers)));
                    assertThat(payloadHolder.mayHaveTrailers(), is(sourceTypeTrailers));
                    break;
                default:
                    break;
            }
        }

        void verifyMocks(UpdateMode updateMode, SourceType sourceType, HttpHeadersFactory headersFactory,
                         boolean canControlPayload) {
            switch (updateMode) {
                case Transform:
                    verify(transformer).apply(any());
                    break;
                case TransformWithTrailer:
                case TransformWithErrorInTrailer:
                    verify(trailerTransformer).newState();
                    if (canControlPayload) {
                        verify(trailerTransformer).accept(any(), any());
                    }
                    if (sourceType != SourceType.Trailers) {
                        verify(headersFactory).newEmptyTrailers();
                    }
                    break;
                case TransformWithSerializer:
                    verify(stringTransformer).apply(any());
                    break;
                default:
                    break;
            }
        }
    }
}
