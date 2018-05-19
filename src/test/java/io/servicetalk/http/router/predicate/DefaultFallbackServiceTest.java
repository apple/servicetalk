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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class DefaultFallbackServiceTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ConnectionContext ctx;
    @Mock
    private HttpRequest<HttpPayloadChunk> request;

    @Before
    public void setUp() {
        when(request.getVersion()).thenReturn(HTTP_1_1);
        when(ctx.getExecutor()).thenReturn(immediate());
    }

    @Test
    public void testDefaultFallbackService() throws Exception {
        final HttpService fixture = DefaultFallbackService.instance();

        final Single<HttpResponse<HttpPayloadChunk>> responseSingle = fixture.handle(ctx, request);

        final HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(responseSingle);
        assert response != null;
        assertEquals(HTTP_1_1, response.getVersion());
        assertEquals(404, response.getStatus().getCode());
        assertEquals("Not Found", response.getStatus().getReasonPhrase().toString(US_ASCII));
        assertEquals("0", response.getHeaders().get("content-length"));
        assertEquals(newAsciiString("text/plain"), response.getHeaders().get("content-type"));
    }
}
