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

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpApiConversions.mayHaveTrailers;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class DeprecatedSerializerPayloadBodyTest {

    private static final StreamingHttpRequestResponseFactory FACTORY = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    @SuppressWarnings("deprecation")
    private static final HttpSerializer<String> SERIALIZER = HttpSerializationProviders.textSerializer();

    @Test
    @SuppressWarnings("deprecation")
    void requestPayloadBodyWithTrailersEmitsPayloadAndTrailers() throws Exception {
        final StreamingHttpRequest request = FACTORY.post("/").transform(new StatelessTrailersTransformer<>());
        assertThat(mayHaveTrailers(request), is(true));

        assertPayloadAndTrailers(request.payloadBody(from("a"), SERIALIZER).messageBody().toFuture().get());
    }

    @Test
    @SuppressWarnings("deprecation")
    void responsePayloadBodyWithTrailersEmitsPayloadAndTrailers() throws Exception {
        final StreamingHttpResponse response = FACTORY.ok().transform(new StatelessTrailersTransformer<>());
        assertThat(mayHaveTrailers(response), is(true));

        assertPayloadAndTrailers(response.payloadBody(from("a"), SERIALIZER).messageBody().toFuture().get());
    }

    private static void assertPayloadAndTrailers(final Collection<Object> messageBody) {
        final List<Object> items = new ArrayList<>(messageBody);
        assertThat(items, hasSize(2));
        assertThat(items.get(0), instanceOf(Buffer.class));
        assertThat(((Buffer) items.get(0)).toString(UTF_8), is("a"));
        assertThat(items.get(1), instanceOf(HttpHeaders.class));
    }
}
