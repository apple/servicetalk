/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StreamingHttpServiceToBlockingStreamingHttpServiceTest {
    private final StreamingHttpRequestResponseFactory streamingReqResponseFactory =
            new DefaultStreamingHttpRequestResponseFactory(
                    DEFAULT_ALLOCATOR,
                    DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1
            );

    @Mock
    private HttpServiceContext ctx;

    @Mock
    private BlockingStreamingHttpServerResponse blockingStreamingHttpServerResponse;

    @BeforeEach
    void beforeEach() {
        AsyncContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sharesAsyncContext() throws Exception {
        // Given
        given(ctx.streamingResponseFactory()).willReturn(streamingReqResponseFactory);
        given(blockingStreamingHttpServerResponse.sendMetaData()).willReturn(mock(HttpPayloadWriter.class));
        ContextMap.Key<Integer> key = ContextMap.Key.newKey("TEST_KEY", Integer.class);
        StreamingHttpService baseStreamingService = (ctx,
                                                     request,
                                                     responseFactory) -> Single.succeeded(responseFactory.ok());
        StreamingHttpServiceFilterFactory streamingHttpServiceFilterFactory =
                service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        StreamingHttpResponseFactory responseFactory) {
                return Single.defer(() -> {
                            AsyncContext.put(key, 42);
                            return Single.succeeded(42);
                        })
                        .flatMap(unused -> delegate().handle(ctx, request, responseFactory));
            }
        };
        try (BlockingStreamingHttpService blockingStreamingHttpService =
                     new StreamingHttpServiceToBlockingStreamingHttpService(
                             streamingHttpServiceFilterFactory.create(baseStreamingService)
                     )
        ) {
            // When
            blockingStreamingHttpService.handle(
                    ctx,
                    mock(BlockingStreamingHttpRequest.class),
                    blockingStreamingHttpServerResponse
            );

            // Then
            verify(blockingStreamingHttpServerResponse).status(HttpResponseStatus.OK);
            assertTrue(AsyncContext.contains(key, 42));
        }
    }
}
