/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.ExecutionContextToHttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.immediate;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinedHttpConnectionTest {

    @RegisterExtension
    final ExecutionContextExtension ctx = immediate();

    @SuppressWarnings("unchecked")
    private final NettyConnection<Object, Object> connection = mock(NettyConnection.class);

    private final TestSingleSubscriber<StreamingHttpResponse> dataSubscriber1 = new TestSingleSubscriber<>();
    private final TestSingleSubscriber<StreamingHttpResponse> dataSubscriber2 = new TestSingleSubscriber<>();

    private final TestPublisher<Object> readPublisher1 = new TestPublisher<>();
    private final TestPublisher<Object> readPublisher2 = new TestPublisher<>();
    private final TestPublisher<Buffer> writePublisher1 = new TestPublisher<>();
    private final TestPublisher<Buffer> writePublisher2 = new TestPublisher<>();

    private final HttpHeaders emptyLastChunk = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
    private final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);
    private final StreamingHttpResponse mockResp = reqRespFactory.ok();

    private StreamingHttpConnection pipe;

    private void commonSetup(NettyConnection<Object, Object> connection) {
        when(connection.onClose()).thenReturn(never());
        when(connection.onClosing()).thenReturn(never());
        when(connection.executionContext())
                .thenReturn((ExecutionContext) new ExecutionContextToHttpExecutionContext(ctx, defaultStrategy()));
        when(connection.protocol()).thenReturn(HTTP_1_1);
        when(connection.updateFlushStrategy(any())).thenReturn(IGNORE_CANCEL);

        pipe = TestStreamingHttpConnection.from(
                new PipelinedStreamingHttpConnection(connection, h1().maxPipelinedRequests(2).build(),
                        reqRespFactory, false));
    }

    private void fullSetup(NettyConnection<Object, Object> connection) {
        commonSetup(connection);
        when(connection.write(any())).then(inv -> {
            Publisher<Object> publisher = inv.getArgument(0);
            return publisher.ignoreElements(); // simulate write consuming all
        });
        when(connection.write(any(), any(), any())).then(inv -> {
            Publisher<Object> publisher = inv.getArgument(0);
            return publisher.ignoreElements(); // simulate write consuming all
        });
        when(connection.read()).thenReturn(readPublisher1, readPublisher2);
    }

    @Test
    void http11RequestShouldCompleteSuccessfully() {
        commonSetup(connection);
        when(connection.write(any())).thenReturn(completed());
        when(connection.write(any(), any(), any())).thenReturn(completed());
        when(connection.read()).thenReturn(Publisher.from(reqRespFactory.ok(), emptyLastChunk));

        Single<StreamingHttpResponse> request = pipe.request(reqRespFactory.get("/Foo"));
        toSource(request).subscribe(dataSubscriber1);
        assertNotNull(dataSubscriber1.awaitOnSuccess());
    }

    @Test
    void ensureRequestsArePipelined() {
        fullSetup(connection);
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
