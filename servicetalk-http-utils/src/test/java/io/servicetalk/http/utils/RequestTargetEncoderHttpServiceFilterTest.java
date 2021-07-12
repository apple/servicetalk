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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.TestHttpServiceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.lang.Character.forDigit;
import static java.lang.Character.toUpperCase;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestTargetEncoderHttpServiceFilterTest {
    private final StreamingHttpService mockService = mock(StreamingHttpService.class);
    private final HttpExecutionContext mockExecutionCtx = mock(HttpExecutionContext.class);
    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private final HttpServiceContext mockCtx = new TestHttpServiceContext(DefaultHttpHeadersFactory.INSTANCE,
            reqRespFactory, mockExecutionCtx);

    @BeforeEach
    void setup() {
        doAnswer((Answer<Single<StreamingHttpResponse>>) invocation -> {
            StreamingHttpRequest request = invocation.getArgument(1);
            StreamingHttpResponse response = mock(StreamingHttpResponse.class);
            when(response.status()).thenReturn(OK);
            when(response.payloadBody()).thenReturn(from(DEFAULT_ALLOCATOR.fromAscii(request.requestTarget())));
            return Single.succeeded(response);
        }).when(mockService).handle(any(), any(), any());
    }

    @Test
    void noEncodeRequired() throws Exception {
        String requestTarget = "/path?key=value";
        invokeServiceAssertValue(requestTarget, requestTarget);
    }

    @Test
    void equalsPreservedForKeyValues() throws Exception {
        invokeServiceAssertValue("/path?key=<value>&key2=value2", "/path?key=%3Cvalue%3E&key2=value2");
    }

    @Test
    void preexistingEncodePreserved() throws Exception {
        String requestTarget = "/path?key=a%20b";
        invokeServiceAssertValue(requestTarget, requestTarget);
    }

    @Test
    void upToSpaceCharsEscaped() throws Exception {
        StringBuilder requestTargetBuilder = new StringBuilder();
        StringBuilder expectedBuilder = new StringBuilder();
        requestTargetBuilder.append("/path?");
        expectedBuilder.append("/path?");
        for (int i = 0; i < 32; ++i) {
            requestTargetBuilder.append((char) i);
            expectedBuilder.append('%')
                    .append(toUpperCase(forDigit((i >>> 4) & 0xF, 16)))
                    .append(toUpperCase(forDigit(i & 0xF, 16)));
        }
        invokeServiceAssertValue(requestTargetBuilder.toString(), expectedBuilder.toString());
    }

    private void invokeServiceAssertValue(String requestTarget, String expectedPayload) throws Exception {
        StreamingHttpResponse response = new RequestTargetEncoderHttpServiceFilter().create(mockService)
                .handle(mockCtx, reqRespFactory.get(requestTarget), reqRespFactory)
                .toFuture().get();
        String payload = response.payloadBody().collect(DEFAULT_ALLOCATOR::newCompositeBuffer, (composite, buf) -> {
            composite.addBuffer(buf);
            return composite;
        }).toFuture().get().toString(US_ASCII);
        assertThat(payload, is(expectedPayload));
    }
}
