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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.ExecutionContextToHttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    private final TestSingleSubscriber<StreamingHttpResponse> dataSubscriber1 = new TestSingleSubscriber<>();
    private final TestSingleSubscriber<StreamingHttpResponse> dataSubscriber2 = new TestSingleSubscriber<>();

    private TestPublisher<Object> readPublisher1;
    private TestPublisher<Object> readPublisher2;
    private TestPublisher<Buffer> writePublisher1;
    private TestPublisher<Buffer> writePublisher2;

    private StreamingHttpConnection pipe;

    private final HttpHeaders emptyLastChunk = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
    private StreamingHttpResponse mockResp;
    private final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        mockResp = reqRespFactory.ok();
        when(connection.onClose()).thenReturn(never());
        when(connection.onClosing()).thenReturn(never());
        when(connection.executionContext()).thenReturn(ctx);
        readPublisher1 = new TestPublisher<>();
        readPublisher2 = new TestPublisher<>();
        writePublisher1 = new TestPublisher<>();
        writePublisher2 = new TestPublisher<>();
        when(connection.write(any())).then(inv -> {
            Publisher<Object> publisher = inv.getArgument(0);
            return publisher.ignoreElements(); // simulate write consuming all
        });
        when(connection.write(any(), any(), any())).then(inv -> {
            Publisher<Object> publisher = inv.getArgument(0);
            return publisher.ignoreElements(); // simulate write consuming all
        });
        when(connection.updateFlushStrategy(any())).thenReturn(IGNORE_CANCEL);
        when(connection.read()).thenReturn(readPublisher1, readPublisher2);

        pipe = TestStreamingHttpConnection.from(
                new PipelinedStreamingHttpConnection(connection, h1().maxPipelinedRequests(2).build(),
                        new ExecutionContextToHttpExecutionContext(ctx, defaultStrategy()),
                        reqRespFactory, false));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void http11RequestShouldCompleteSuccessfully() {
        reset(connection); // Simplified mocking
        when(connection.executionContext()).thenReturn(ctx);
        when(connection.write(any())).thenReturn(completed());
        when(connection.write(any(), any(), any())).thenReturn(completed());
        when(connection.read()).thenReturn(Publisher.from(reqRespFactory.ok(), emptyLastChunk));
        when(connection.updateFlushStrategy(any())).thenReturn(IGNORE_CANCEL);
        Single<StreamingHttpResponse> request = pipe.request(reqRespFactory.get("/Foo"));
        toSource(request).subscribe(dataSubscriber1);
        assertNotNull(dataSubscriber1.awaitOnSuccess());
    }

    @Test
    public void ensureRequestsArePipelined() {
        toSource(pipe.request(reqRespFactory.get("/foo").payloadBody(writePublisher1)))
                .subscribe(dataSubscriber1);
        toSource(pipe.request(reqRespFactory.get("/bar").payloadBody(writePublisher2)))
                .subscribe(dataSubscriber2);

        assertTrue(readPublisher1.isSubscribed());
        assertFalse(readPublisher2.isSubscribed());
        assertTrue(writePublisher1.isSubscribed());
        assertFalse(writePublisher2.isSubscribed());

        writePublisher1.onComplete();
        // read after write completes, pipelining will be full-duplex in follow-up PR
        assertTrue(readPublisher1.isSubscribed());
        readPublisher1.onNext(mockResp);

        // pipelining in action – 2nd req writing while 1st req still reading
        assertTrue(writePublisher2.isSubscribed());
        writePublisher2.onComplete();

        readPublisher1.onComplete();
        assertNotNull(dataSubscriber1.awaitOnSuccess());

        assertTrue(readPublisher2.isSubscribed());
        readPublisher2.onNext(mockResp);
        readPublisher2.onComplete();
        assertNotNull(dataSubscriber2.awaitOnSuccess());
    }
}
