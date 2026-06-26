/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(MockitoExtension.class)
class HttpExceptionMapperServiceFilterTest {

    @Mock
    private HttpExecutionContext executionContext;

    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private HttpServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestHttpServiceContext(DefaultHttpHeadersFactory.INSTANCE, reqRespFactory, executionContext);
    }

    @Test
    void payloadTooLargeMapsTo413() throws Exception {
        // The server-side aggregation limit raises PayloadTooLargeException; verify it deterministically maps to 413
        // independent of the connection-teardown race exercised by the netty integration test.
        StreamingHttpService service = (c, request, factory) ->
                Single.failed(new PayloadTooLargeException("too large"));
        StreamingHttpServiceFilter filter = HttpExceptionMapperServiceFilter.INSTANCE.create(service);

        StreamingHttpResponse response = filter.handle(ctx, reqRespFactory.newRequest(GET, "/"),
                reqRespFactory).toFuture().get();
        assertThat(response.status(), is(PAYLOAD_TOO_LARGE));
    }
}
