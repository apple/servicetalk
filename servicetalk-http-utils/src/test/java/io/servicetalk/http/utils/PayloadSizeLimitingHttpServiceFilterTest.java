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

import io.servicetalk.http.api.HttpServiceContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilterTest.REQ_RESP_FACTORY;
import static io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilterTest.newBufferPublisher;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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
}
