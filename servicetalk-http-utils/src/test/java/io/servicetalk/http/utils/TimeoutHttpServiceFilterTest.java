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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;

import org.junit.Test;

import java.time.Duration;

import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
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
                                              Single<StreamingHttpResponse> responseSingle) {
        return applyFilter(new TimeoutHttpServiceFilter(duration, fullRequestResponse), responseSingle);
    }

    @Override
    Single<StreamingHttpResponse> applyFilter(TimeoutFromRequest timeoutForRequest, boolean fullRequestResponse,
                                              Single<StreamingHttpResponse> responseSingle) {
        return applyFilter(new TimeoutHttpServiceFilter(timeoutForRequest, fullRequestResponse), responseSingle);
    }

    private static Single<StreamingHttpResponse> applyFilter(TimeoutHttpServiceFilter filterFactory,
                                                             Single<StreamingHttpResponse> responseSingle) {
        StreamingHttpService service = mock(StreamingHttpService.class);
        when(service.handle(any(), any(), any())).thenReturn(responseSingle);

        StreamingHttpServiceFilter filter = filterFactory.create(service);
        return filter.handle(mock(HttpServiceContext.class), mock(StreamingHttpRequest.class),
                mock(StreamingHttpResponseFactory.class));
    }

    @Test
    public void verifyAsyncContext() throws Exception {
        verifyServerFilterAsyncContextVisibility(new TimeoutHttpServiceFilter(Duration.ofDays(1), true));
    }
}
