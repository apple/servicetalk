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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestHandler;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class ResponseStatusLoggingStreamingHttpServiceFilterTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Mock
    private HttpServiceContext mockCtx;

    @Mock
    private StreamingHttpRequest mockRequest;

    @Mock
    private StreamingHttpResponseFactory mockFactory;

    @Mock
    private StreamingHttpResponse mockResponse;
    @Mock
    private StreamingHttpResponse mockResponse2;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockFactory.ok()).thenReturn(mockResponse);
        when(mockResponse.payloadBody()).thenReturn(empty());
        when(mockResponse.transformRawPayloadBody(any())).thenReturn(mockResponse);
        doAnswer((Answer<StreamingHttpResponse>) invocation -> {
            UnaryOperator<Publisher<?>> transformer = invocation.getArgument(0);
            when(mockResponse2.payloadBodyAndTrailers()).thenReturn(transformer.apply(empty())
                    .map(o -> (Object) o));
            return mockResponse2;
        }).when(mockResponse).transformRawPayloadBody(any());
        when(mockResponse.status()).thenReturn(OK);
    }

    @Test
    public void successfulRequestResponse() throws ExecutionException, InterruptedException {
        new ResponseStatusLoggingStreamingHttpServiceFilter("testServer",
                        ((StreamingHttpRequestHandler) (ctx, request, responseFactory) ->
                                success(responseFactory.ok())).asStreamingService())
                .handle(mockCtx, mockRequest, mockFactory)
                .toFuture().get().payloadBodyAndTrailers().ignoreElements().toFuture().get();
        // we are not asserting that the log methods are actually called, but instead just verified code paths
        // execute "normally".
    }
}
