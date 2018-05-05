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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.BlockingIterator;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class AbstractBlockingHttpRequesterTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Mock
    private ExecutionContext mockExecutionCtx;
    @Mock
    private ConnectionContext mockCtx;
    @Rule
    public final PublisherRule<String> publisherRule = new PublisherRule<>();
    @Mock
    private BlockingIterable<String> mockIterable;
    @Mock
    private BlockingIterator<String> mockIterator;

    protected abstract <I, O, T extends HttpRequester<I, O> & TestHttpRequester>
        T newAsyncRequester(ExecutionContext executionContext,
                            Function<HttpRequest<I>, Single<HttpResponse<O>>> doRequest);

    protected abstract <I, O, T extends BlockingHttpRequester<I, O> & TestHttpRequester>
        T newBlockingRequester(ExecutionContext executionContext,
                               Function<BlockingHttpRequest<I>, BlockingHttpResponse<O>> doRequest);

    protected interface TestHttpRequester {
        boolean isClosed();
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockExecutionCtx.getExecutor()).thenReturn(immediate());
        when(mockCtx.getExecutionContext()).thenReturn(mockExecutionCtx);
        when(mockIterable.iterator()).thenReturn(mockIterator);
    }

    @Test
    public void asyncToSyncNoPayload() throws Exception {
        HttpRequester<String, String> asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> success(HttpResponses.<String>newResponse(HTTP_1_1, OK, immediate())));
        BlockingHttpRequester<String, String> syncRequester = asyncRequester.asBlockingRequester();
        BlockingHttpResponse<String> syncResponse = syncRequester.request(
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/", immediate()));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        HttpRequester<String, String> asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> success(newResponse(HTTP_1_1, OK, just("hello", immediate()))));
        BlockingHttpRequester<String, String> syncRequester = asyncRequester.asBlockingRequester();
        BlockingHttpResponse<String> syncResponse = syncRequester.request(
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/", immediate()));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<String> iterator = syncResponse.getPayloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals("hello", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void asyncToSyncClose() throws Exception {
        HttpRequester<String, String> asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> Single.<HttpResponse<String>>error(new IllegalStateException("shouldn't be called!")));
        BlockingHttpRequester<String, String> syncRequester = asyncRequester.asBlockingRequester();
        syncRequester.close();
        assertTrue(((TestHttpRequester) asyncRequester).isClosed());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        HttpRequester<String, String> asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> success(newResponse(HTTP_1_1, OK, publisherRule.getPublisher())));
        BlockingHttpRequester<String, String> syncRequester = asyncRequester.asBlockingRequester();
        BlockingHttpResponse<String> syncResponse = syncRequester.request(
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/", immediate()));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<String> iterator = syncResponse.getPayloadBody().iterator();
        publisherRule.sendItems("hello");
        assertTrue(iterator.hasNext());
        iterator.close();
        publisherRule.verifyCancelled();
    }

    @Test
    public void syncToAsyncNoPayload() throws Exception {
        BlockingHttpRequester<String, String> syncRequester = newBlockingRequester(mockExecutionCtx,
                req -> BlockingHttpResponses.<String>newResponse(HTTP_1_1, OK, immediate()));
        HttpRequester<String, String> asyncRequester = syncRequester.asAsynchronousRequester();
        HttpResponse<String> asyncResponse = awaitIndefinitely(asyncRequester.request(
                HttpRequests.newRequest(HTTP_1_1, GET, "/", immediate())));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        BlockingHttpRequester<String, String> syncRequester = newBlockingRequester(mockExecutionCtx,
                req -> BlockingHttpResponses.newResponse(HTTP_1_1, OK, singleton("hello"), immediate()));
        HttpRequester<String, String> asyncRequester = syncRequester.asAsynchronousRequester();
        HttpResponse<String> asyncResponse = awaitIndefinitely(asyncRequester.request(
                HttpRequests.newRequest(HTTP_1_1, GET, "/", immediate())));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
        assertEquals("hello", awaitIndefinitely(asyncResponse.getPayloadBody()
                .reduce(() -> "", (acc, next) -> acc + next)));
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        BlockingHttpRequester<String, String> syncRequester = newBlockingRequester(mockExecutionCtx,
                (Function<BlockingHttpRequest<String>, BlockingHttpResponse<String>>) req -> {
            throw new IllegalStateException("shouldn't be called!");
        });
        HttpRequester<String, String> asyncRequester = syncRequester.asAsynchronousRequester();
        awaitIndefinitely(asyncRequester.closeAsync());
        assertTrue(((TestHttpRequester) syncRequester).isClosed());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        BlockingHttpRequester<String, String> syncRequester = newBlockingRequester(mockExecutionCtx, req ->
                BlockingHttpResponses.newResponse(HTTP_1_1, OK, mockIterable, immediate()));
        HttpRequester<String, String> asyncRequester = syncRequester.asAsynchronousRequester();
        HttpResponse<String> asyncResponse = awaitIndefinitely(asyncRequester.request(
                HttpRequests.newRequest(HTTP_1_1, GET, "/", immediate())));
        assertNotNull(asyncResponse);
        CountDownLatch latch = new CountDownLatch(1);
        asyncResponse.getPayloadBody().subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
                latch.countDown();
            }

            @Override
            public void onNext(final String s) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        latch.await();
        verify(mockIterator).close();
    }
}
