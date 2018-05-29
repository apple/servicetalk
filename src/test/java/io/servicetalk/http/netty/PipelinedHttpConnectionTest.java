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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersions.getProtocolVersion;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.netty.EmptyLastHttpPayloadChunk.INSTANCE;
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
    public final MockedSubscriberRule<HttpResponse<HttpPayloadChunk>> dataSubscriber1 = new MockedSubscriberRule<>();
    @Rule
    public final MockedSubscriberRule<HttpResponse<HttpPayloadChunk>> dataSubscriber2 = new MockedSubscriberRule<>();

    private TestPublisher<Object> readPublisher1;
    private TestPublisher<Object> readPublisher2;
    private TestPublisher<HttpPayloadChunk> writePublisher1;
    private TestPublisher<HttpPayloadChunk> writePublisher2;

    private HttpConnection pipe;

    private final LastHttpPayloadChunk emptyLastChunk = INSTANCE;
    private HttpResponse<HttpPayloadChunk> mockResp;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        mockResp = newResponse(OK, emptyLastChunk);
        when(connection.getExecutor()).thenReturn(ctx.getExecutor());
        when(connection.getExecutionContext()).thenReturn(ctx);
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
        pipe = new PipelinedHttpConnection(connection, config.asReadOnly(), ctx);
    }

    @Test
    public void http09RequestShouldReturnOnError() {
        Single<HttpResponse<HttpPayloadChunk>> request = pipe.request(
                newRequest(getProtocolVersion(0, 9), GET, "/Foo"));
        dataSubscriber1.subscribe(request).request(1).verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void http10RequestShouldReturnOnError() {
        Single<HttpResponse<HttpPayloadChunk>> request = pipe.request(newRequest(HTTP_1_0, GET, "/Foo"));
        dataSubscriber1.subscribe(request).request(1).verifyFailure(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void http11RequestShouldCompleteSuccessfully() {
        reset(connection); // Simplified mocking
        when(connection.getExecutor()).thenReturn(ctx.getExecutor());
        when(connection.getExecutionContext()).thenReturn(ctx);
        when(connection.write(any(), any())).thenReturn(completed());
        when(connection.read()).thenReturn(Publisher.from(newResponse(OK), emptyLastChunk));
        Single<HttpResponse<HttpPayloadChunk>> request = pipe.request(
                newRequest(HTTP_1_1, GET, "/Foo"));
        dataSubscriber1.subscribe(request).request(1).verifySuccess();
    }

    @Test
    public void ensureRequestsArePipelined() {
        dataSubscriber1.subscribe(pipe.request(newRequest(GET, "/foo", writePublisher1))).request(1);
        dataSubscriber2.subscribe(pipe.request(newRequest(GET, "/bar", writePublisher2))).request(1);

        readPublisher1.verifyNotSubscribed();
        readPublisher2.verifyNotSubscribed();
        writePublisher1.verifySubscribed();
        writePublisher2.verifyNotSubscribed();

        writePublisher1.sendItems(emptyLastChunk).onComplete();
        readPublisher1.verifySubscribed(); // read after write completes, pipelining will be full-duplex in follow-up PR
        readPublisher1.sendItems(mockResp);

        writePublisher2.verifySubscribed(); // pipelining in action – 2nd req writing while 1st req still reading
        writePublisher2.sendItems(emptyLastChunk).onComplete();

        readPublisher1.onComplete();
        dataSubscriber1.request(1).verifySuccess();

        readPublisher2.verifySubscribed();
        readPublisher2.sendItems(mockResp).onComplete();
        dataSubscriber2.request(1).verifySuccess();
    }
}
