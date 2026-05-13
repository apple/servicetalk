/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilterTest.REQ_RESP_FACTORY;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilterTest.newBufferPublisher;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PayloadSizeLimitingHttpServiceFilterTest {
    @ParameterizedTest
    @ValueSource(ints = {99, 100})
    void lessThanEqToMaxAllowed(int payloadLen) throws ExecutionException, InterruptedException {
        new PayloadSizeLimitingHttpServiceFilter(100)
                .create((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(request.payloadBody())))
                .handle(mock(HttpServiceContext.class),
                        REQ_RESP_FACTORY.post("/").payloadBody(newBufferPublisher(payloadLen, DEFAULT_ALLOCATOR)),
                        REQ_RESP_FACTORY).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void moreThanMaxRejected() {
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpServiceFilter(100)
                        .create((ctx, request, responseFactory) ->
                                succeeded(responseFactory.ok().payloadBody(request.payloadBody())))
                        .handle(mock(HttpServiceContext.class),
                                REQ_RESP_FACTORY.post("/").payloadBody(newBufferPublisher(101, DEFAULT_ALLOCATOR)),
                                REQ_RESP_FACTORY).toFuture().get()
                        .payloadBody().toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
    }

    @Test
    void contentLengthOverMaxRejectedAndDrainsBody() {
        AtomicBoolean drained = new AtomicBoolean();
        Publisher<Buffer> body = newBufferPublisher(50, DEFAULT_ALLOCATOR)
                .afterFinally(() -> drained.set(true));
        StreamingHttpRequest request = REQ_RESP_FACTORY.post("/").payloadBody(body);
        request.headers().set(CONTENT_LENGTH, "101");
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpServiceFilter(100)
                        .create((ctx, req, responseFactory) ->
                                succeeded(responseFactory.ok().payloadBody(req.payloadBody())))
                        .handle(mock(HttpServiceContext.class), request, REQ_RESP_FACTORY).toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
        assertThat("request payload was not drained before failing", drained.get(), is(true));
    }

    @Test
    void contentLengthAtMaxAllowed() throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = REQ_RESP_FACTORY.post("/")
                .payloadBody(newBufferPublisher(100, DEFAULT_ALLOCATOR));
        request.headers().set(CONTENT_LENGTH, "100");
        new PayloadSizeLimitingHttpServiceFilter(100)
                .create((ctx, req, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(req.payloadBody())))
                .handle(mock(HttpServiceContext.class), request, REQ_RESP_FACTORY).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void malformedContentLengthIgnored() throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = REQ_RESP_FACTORY.post("/")
                .payloadBody(newBufferPublisher(50, DEFAULT_ALLOCATOR));
        request.headers().set(CONTENT_LENGTH, "not-a-number");
        new PayloadSizeLimitingHttpServiceFilter(100)
                .create((ctx, req, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(req.payloadBody())))
                .handle(mock(HttpServiceContext.class), request, REQ_RESP_FACTORY).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void lyingContentLengthStillCaughtByStreamingLimiter() {
        // A Content-Length that under-reports the body size should be caught by the HTTP message codecs
        // in practice; this test exercises the filter's defense-in-depth fallback to the streaming counter.
        StreamingHttpRequest request = REQ_RESP_FACTORY.post("/")
                .payloadBody(newBufferPublisher(101, DEFAULT_ALLOCATOR));
        request.headers().set(CONTENT_LENGTH, "50");
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpServiceFilter(100)
                        .create((ctx, req, responseFactory) ->
                                succeeded(responseFactory.ok().payloadBody(req.payloadBody())))
                        .handle(mock(HttpServiceContext.class), request, REQ_RESP_FACTORY).toFuture().get()
                        .payloadBody().toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
    }

    @Test
    void expectContinueRequestIsNotDrained() {
        // When Expect: 100-continue is set and Content-Length exceeds the limit, the filter must fail
        // without subscribing to the body. If it subscribed, NettyHttpServer would write 100 Continue,
        // prompting the client to send bytes we do not want.
        AtomicBoolean subscribed = new AtomicBoolean();
        Publisher<Buffer> body = Publisher.defer(() -> {
            subscribed.set(true);
            return newBufferPublisher(50, DEFAULT_ALLOCATOR).shareContextOnSubscribe();
        });
        StreamingHttpRequest request = REQ_RESP_FACTORY.post("/").payloadBody(body);
        request.headers().set(CONTENT_LENGTH, "101");
        request.headers().set(EXPECT, CONTINUE);
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpServiceFilter(100)
                        .create((ctx, req, responseFactory) ->
                                succeeded(responseFactory.ok().payloadBody(req.payloadBody())))
                        .handle(mock(HttpServiceContext.class), request, REQ_RESP_FACTORY).toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
        assertThat("request payload was subscribed which would trigger 100 Continue",
                subscribed.get(), is(false));
    }
}
