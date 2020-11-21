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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class StreamingHttpPayloadHolderTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private enum UpdateMode {
        None,
        Set,
        SetWithSerializer,
        Transform,
        TransformWithTrailer,
        TransformWithSerializer,
        TransformRaw,
        TransformRawWithTrailer,
    }

    private enum SourceType {
        None,
        BufferOnly,
        Trailers
    }

    private final HttpHeaders headers;
    private final HttpHeadersFactory headersFactory;

    @Nullable
    private final TestPublisher<Object> payloadSource;
    private final TestPublisher<Object> updatedPayloadSource = new TestPublisher<>();
    private final TestPublisherSubscriber<Buffer> bufferPayloadSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<String> stringPayloadSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Object> payloadAndTrailersSubscriber = new TestPublisherSubscriber<>();
    private final TransformFunctions transformFunctions = new TransformFunctions();
    private final TransformFunctions secondTransformFunctions = new TransformFunctions();
    private final RawTransformFunctions rawTransformFunctions = new RawTransformFunctions();
    private final RawTransformFunctions secondRawTransformFunctions = new RawTransformFunctions();
    private final StreamingHttpPayloadHolder payloadHolder;
    private final UpdateMode updateMode;
    private final boolean doubleTransform;
    private final SourceType sourceType;

    public StreamingHttpPayloadHolderTest(SourceType sourceType, UpdateMode updateMode,
                                          boolean doubleTransform) {
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
        final DefaultPayloadInfo payloadInfo = forTransportReceive(headers);
        payloadHolder = new StreamingHttpPayloadHolder(headers, DEFAULT_ALLOCATOR, payloadSource, payloadInfo,
                headersFactory, HTTP_1_1);
    }

    @Parameterized.Parameters(name = "{index}: source type: {0}, update mode = {1}, double transform? {2}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        for (SourceType sourceType : SourceType.values()) {
            for (UpdateMode updateMode : UpdateMode.values()) {
                params.add(new Object[]{sourceType, updateMode, false});
                if (updateMode == UpdateMode.Transform || updateMode == UpdateMode.TransformWithTrailer ||
                        updateMode == UpdateMode.TransformWithSerializer ||
                        updateMode == UpdateMode.TransformRaw || updateMode == UpdateMode.TransformRawWithTrailer) {
                    params.add(new Object[]{sourceType, updateMode, true});
                }
            }
        }
        return params;
    }

    @Before
    public void setUp() {
        if (sourceType == SourceType.Trailers) {
            assertThat("Unexpected payload info trailer indication.", payloadHolder.mayHaveTrailers(), is(true));
        }

        switch (updateMode) {
            case Set:
                payloadHolder.payloadBody(updatedPayloadSource.map(b -> (Buffer) b));
                assertThat("Expected buffer payload.", payloadHolder.onlyEmitsBuffer(), is(true));
                break;
            case SetWithSerializer:
                payloadHolder.payloadBody(updatedPayloadSource.map(b -> ((Buffer) b).toString(defaultCharset())),
                        textSerializer());
                assertThat("Expected buffer payload.", payloadHolder.onlyEmitsBuffer(), is(true));
                break;
            case Transform:
            case TransformWithTrailer:
            case TransformWithSerializer:
                transformFunctions.setupFor(updateMode, payloadHolder);
                if (doubleTransform) {
                    secondTransformFunctions.setupFor(updateMode, payloadHolder);
                }
                break;
            case TransformRaw:
            case TransformRawWithTrailer:
                rawTransformFunctions.setupFor(updateMode, payloadHolder);
                if (doubleTransform) {
                    secondRawTransformFunctions.setupFor(updateMode, payloadHolder);
                }
                break;
            default:
                break;
        }
    }

    @After
    public void tearDown() {
        switch (updateMode) {
            case Transform:
                transformFunctions.verifyMocks(updateMode, sourceType, headersFactory, canControlPayload());
                if (doubleTransform) {
                    secondTransformFunctions.verifyMocks(updateMode, sourceType, headersFactory, canControlPayload());
                }
                break;
            case TransformRaw:
                rawTransformFunctions.verifyMocks(updateMode, sourceType, headersFactory, canControlPayload());
                if (doubleTransform) {
                    secondRawTransformFunctions.verifyMocks(updateMode, sourceType, headersFactory,
                            canControlPayload());
                }
                break;
            default:
                break;
        }
    }

    @Test
    public void getPayload() {
        Publisher<Buffer> payload = payloadHolder.payloadBody();
        toSource(payload).subscribe(bufferPayloadSubscriber);
        simulateAndVerifyPayloadRead(bufferPayloadSubscriber);
        simulateAndVerifyPayloadComplete(bufferPayloadSubscriber);
    }

    @Test
    public void getPayloadWithSerializer() {
        when(headers.get(CONTENT_TYPE)).thenReturn(TEXT_PLAIN_UTF_8);
        Publisher<String> payload = textDeserializer().deserialize(headers, payloadHolder.payloadBody());
        toSource(payload).subscribe(stringPayloadSubscriber);
        simulateAndVerifyPayloadRead(stringPayloadSubscriber);
        simulateAndVerifyPayloadComplete(stringPayloadSubscriber);
    }

    @Test
    public void getPayloadAndTrailers() {
        Publisher<Object> bodyAndTrailers = payloadHolder.payloadBodyAndTrailers();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);
        simulateAndVerifyTrailerReadIfApplicable();
    }

    @Test
    public void sourceEmitsTrailersUnconditionally() {
        assumeThat("Ignored source type: " + sourceType, sourceType, is(not(equalTo(SourceType.None))));
        assert payloadSource != null;
        Publisher<Object> bodyAndTrailers = payloadHolder.payloadBodyAndTrailers();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);
        payloadAndTrailersSubscriber.awaitSubscription().request(2);
        payloadSource.onNext(mock(HttpHeaders.class));
        if (sourceType == SourceType.Trailers &&
                (updateMode == UpdateMode.Set || updateMode == UpdateMode.SetWithSerializer)) {
            payloadSource.onComplete(); // Original source should complete for us to emit trailers
        }
        getPayloadSource().onComplete();
        if (!payloadHolder.onlyEmitsBuffer() && payloadHolder.mayHaveTrailers() ||
                headers.containsIgnoreCase(TRANSFER_ENCODING, CHUNKED)) {
            verifyTrailersReceived();
        } else {
            if (sourceType == SourceType.Trailers || updateMode == UpdateMode.TransformWithTrailer) {
                assertThat(payloadAndTrailersSubscriber.takeOnNext(), instanceOf(HttpHeaders.class));
            }
            payloadAndTrailersSubscriber.awaitOnComplete();
        }
    }

    @Test
    public void transformedWithTrailersPayloadEmitsError() throws Throwable {
        assumeThat("Ignored source type: " + sourceType, sourceType, is(not(equalTo(SourceType.None))));
        assert payloadSource != null;
        assumeThat("Ignored update mode: " + updateMode, updateMode,
                anyOf(equalTo(UpdateMode.TransformWithTrailer), equalTo(UpdateMode.TransformRawWithTrailer)));

        throwPayloadErrorFromTransformer(updateMode, transformFunctions.trailerTransformer);
        throwPayloadErrorFromTransformer(updateMode, rawTransformFunctions.trailerTransformer);
        if (doubleTransform) {
            throwPayloadErrorFromTransformer(updateMode, secondTransformFunctions.trailerTransformer);
            throwPayloadErrorFromTransformer(updateMode, secondRawTransformFunctions.trailerTransformer);
        }

        Publisher<Object> bodyAndTrailers = payloadHolder.payloadBodyAndTrailers();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);

        getPayloadSource().onError(DELIBERATE_EXCEPTION);
        assertThat(payloadAndTrailersSubscriber.awaitOnError(), equalTo(DELIBERATE_EXCEPTION));

        switch (updateMode) {
            case TransformWithTrailer:
                verify(transformFunctions.trailerTransformer).catchPayloadFailure(any(),
                        eq(DELIBERATE_EXCEPTION), any());
                break;
            case TransformRawWithTrailer:
                verify(rawTransformFunctions.trailerTransformer).catchPayloadFailure(any(),
                        eq(DELIBERATE_EXCEPTION), any());
                break;
            default:
                break;
        }
    }

    @Test
    public void transformedWithTrailersPayloadEmitsErrorAndSwallowed() throws Throwable {
        assumeThat("Ignored source type: " + sourceType, sourceType, is(not(equalTo(SourceType.None))));
        assert payloadSource != null;
        assumeThat("Ignored update mode: " + updateMode, updateMode,
                anyOf(equalTo(UpdateMode.TransformWithTrailer), equalTo(UpdateMode.TransformRawWithTrailer)));

        Publisher<Object> bodyAndTrailers = payloadHolder.payloadBodyAndTrailers();
        toSource(bodyAndTrailers).subscribe(payloadAndTrailersSubscriber);
        simulateAndVerifyPayloadRead(payloadAndTrailersSubscriber);

        // We are swallowing error so let trailers be emitted with terminal.
        payloadAndTrailersSubscriber.awaitSubscription().request(1);
        if (sourceType == SourceType.Trailers) {
            payloadSource.onNext(mock(HttpHeaders.class));
        }
        swallowPayloadErrorInTransformer(updateMode, transformFunctions.trailerTransformer);
        swallowPayloadErrorInTransformer(updateMode, rawTransformFunctions.trailerTransformer);
        if (doubleTransform) {
            swallowPayloadErrorInTransformer(updateMode, secondTransformFunctions.trailerTransformer);
            swallowPayloadErrorInTransformer(updateMode, secondRawTransformFunctions.trailerTransformer);
        }
        payloadSource.onError(DELIBERATE_EXCEPTION);
        List<Object> items = payloadAndTrailersSubscriber.takeOnNext(1);
        assertThat("Unexpected trailer", items, hasSize(1));
        assertThat("Unexpected trailer", items.get(0), is(instanceOf(HttpHeaders.class)));
        payloadAndTrailersSubscriber.awaitOnComplete();

        switch (updateMode) {
            case TransformWithTrailer:
                verify(transformFunctions.trailerTransformer).catchPayloadFailure(any(),
                        eq(DELIBERATE_EXCEPTION), any());
                break;
            case TransformRawWithTrailer:
                verify(rawTransformFunctions.trailerTransformer).catchPayloadFailure(any(),
                        eq(DELIBERATE_EXCEPTION), any());
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private void simulateAndVerifyPayloadRead(final TestPublisherSubscriber<?> subscriber) {
        if (!canControlPayload()) {
            return;
        }
        Buffer buf = DEFAULT_ALLOCATOR.fromAscii("foo");
        subscriber.awaitSubscription().request(1);
        getPayloadSource().onNext(buf);
        assertThat("Unexpected payload", subscriber.takeOnNext(1),
                contains((subscriber == bufferPayloadSubscriber || subscriber == payloadAndTrailersSubscriber) ?
                        buf : "foo"));
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
            payloadSource.onComplete();
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
        TestPublisher tp = getPayloadSource();
        if (tp != payloadSource) {
            tp.onComplete(); // Force trailer emission
        }
    }

    private void verifyTrailersReceived() {
        List<Object> items = payloadAndTrailersSubscriber.takeOnNext(1);
        assertThat("Unexpected trailer", items, hasSize(1));
        assertThat("Unexpected trailer", items.get(0), is(instanceOf(HttpHeaders.class)));
        payloadAndTrailersSubscriber.awaitOnComplete();
        if (updateMode == UpdateMode.TransformWithTrailer) {
            verify(transformFunctions.trailerTransformer).payloadComplete(any(), any());
        } else if (updateMode == UpdateMode.TransformRawWithTrailer) {
            verify(rawTransformFunctions.trailerTransformer).payloadComplete(any(), any());
        }
    }

    private void simulateAndVerifyPayloadComplete(final TestPublisherSubscriber<?> subscriber) {
        if (canControlPayload()) {
            getPayloadSource().onComplete();
        }
        subscriber.awaitOnComplete();
    }

    private TestPublisher getPayloadSource() {
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
        return sourceType != SourceType.Trailers && updateMode != UpdateMode.TransformRawWithTrailer &&
                updateMode != UpdateMode.TransformWithTrailer;
    }

    private boolean canControlPayload() {
        return sourceType != SourceType.None || updateMode == UpdateMode.Set ||
                updateMode == UpdateMode.SetWithSerializer;
    }

    static void throwPayloadErrorFromTransformer(final UpdateMode updateMode,
                                                 final TrailersTransformer<String, ?> trailersTransformer)
            throws Throwable {
        switch (updateMode) {
            case TransformWithTrailer:
                when(trailersTransformer.catchPayloadFailure(any(), eq(DELIBERATE_EXCEPTION), any()))
                        .thenAnswer(invocation -> {
                            throw DELIBERATE_EXCEPTION;
                        });
                break;
            case TransformRawWithTrailer:
                when(trailersTransformer.catchPayloadFailure(any(), eq(DELIBERATE_EXCEPTION),
                        any()))
                        .thenAnswer(invocation -> {
                            throw DELIBERATE_EXCEPTION;
                        });
                break;
            default:
                break;
        }
    }

    void swallowPayloadErrorInTransformer(final UpdateMode updateMode,
                                          final TrailersTransformer<String, ?> trailersTransformer) throws Throwable {
        switch (updateMode) {
            case TransformWithTrailer:
                when(trailersTransformer.catchPayloadFailure(any(), eq(DELIBERATE_EXCEPTION), any()))
                        .thenAnswer(invocation -> invocation.getArgument(2));
                break;
            case TransformRawWithTrailer:
                when(trailersTransformer.catchPayloadFailure(any(), eq(DELIBERATE_EXCEPTION),
                        any()))
                        .thenAnswer(invocation -> invocation.getArgument(2));
                break;
            default:
                break;
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
                            .map(buffer -> buffer.toString(defaultCharset())));
            when(trailerTransformer.accept(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        }

        void setupFor(UpdateMode updateMode, StreamingHttpPayloadHolder payloadHolder) {
            switch (updateMode) {
                case Transform:
                    payloadHolder.transformPayloadBody(transformer);
                    assertThat("Expected buffer payload.", payloadHolder.onlyEmitsBuffer(), is(true));
                    break;
                case TransformWithTrailer:
                    when(trailerTransformer.payloadComplete(any(), any()))
                            .thenAnswer(invocation -> invocation.getArgument(1));
                    payloadHolder.transform(trailerTransformer);
                    assertThat("Expected buffer payload.", payloadHolder.onlyEmitsBuffer(), is(true));
                    assertThat("Unexpected payload info trailer indication.", payloadHolder.mayHaveTrailers(),
                            is(true));
                    break;
                case TransformWithSerializer:
                    payloadHolder.transformPayloadBody(stringTransformer, textSerializer());
                    assertThat("Expected buffer payload.", payloadHolder.onlyEmitsBuffer(), is(true));
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

    private static final class RawTransformFunctions {
        @SuppressWarnings("unchecked")
        private final UnaryOperator<Publisher<?>> transformer = mock(UnaryOperator.class);
        @SuppressWarnings("unchecked")
        private final TrailersTransformer<String, Object> trailerTransformer = mock(TrailersTransformer.class);

        RawTransformFunctions() {
            when(transformer.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(trailerTransformer.accept(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        }

        void setupFor(UpdateMode updateMode, StreamingHttpPayloadHolder request) {
            switch (updateMode) {
                case TransformRaw:
                    request.transformRawPayloadBody(transformer);
                    assertThat("Expected raw payload.", request.onlyEmitsBuffer(), is(false));
                    break;
                case TransformRawWithTrailer:
                    when(trailerTransformer.payloadComplete(any(), any()))
                            .thenAnswer(invocation -> invocation.getArgument(1));
                    request.transformRaw(trailerTransformer);
                    assertThat("Expected raw payload.", request.onlyEmitsBuffer(), is(false));
                    assertThat("Unexpected payload info trailer indication.", request.mayHaveTrailers(), is(true));
                    break;
                default:
                    break;
            }
        }

        void verifyMocks(UpdateMode updateMode, SourceType sourceType, HttpHeadersFactory headersFactory,
                         boolean canControlPayload) {
            switch (updateMode) {
                case TransformRaw:
                    verify(transformer).apply(any());
                    break;
                case TransformRawWithTrailer:
                    verify(trailerTransformer).newState();
                    if (canControlPayload) {
                        verify(trailerTransformer).accept(any(), any());
                    }
                    if (sourceType != SourceType.Trailers) {
                        verify(headersFactory).newEmptyTrailers();
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
