/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.data.protobuf.jersey;

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.router.jersey.TestUtils.ContentReadException;
import io.servicetalk.tests.helloworld.HelloReply;
import io.servicetalk.tests.helloworld.HelloRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_PROTOBUF_VAR_INT;
import static io.servicetalk.data.protobuf.jersey.PublisherProtobufResourcesTest.getRootCause;
import static io.servicetalk.data.protobuf.jersey.resources.SingleProtobufResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_PROTOBUF;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleProtobufResourcesTest extends AbstractStreamingProtobufResourcesTest {
    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapPublisher(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapPublisher("/map-publisher", OK));
    }

    private void testPostMapPublisher(final String path, final HttpResponseStatus expectedStatus) {
        try (ByteArrayOutputStream boas = new ByteArrayOutputStream()) {
            HelloReply.newBuilder().setMessage("hello world").build().writeDelimitedTo(boas);
            sendAndAssertResponse(post(path, HelloRequest.newBuilder().setName("world").build().toByteArray(),
                            APPLICATION_PROTOBUF), expectedStatus, APPLICATION_PROTOBUF_VAR_INT,
                    equalTo(new String(boas.toByteArray(), UTF_8)), __ -> null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapPublisherFail(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapPublisherFail("/map-publisher",
                api != RouterApi.ASYNC_STREAMING && api != RouterApi.BLOCKING_STREAMING));
    }

    private void testPostMapPublisherFail(final String path, final boolean expectInternalServerError) {
        byte[] request = HelloRequest.newBuilder().setName("world1").build().toByteArray();
        if (expectInternalServerError) {
            sendAndAssertNoResponse(post(path + "?fail=true", request,
                    APPLICATION_PROTOBUF), INTERNAL_SERVER_ERROR);
        } else {
            ContentReadException e = assertThrows(ContentReadException.class, () -> sendAndAssertResponse(
                    post(path + "?fail=true", request, APPLICATION_PROTOBUF),
                    // For streaming the headers are sent before the exception is thrown, so OK is expected.
                    OK, APPLICATION_PROTOBUF_VAR_INT, ""));
            // We expect that the response parsing failed because the channel was closed after resp headers sent.
            assertThat(getRootCause(e), instanceOf(IOException.class));
        }
    }
}
