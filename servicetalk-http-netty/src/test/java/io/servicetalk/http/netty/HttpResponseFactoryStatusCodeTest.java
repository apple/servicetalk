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

import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.BlockingStreamingHttpResponseFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class HttpResponseFactoryStatusCodeTest {

    private static List<HttpResponseStatus> expectedStatuses;

    @BeforeAll
    static void before() {
        expectedStatuses = IntStream.range(100, 600)
                .mapToObj(code -> HttpResponseStatus.of(Integer.toString(code)))
                .filter(status -> !"unknown".equals(status.reasonPhrase()))
                .collect(Collectors.toList());
    }

    @Test
    void testStatusCode() {
        final HttpResponseFactory httpResponseFactory =
                new DefaultHttpResponseFactory(INSTANCE, DEFAULT_ALLOCATOR, HTTP_1_1);

        List<HttpResponseStatus> actualStatuses =
                buildHttpStatuses(httpResponseFactory, HttpResponseFactory.class, HttpResponse.class);

        assertThat("unexpected statuses", actualStatuses, containsInAnyOrder(expectedStatuses.toArray()));
    }

    @Test
    void testBlockingStatusCode() {

        final BlockingStreamingHttpResponseFactory blockingStreamingHttpResponseFactory =
                new DefaultBlockingStreamingHttpResponseFactory(INSTANCE, DEFAULT_ALLOCATOR, HTTP_1_1);

        List<HttpResponseStatus> actualStatuses =
                buildHttpStatuses(blockingStreamingHttpResponseFactory,
                        BlockingStreamingHttpResponseFactory.class, BlockingStreamingHttpResponse.class);

        assertThat("unexpected statuses", actualStatuses, containsInAnyOrder(expectedStatuses.toArray()));
    }

    @Test
    void testStreamingStatusCode() {

        final StreamingHttpResponseFactory streamingHttpResponseFactory =
                new DefaultStreamingHttpResponseFactory(INSTANCE, DEFAULT_ALLOCATOR, HTTP_1_1);

        List<HttpResponseStatus> actualStatuses =
                buildHttpStatuses(streamingHttpResponseFactory,
                        StreamingHttpResponseFactory.class, StreamingHttpResponse.class);

        assertThat("unexpected statuses", actualStatuses, containsInAnyOrder(expectedStatuses.toArray()));
    }

    @SuppressWarnings("unchecked")
    private static <T, R extends HttpResponseMetaData> List<HttpResponseStatus> buildHttpStatuses(
            final T factory, final Class<T> factoryClass, final Class<R> returnType) {
        return Arrays.stream(factoryClass.getMethods())
                .filter(method -> method.getParameterCount() == 0 &&
                        method.getReturnType().equals(returnType))
                .map(method -> {
                    try {
                        return (R) method.invoke(factory);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(HttpResponseMetaData::status)
                .collect(Collectors.toList());
    }
}
