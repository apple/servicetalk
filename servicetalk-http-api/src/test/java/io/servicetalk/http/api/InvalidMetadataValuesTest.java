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

import io.servicetalk.buffer.netty.BufferAllocators;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SuppressWarnings("ConstantConditions")
public class InvalidMetadataValuesTest {

    @SuppressWarnings("unused")
    private static Stream<Arguments> data() throws ExecutionException, InterruptedException {
        return Stream.of(
        Arguments.of(newRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE), "streaming request"),
        Arguments.of(newResponse(HttpResponseStatus.OK, HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE), "streaming response"),
        Arguments.of(newRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toRequest().toFuture().get(), "request"),
        Arguments.of(newResponse(HttpResponseStatus.OK, HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toResponse().toFuture().get(), "response"),
        Arguments.of(newRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toBlockingStreamingRequest(), "blocking streaming request"),
        Arguments.of(newResponse(HttpResponseStatus.OK, HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toBlockingStreamingResponse(), "blocking streaming response"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullHeaderNameToAdd(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addHeader(null, "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void emptyHeaderNameToAdd(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addHeader("", "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullHeaderValueToAdd(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addHeader("foo", null));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullHeaderNameToSet(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.setHeader(null, "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void emptyHeaderNameToSet(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.setHeader("", "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullHeaderValueToSet(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.setHeader("foo", null));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullQPNameToAdd(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        HttpRequestMetaData requestMeta = assumeRequestMeta(metaData);
        assertThrows(IllegalArgumentException.class, () -> requestMeta.addQueryParameter(null, "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void emptyQPNameToAdd(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        HttpRequestMetaData requestMeta = assumeRequestMeta(metaData);
        assertThrows(IllegalArgumentException.class, () -> requestMeta.addQueryParameter("", "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullQPValueToAdd(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        HttpRequestMetaData requestMeta = assumeRequestMeta(metaData);
        assertThrows(IllegalArgumentException.class, () -> requestMeta.addQueryParameter("foo", null));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullQPNameToSet(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        HttpRequestMetaData requestMeta = assumeRequestMeta(metaData);
        assertThrows(IllegalArgumentException.class, () -> requestMeta.setQueryParameter(null, ""));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void emptyQPNameToSet(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        HttpRequestMetaData requestMeta = assumeRequestMeta(metaData);
        assertThrows(IllegalArgumentException.class, () -> requestMeta.setQueryParameter("", "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullQPValueToSet(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        HttpRequestMetaData requestMeta = assumeRequestMeta(metaData);
        assertThrows(IllegalArgumentException.class, () -> requestMeta.setQueryParameter("foo", null));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullCookieName(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addCookie(null, "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void emptyCookieName(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addCookie("", ""));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullCookieValue(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addCookie("foo", null));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullSetCookieName(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addSetCookie(null, "foo"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void emptySetCookieName(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addSetCookie("", ""));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: source = {1}")
    @MethodSource("data")
    void nullSetCookieValue(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        assertThrows(IllegalArgumentException.class, () -> metaData.addSetCookie("foo", null));
    }

    private static HttpRequestMetaData assumeRequestMeta(final HttpMetaData metaData) {
        assumeTrue(metaData instanceof HttpRequestMetaData, "Test not applicable for response.");
        return (HttpRequestMetaData) metaData;
    }
}
