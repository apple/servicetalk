/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.TimeSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;

import java.time.Duration;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TimeoutHttpServiceFilterTest extends AbstractTimeoutHttpFilterTest {

    @Override
    void newFilter(Duration duration) {
        new TimeoutHttpServiceFilter(duration);
    }

    @Override
    Single<StreamingHttpResponse> applyFilter(Duration duration, boolean fullRequestResponse,
                                              final HttpExecutionStrategy strategy,
                                              Single<StreamingHttpResponse> responseSingle) {
        return applyFilter(new TimeoutHttpServiceFilter(duration, fullRequestResponse), strategy, responseSingle);
    }

    @Override
    Single<StreamingHttpResponse> applyFilter(BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                                              boolean fullRequestResponse,
                                              final HttpExecutionStrategy strategy,
                                              Single<StreamingHttpResponse> responseSingle) {
        return applyFilter(new TimeoutHttpServiceFilter(timeoutForRequest, fullRequestResponse),
                strategy, responseSingle);
    }

    private static Single<StreamingHttpResponse> applyFilter(TimeoutHttpServiceFilter filterFactory,
                                                             final HttpExecutionStrategy strategy,
                                                             final Single<StreamingHttpResponse> responseSingle) {
        HttpExecutionContext executionContext =
                new DefaultHttpExecutionContext(DEFAULT_ALLOCATOR, IO_EXECUTOR, EXECUTOR, strategy);

        HttpServiceContext serviceContext = mock(HttpServiceContext.class);
        when(serviceContext.executionContext()).thenReturn(executionContext);
        StreamingHttpService service = mock(StreamingHttpService.class);
        when(service.handle(any(), any(), any())).thenReturn(responseSingle);

        StreamingHttpServiceFilter filter = filterFactory.create(service);
        StreamingHttpRequest request = mock(StreamingHttpRequest.class);
        ContextMap requestContext = mock(ContextMap.class);
        when(request.context()).thenReturn(requestContext);
        when(requestContext.getOrDefault(HTTP_EXECUTION_STRATEGY_KEY, strategy)).thenReturn(strategy);
        return filter.handle(serviceContext, request, mock(StreamingHttpResponseFactory.class));
    }
}
