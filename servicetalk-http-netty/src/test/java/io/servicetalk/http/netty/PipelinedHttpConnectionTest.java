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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.TestPublisher.newTestPublisher;
import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.newProtocolVersion;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class PipelinedHttpConnectionTest {

    @Rule
    public final Timeout serviceTalkTestTimeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExecutionContextRule ctx = immediate();

    @SuppressWarnings("unchecked")
    private final NettyConnection<Object, Object> connection = mock(NettyConnection.class);

    private final TestPublisherSubscriber<StreamingHttpResponse> dataSubscriber1 = newTestPublisherSubscriber();
    private final TestPublisherSubscriber<StreamingHttpResponse> dataSubscriber2 = newTestPublisherSubscriber();

    private TestPublisher<Object> readPublisher1;
    private TestPublisher<Object> readPublisher2;
    private TestPublisher<Buffer> writePublisher1;
    private TestPublisher<Buffer> writePublisher2;

    private StreamingHttpConnection pipe;

    private final HttpHeaders emptyLastChunk = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
    private StreamingHttpResponse mockResp;
    private final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE);

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        mockResp = reqRespFactory.ok();
        when(connection.onClose()).thenReturn(never());
        when(connection.executionContext()).thenReturn(ctx);
        HttpClientConfig config = new HttpClientConfig(new TcpClientConfig(true));
        config.maxPipelinedRequests(2);
        readPublisher1 = newTestPublisher();
        readPublisher2 = newTestPublisher();
        writePublisher1 = newTestPublisher();
        writePublisher2 = newTestPublisher();
        when(connection.write(any())).then(inv -> {
            Publisher<Object> publisher = inv.getArgument(0);
            return publisher.ignoreElements(); // simulate write consuming all
        });
        when(connection.read()).thenReturn(readPublisher1, readPublisher2);
        pipe = new PipelinedStreamingHttpConnection(connection, config.asReadOnly(), ctx, reqRespFactory,
                defaultStrategy());
    }

    @Test
    public void http09RequestShouldReturnOnError() {
        Single<StreamingHttpResponse> request = pipe.request(
                reqRespFactory.get("/Foo").version(newProtocolVersion(0, 9)));
        toSource(request).subscribe(dataSubscriber1.forSingle());
        dataSubscriber1.request(1);
        assertThat(dataSubscriber1.error(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void http10RequestShouldReturnOnError() {
        Single<StreamingHttpResponse> request = pipe.request(reqRespFactory.get("/Foo").version(HTTP_1_0));
        toSource(request).subscribe(dataSubscriber1.forSingle());
        dataSubscriber1.request(1);
        assertThat(dataSubscriber1.error(), instanceOf(IllegalArgumentException.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void http11RequestShouldCompleteSuccessfully() {
        reset(connection); // Simplified mocking
        when(connection.executionContext()).thenReturn(ctx);
        when(connection.write(any())).thenReturn(completed());
        when(connection.read()).thenReturn(Publisher.from(reqRespFactory.ok(), emptyLastChunk));
        Single<StreamingHttpResponse> request = pipe.request(
                reqRespFactory.get("/Foo"));
        toSource(request).subscribe(dataSubscriber1.forSingle());
        dataSubscriber1.request(1);
        assertTrue(dataSubscriber1.isCompleted());
    }

    @Test
    public void ensureRequestsArePipelined() {
        toSource(pipe.request(reqRespFactory.get("/foo").payloadBody(writePublisher1)))
                .subscribe(dataSubscriber1.forSingle());
        dataSubscriber1.request(1);
        toSource(pipe.request(reqRespFactory.get("/bar").payloadBody(writePublisher2)))
                .subscribe(dataSubscriber2.forSingle());
        dataSubscriber2.request(1);

        assertFalse(readPublisher1.isSubscribed());
        assertFalse(readPublisher2.isSubscribed());
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());

        writePublisher1.onComplete();
        assertTrue(readPublisher1.isSubscribed()); // read after write completes, pipelining will be full-duplex in follow-up PR
        readPublisher1.onNext(mockResp);

        assertTrue(writePublisher2.isSubscribed()); // pipelining in action – 2nd req writing while 1st req still reading
        writePublisher2.onComplete();

        readPublisher1.onComplete();
        dataSubscriber1.request(1);
        assertTrue(dataSubscriber1.isCompleted());

        assertTrue(readPublisher2.isSubscribed());
        readPublisher2.onNext(mockResp);
        readPublisher2.onComplete();
        dataSubscriber2.request(1);
        assertTrue(dataSubscriber1.isCompleted());
    }
}
