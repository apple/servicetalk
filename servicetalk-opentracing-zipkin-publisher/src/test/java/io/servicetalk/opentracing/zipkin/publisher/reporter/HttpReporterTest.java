/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.zipkin.publisher.reporter;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.opentracing.zipkin.publisher.reporter.HttpReporter.Builder;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.opentracing.zipkin.publisher.reporter.HttpReporter.V1_PATH;
import static io.servicetalk.opentracing.zipkin.publisher.reporter.HttpReporter.V2_PATH;
import static io.servicetalk.opentracing.zipkin.publisher.reporter.SpanUtils.newSpan;
import static io.servicetalk.opentracing.zipkin.publisher.reporter.SpanUtils.verifySpan;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zipkin2.CheckResult.OK;

class HttpReporterTest {
    private final BlockingQueue<HttpRequest> receivedRequests;
    private final ServerContext context;
    @Nullable
    private HttpReporter reporter;
    private volatile BiFunction<HttpServiceContext, HttpResponseFactory, HttpResponse> responseGenerator =
            (__, factory) -> factory.ok();

    HttpReporterTest() throws Exception {
        receivedRequests = new LinkedBlockingQueue<>();
        this.context = forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    receivedRequests.add(request);
                    return responseGenerator.apply(ctx, responseFactory);
                });
    }

    @AfterEach
    public void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (reporter != null) {
            closeable.append(reporter);
        }
        closeable.append(context);
        closeable.closeGracefully();
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void disableBatching(final Codec codec) throws Exception {
        HttpReporter reporter = initReporter(codec, builder -> builder.spansBatchingEnabled(false));
        reporter.report(newSpan("1"));
        List<Span> spans = verifyRequest(codec, receivedRequests.take(), false);
        assertThat("Unexpected spans received.", spans, hasSize(1));
        verifySpan(spans.get(0), "1");

        reporter.report(newSpan("2"));
        List<Span> spans2 = verifyRequest(codec, receivedRequests.take(), false);
        assertThat("Unexpected spans received.", spans, hasSize(1));
        verifySpan(spans2.get(0), "2");
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void batching(final Codec codec) throws Exception {
        HttpReporter reporter = initReporter(codec, builder -> builder.batchSpans(2, ofMillis(200)));
        reporter.report(newSpan("1"));
        reporter.report(newSpan("2"));
        List<Span> spans = verifyRequest(codec, receivedRequests.take(), true);
        assertThat("Unexpected spans received.", spans, hasSize(2));
        verifySpan(spans.get(0), "1");
        verifySpan(spans.get(1), "2");
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void reportAfterClose(final Codec codec) {
        HttpReporter reporter = initReporter(codec, builder -> builder.spansBatchingEnabled(false));
        assertThat("Unexpected check state.", reporter.check(), is(OK));
        reporter.close();
        assertThat("Unexpected check state.", reporter.check(), is(not(OK)));
        assertThrows(IllegalStateException.class, () -> reporter.report(newSpan("1")),
                "Report post close accepted.");
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void non200ResponsesAreOkWithoutBatching(final Codec codec) throws Exception {
        HttpReporter reporter = initReporter(codec, builder -> builder.spansBatchingEnabled(false));
        verifyNon200ResponsesAreOk(codec, reporter, false);
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void non200ResponsesAreOkWithBatching(final Codec codec) throws Exception {
        HttpReporter reporter = initReporter(codec, builder -> builder.batchSpans(1, ofMillis(200)));
        verifyNon200ResponsesAreOk(codec, reporter, true);
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void reportFailuresAreRecoveredWithBatching(final Codec codec) throws Exception {
        HttpReporter reporter = initReporter(codec, builder -> builder.batchSpans(1, ofMillis(200)));
        verifySpanSendFailuresAreRecovered(codec, reporter, true);
    }

    @ParameterizedTest(name = "codec: {0}")
    @EnumSource(Codec.class)
    void reportFailuresAreRecoveredWithoutBatching(final Codec codec) throws Exception {
        HttpReporter reporter = initReporter(codec, builder -> builder.spansBatchingEnabled(false));
        verifySpanSendFailuresAreRecovered(codec, reporter, false);
    }

    private void verifyNon200ResponsesAreOk(final Codec codec, final HttpReporter reporter,
                                            final boolean batched) throws Exception {
        responseGenerator = (__, factory) -> factory.internalServerError();
        reporter.report(newSpan("1"));
        List<Span> spans = verifyRequest(codec, receivedRequests.take(), batched);
        assertThat("Unexpected spans received.", spans, hasSize(1));
        verifySpan(spans.get(0), "1");

        responseGenerator = (__, factory) -> factory.ok();
        reporter.report(newSpan("2"));
        List<Span> spans2 = verifyRequest(codec, receivedRequests.take(), batched);
        assertThat("Unexpected spans received.", spans2, hasSize(1));
        verifySpan(spans2.get(0), "2");
    }

    private void verifySpanSendFailuresAreRecovered(final Codec codec, final HttpReporter reporter,
                                                    final boolean batched) throws Exception {
        responseGenerator = (httpServiceContext, factory) -> {
            try {
                httpServiceContext.closeAsync().toFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return factory.ok();
        };
        reporter.report(newSpan("1"));
        List<Span> spans = verifyRequest(codec, receivedRequests.take(), batched);
        assertThat("Unexpected spans received.", spans, hasSize(1));
        verifySpan(spans.get(0), "1");

        responseGenerator = (__, factory) -> factory.ok();
        reporter.report(newSpan("2"));
        List<Span> spans2 = verifyRequest(codec, receivedRequests.take(), batched);
        assertThat("Unexpected spans received.", spans2, hasSize(1));
        verifySpan(spans2.get(0), "2");
    }

    private List<Span> verifyRequest(final Codec codec, final HttpRequest request, final boolean multipleSpans) {
        SpanBytesDecoder decoder;
        switch (codec) {
            case JSON_V1:
                assertThat("Unexpected path.", request.path(), equalTo(V1_PATH));
                decoder = SpanBytesDecoder.JSON_V1;
                break;
            case JSON_V2:
                assertThat("Unexpected path.", request.path(), equalTo(V2_PATH));
                decoder = SpanBytesDecoder.JSON_V2;
                break;
            case THRIFT:
                assertThat("Unexpected path.", request.path(), equalTo(V2_PATH));
                decoder = SpanBytesDecoder.THRIFT;
                break;
            case PROTO3:
                assertThat("Unexpected path.", request.path(), equalTo(V2_PATH));
                decoder = SpanBytesDecoder.PROTO3;
                break;
            default:
                throw new IllegalArgumentException("Unknown codec: " + codec);
        }
        Buffer buffer = request.payloadBody();
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        List<Span> decoded = new ArrayList<>();
        if (multipleSpans) {
            decoder.decodeList(data, decoded);
        } else {
            decoder.decode(data, decoded);
        }
        return decoded;
    }

    private HttpReporter initReporter(final Codec codec, final UnaryOperator<Builder> configurator) {
        reporter = configurator.apply(new Builder(forSingleAddress(serverHostAndPort(context))).codec(codec)).build();
        return reporter;
    }
}
