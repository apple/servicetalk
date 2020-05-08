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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.opentracing.zipkin.publisher.reporter.HttpReporter.Builder;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThrows;

@RunWith(Parameterized.class)
public class HttpReporterTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final BlockingQueue<HttpRequest> receivedRequests;
    private final ServerContext context;
    private final Codec codec;
    @Nullable
    private HttpReporter reporter;

    public HttpReporterTest(final Codec codec) throws Exception {
        this.codec = codec;
        receivedRequests = new LinkedBlockingQueue<>();
        this.context = forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    receivedRequests.add(request);
                    return responseFactory.ok();
                });
    }

    @Parameterized.Parameters(name = "codec: {0}")
    public static Collection<Codec> data() {
        return asList(Codec.values());
    }

    @After
    public void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (reporter != null) {
            closeable.append(reporter);
        }
        closeable.append(context);
        closeable.closeGracefully();
    }

    @Test
    public void disableBatching() throws Exception {
        HttpReporter reporter = initReporter(Builder::disableSpanBatching);
        reporter.report(newSpan());
        List<Span> spans = verifyRequest(receivedRequests.take(), false);
        assertThat("Unexpected spans received.", spans, hasSize(1));
        verifySpan(spans.get(0));
        reporter.report(newSpan());
        assertThat("Unexpected spans received.", spans, hasSize(1));
        verifySpan(spans.get(0));
    }

    @Test
    public void batching() throws Exception {
        HttpReporter reporter = initReporter(builder -> builder.batchSpans(2, ofMillis(200)));
        reporter.report(newSpan());
        reporter.report(newSpan());
        List<Span> spans = verifyRequest(receivedRequests.take(), true);
        assertThat("Unexpected spans received.", spans, hasSize(2));
        verifySpan(spans.get(0));
        verifySpan(spans.get(0));
    }

    @Test
    public void reportAfterClose() {
        HttpReporter reporter = initReporter(Builder::disableSpanBatching);
        reporter.close();
        assertThrows("Report post close accepted.", IllegalStateException.class, () -> reporter.report(newSpan()));
    }

    private List<Span> verifyRequest(final HttpRequest request, final boolean multipleSpans) {
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

    private HttpReporter initReporter(UnaryOperator<Builder> configurator) {
        reporter = configurator.apply(new Builder(forSingleAddress(serverHostAndPort(context))).codec(codec)).build();
        return reporter;
    }
}
