/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TimeoutHttpRequesterFilterTest extends AbstractTimeoutHttpFilterTest {

    @Override
    void newFilter(Duration duration) {
        new TimeoutHttpRequesterFilter(duration);
    }

    @Override
    Single<StreamingHttpResponse> applyFilter(Duration duration, boolean fullRequestResponse,
                                              final HttpExecutionStrategy strategy,
                                              final Single<StreamingHttpResponse> responseSingle) {
        return applyFilter(new TimeoutHttpRequesterFilter(duration, fullRequestResponse),
                strategy, responseSingle);
    }

    @Override
    Single<StreamingHttpResponse> applyFilter(BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                                              boolean fullRequestResponse,
                                              final HttpExecutionStrategy strategy,
                                              final Single<StreamingHttpResponse> responseSingle) {
        return applyFilter(new TimeoutHttpRequesterFilter(timeoutForRequest, fullRequestResponse),
                strategy, responseSingle);
    }

    private static Single<StreamingHttpResponse> applyFilter(TimeoutHttpRequesterFilter filterFactory,
                                                             final HttpExecutionStrategy strategy,
                                                             final Single<StreamingHttpResponse> responseSingle) {
        HttpExecutionContext executionContext =
                new DefaultHttpExecutionContext(DEFAULT_ALLOCATOR, IO_EXECUTOR, EXECUTOR, strategy);

        HttpConnectionContext connectionContext = mock(HttpConnectionContext.class);
        when(connectionContext.executionContext()).thenReturn(executionContext);
        FilterableStreamingHttpConnection connection = mock(FilterableStreamingHttpConnection.class);
        when(connection.executionContext()).thenReturn(executionContext);
        when(connection.request(any())).thenReturn(responseSingle);

        StreamingHttpRequester requester = filterFactory.create(connection);
        return requester.request(mock(StreamingHttpRequest.class));
    }
}
