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
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.newProtocolVersion;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
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
    private final Connection<Object, Object> connection = mock(Connection.class);

    @Rule
    public final MockedSubscriberRule<StreamingHttpResponse> dataSubscriber1 = new MockedSubscriberRule<>();
    @Rule
    public final MockedSubscriberRule<StreamingHttpResponse> dataSubscriber2 = new MockedSubscriberRule<>();

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
        when(connection.onClosing()).thenReturn(never());
        when(connection.executionContext()).thenReturn(ctx);
        HttpClientConfig config = new HttpClientConfig(new TcpClientConfig(true));
        config.setMaxPipelinedRequests(2);
        readPublisher1 = new TestPublisher<>();
        readPublisher2 = new TestPublisher<>();
        writePublisher1 = new TestPublisher<>();
        writePublisher2 = new TestPublisher<>();
        when(connection.write(any(), any())).then(inv -> {
            Publisher<Object> publisher = inv.getArgument(0);
            return publisher.ignoreElements(); // simulate write consuming all
        });
        when(connection.read()).thenReturn(readPublisher1, readPublisher2);
        readPublisher1.sendOnSubscribe();
        readPublisher2.sendOnSubscribe();
        writePublisher1.sendOnSubscribe();
        writePublisher2.sendOnSubscribe();
        pipe = new PipelinedStreamingHttpConnection(connection, config.asReadOnly(), ctx, reqRespFactory);
    }

    @Test
    public void http09RequestShouldReturnOnError() {
        Single<StreamingHttpResponse> request = pipe.request(
                reqRespFactory.get("/Foo").version(newProtocolVersion(0, 9)));
        dataSubscriber1.subscribe(request).request(1).verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void http10RequestShouldReturnOnError() {
        Single<StreamingHttpResponse> request = pipe.request(reqRespFactory.get("/Foo").version(HTTP_1_0));
        dataSubscriber1.subscribe(request).request(1).verifyFailure(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void http11RequestShouldCompleteSuccessfully() {
        reset(connection); // Simplified mocking
        when(connection.executionContext()).thenReturn(ctx);
        when(connection.write(any(), any())).thenReturn(completed());
        when(connection.read()).thenReturn(Publisher.from(reqRespFactory.ok(), emptyLastChunk));
        Single<StreamingHttpResponse> request = pipe.request(
                reqRespFactory.get("/Foo"));
        dataSubscriber1.subscribe(request).request(1).verifySuccess();
    }

    @Test
    public void ensureRequestsArePipelined() {
        dataSubscriber1.subscribe(pipe.request(reqRespFactory.get("/foo").payloadBody(writePublisher1))).request(1);
        dataSubscriber2.subscribe(pipe.request(reqRespFactory.get("/bar").payloadBody(writePublisher2))).request(1);

        readPublisher1.verifyNotSubscribed();
        readPublisher2.verifyNotSubscribed();
        writePublisher1.verifySubscribed();
        writePublisher2.verifyNotSubscribed();

        writePublisher1.onComplete();
        readPublisher1.verifySubscribed(); // read after write completes, pipelining will be full-duplex in follow-up PR
        readPublisher1.sendItems(mockResp);

        writePublisher2.verifySubscribed(); // pipelining in action – 2nd req writing while 1st req still reading
        writePublisher2.onComplete();

        readPublisher1.onComplete();
        dataSubscriber1.request(1).verifySuccess();

        readPublisher2.verifySubscribed();
        readPublisher2.sendItems(mockResp).onComplete();
        dataSubscriber2.request(1).verifySuccess();
    }
}
