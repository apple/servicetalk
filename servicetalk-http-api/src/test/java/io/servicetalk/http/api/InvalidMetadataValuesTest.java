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
package io.servicetalk.http.api;

import io.servicetalk.buffer.netty.BufferAllocators;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

@SuppressWarnings("ConstantConditions")
@RunWith(Parameterized.class)
public class InvalidMetadataValuesTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final HttpMetaData metaData;

    public InvalidMetadataValuesTest(final HttpMetaData metaData, @SuppressWarnings("unused") String testName) {
        this.metaData = metaData;
    }

    @Parameterized.Parameters(name = "{index}: source = {1}")
    public static List<Object[]> data() throws ExecutionException, InterruptedException {
        List<Object[]> params = new ArrayList<>();
        params.add(new Object[]{newRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE), "streaming request"});
        params.add(new Object[]{newResponse(HttpResponseStatus.OK, HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE), "streaming response"});
        params.add(new Object[]{newRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toRequest().toFuture().get(), "request"});
        params.add(new Object[]{newResponse(HttpResponseStatus.OK, HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toResponse().toFuture().get(), "response"});
        params.add(new Object[]{newRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toBlockingStreamingRequest(), "blocking streaming request"});
        params.add(new Object[]{newResponse(HttpResponseStatus.OK, HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).toBlockingStreamingResponse(), "blocking streaming response"});
        return params;
    }

    @Test
    public void nullHeaderNameToAdd() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addHeader(null, "foo");
    }

    @Test
    public void emptyHeaderNameToAdd() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addHeader("", "foo");
    }

    @Test
    public void nullHeaderValueToAdd() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addHeader("foo", null);
    }

    @Test
    public void nullHeaderNameToSet() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.setHeader(null, "foo");
    }

    @Test
    public void emptyHeaderNameToSet() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.setHeader("", "foo");
    }

    @Test
    public void nullHeaderValueToSet() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.setHeader("foo", null);
    }

    @Test
    public void nullQPNameToAdd() {
        HttpRequestMetaData requestMeta = assumeRequestMeta();
        expectedException.expect(IllegalArgumentException.class);
        requestMeta.addQueryParameter(null, "foo");
    }

    @Test
    public void emptyQPNameToAdd() {
        HttpRequestMetaData requestMeta = assumeRequestMeta();
        expectedException.expect(IllegalArgumentException.class);
        requestMeta.addQueryParameter("", "foo");
    }

    @Test
    public void nullQPValueToAdd() {
        HttpRequestMetaData requestMeta = assumeRequestMeta();
        expectedException.expect(IllegalArgumentException.class);
        requestMeta.addQueryParameter("foo", null);
    }

    @Test
    public void nullQPNameToSet() {
        HttpRequestMetaData requestMeta = assumeRequestMeta();
        expectedException.expect(IllegalArgumentException.class);
        requestMeta.setQueryParameter(null, "");
    }

    @Test
    public void emptyQPNameToSet() {
        HttpRequestMetaData requestMeta = assumeRequestMeta();
        expectedException.expect(IllegalArgumentException.class);
        requestMeta.setQueryParameter("", "foo");
    }

    @Test
    public void nullQPValueToSet() {
        HttpRequestMetaData requestMeta = assumeRequestMeta();
        expectedException.expect(IllegalArgumentException.class);
        requestMeta.setQueryParameter("foo", null);
    }

    @Test
    public void nullCookieName() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addCookie(null, "foo");
    }

    @Test
    public void emptyCookieName() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addCookie("", "");
    }

    @Test
    public void nullCookieValue() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addCookie("foo", null);
    }

    @Test
    public void nullSetCookieName() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addSetCookie(null, "foo");
    }

    @Test
    public void emptySetCookieName() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addSetCookie("", "");
    }

    @Test
    public void nullSetCookieValue() {
        expectedException.expect(IllegalArgumentException.class);
        metaData.addSetCookie("foo", null);
    }

    private HttpRequestMetaData assumeRequestMeta() {
        assumeThat("Test not applicable for response.", metaData, is(instanceOf(HttpRequestMetaData.class)));
        return (HttpRequestMetaData) metaData;
    }
}
