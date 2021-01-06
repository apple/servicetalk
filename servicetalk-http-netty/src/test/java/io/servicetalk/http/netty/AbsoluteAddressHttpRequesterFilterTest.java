/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AbsoluteAddressHttpRequesterFilterTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Mock
    private FilterableStreamingHttpClient delegate;
    @Mock
    private StreamingHttpResponse response;
    private final HttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);
    private final StreamingHttpRequest request = StreamingHttpRequests.newRequest(HttpRequestMethod.GET, "",
            HttpProtocolVersion.HTTP_1_1, headersFactory.newHeaders(), DEFAULT_ALLOCATOR, headersFactory);
    private final ArgumentCaptor<StreamingHttpRequest> requestCapture =
            ArgumentCaptor.forClass(StreamingHttpRequest.class);
    private StreamingHttpClientFilter filter;

    @Before
    public void setup() {
        when(delegate.request(any(), any())).thenReturn(succeeded(response));
        filter = new AbsoluteAddressHttpRequesterFilter("http", "host:80").create(delegate);
    }

    @Test
    public void shouldAddAuthorityToOriginFormRequestTarget() throws Exception {
        request.requestTarget("/path?query");
        filter.request(noOffloadsStrategy(), request).toFuture().get();
        verify(delegate).request(any(), requestCapture.capture());

        final StreamingHttpRequest capturedRequest = requestCapture.getValue();
        assertThat(capturedRequest.requestTarget(), is("http://host:80/path?query"));
    }

    @Test
    public void shouldNotAddAuthorityToAbsoluteFormRequestTarget() throws Exception {
        request.requestTarget("https://otherhost:443/otherpath?otherQuery");
        filter.request(noOffloadsStrategy(), request).toFuture().get();
        verify(delegate).request(any(), requestCapture.capture());

        final StreamingHttpRequest capturedRequest = requestCapture.getValue();
        assertThat(capturedRequest.requestTarget(), is("https://otherhost:443/otherpath?otherQuery"));
    }
}
