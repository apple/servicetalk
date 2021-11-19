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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnforceSequentialModeRequesterFilterTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    private final FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);

    @BeforeEach
    void setUp() {
        when(client.request(any())).thenAnswer(invocation -> {
            // Simulate consumption of the request payload body:
            StreamingHttpRequest request = invocation.getArgument(0);
            request.payloadBody().forEach(__ -> { /* noop */ });
            return succeeded(REQ_RES_FACTORY.ok());
        });
    }

    @Test
    void responseCompletesAfterRequestPayloadBodyCompletes() {
        TestPublisher<Buffer> payloadBody = new TestPublisher<>();
        StreamingHttpRequest request = REQ_RES_FACTORY.post("/").payloadBody(payloadBody);

        FilterableStreamingHttpClient client = EnforceSequentialModeRequesterFilter.INSTANCE.create(this.client);
        StepVerifiers.create(client.request(request))
                .expectCancellable()
                .then(payloadBody::onComplete)
                .expectSuccess()
                .verify();
    }

    @Test
    void responseNeverCompletesIfRequestPayloadBodyNeverCompletes() {
        StreamingHttpRequest request = REQ_RES_FACTORY.post("/").payloadBody(never());

        StreamingHttpClientFilter client = EnforceSequentialModeRequesterFilter.INSTANCE.create(this.client);
        AssertionError e = assertThrows(AssertionError.class,
                () -> StepVerifiers.create(client.request(request))
                        .expectCancellable()
                        .expectSuccess()
                        .verify(Duration.ofMillis(100)));
        assertThat(e.getCause(), instanceOf(TimeoutException.class));
    }

    @Test
    void withoutFilterResponseCompletesIndependently() {
        StreamingHttpRequest request = REQ_RES_FACTORY.post("/").payloadBody(never());

        StepVerifiers.create(this.client.request(request))
                .expectCancellable()
                .expectSuccess()
                .verify();
    }
}
