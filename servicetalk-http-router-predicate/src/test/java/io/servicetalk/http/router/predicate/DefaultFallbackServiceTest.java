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
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.jupiter.api.Test;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultFallbackServiceTest extends BaseHttpPredicateRouterBuilderTest {

    @Test
    void testDefaultFallbackService() throws Exception {
        final StreamingHttpService fixture = DefaultFallbackServiceStreaming.instance();

        final Single<StreamingHttpResponse> responseSingle = fixture.handle(ctx, request, reqRespFactory);

        final StreamingHttpResponse response = responseSingle.toFuture().get();
        assert response != null;
        assertEquals(HTTP_1_1, response.version());
        assertEquals(NOT_FOUND, response.status());
        assertEquals(ZERO, response.headers().get(CONTENT_LENGTH));
        assertEquals(TEXT_PLAIN_UTF_8, response.headers().get(CONTENT_TYPE));
    }
}
