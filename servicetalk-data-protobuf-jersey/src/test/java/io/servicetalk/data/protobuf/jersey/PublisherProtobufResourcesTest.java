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

import io.servicetalk.data.protobuf.jersey.resources.PublisherProtobufResources;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest;
import io.servicetalk.http.router.jersey.TestUtils.ContentReadException;
import io.servicetalk.tests.helloworld.HelloReply;
import io.servicetalk.tests.helloworld.HelloRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_PROTOBUF;
import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_PROTOBUF_VAR_INT;
import static io.servicetalk.data.protobuf.jersey.ServiceTalkProtobufSerializerFeature.PROTOBUF_FEATURE;
import static io.servicetalk.data.protobuf.jersey.ServiceTalkProtobufSerializerFeature.ST_PROTOBUF_FEATURE;
import static io.servicetalk.data.protobuf.jersey.resources.PublisherProtobufResources.PATH;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublisherProtobufResourcesTest extends AbstractJerseyStreamingHttpServiceTest {
    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(singletonList(PublisherProtobufResources.class));
        }

        @Override
        public Map<String, Object> getProperties() {
            return singletonMap(PROTOBUF_FEATURE, ST_PROTOBUF_FEATURE);
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMap(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMap("/map", OK));
    }

    private void testPostMap(final String path, final HttpResponseStatus expectedStatus) {
        try (ByteArrayOutputStream reqStream = new ByteArrayOutputStream();
             ByteArrayOutputStream respStream = new ByteArrayOutputStream()) {
            HelloRequest.newBuilder().setName("world1").build().writeDelimitedTo(reqStream);
            HelloRequest.newBuilder().setName("world2").build().writeDelimitedTo(reqStream);
            HelloReply.newBuilder().setMessage("hello world1").build().writeDelimitedTo(respStream);
            HelloReply.newBuilder().setMessage("hello world2").build().writeDelimitedTo(respStream);
            sendAndAssertResponse(post(path, reqStream.toByteArray(), APPLICATION_PROTOBUF_VAR_INT), expectedStatus,
                    APPLICATION_PROTOBUF_VAR_INT,
                    equalTo(new String(respStream.toByteArray(), UTF_8)), __ -> null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapFail(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapFail("/map",
                api != RouterApi.ASYNC_STREAMING && api != RouterApi.BLOCKING_STREAMING));
    }

    private void testPostMapFail(final String path, final boolean expectInternalServerError) {
        try (ByteArrayOutputStream reqStream = new ByteArrayOutputStream()) {
            HelloRequest.newBuilder().setName("world1").build().writeDelimitedTo(reqStream);
            HelloRequest.newBuilder().setName("world2").build().writeDelimitedTo(reqStream);
            if (expectInternalServerError) {
                sendAndAssertNoResponse(post(path + "?fail=true", reqStream.toByteArray(),
                                APPLICATION_PROTOBUF_VAR_INT), INTERNAL_SERVER_ERROR);
            } else {
                ContentReadException e = assertThrows(ContentReadException.class, () -> sendAndAssertResponse(
                        post(path + "?fail=true", reqStream.toByteArray(), APPLICATION_PROTOBUF_VAR_INT),
                        // For streaming the headers are sent before the exception is thrown, so OK is expected.
                        OK, APPLICATION_PROTOBUF_VAR_INT, ""));
                // We expect that the response parsing failed because the channel was closed after resp headers sent.
                assertThat(getRootCause(e), instanceOf(IOException.class));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapSingle(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapSingle("/map-single", OK));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapSingleResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapSingle("/map-single-response", ACCEPTED));
    }

    private void testPostMapSingle(final String path, final HttpResponseStatus expectedStatus) {
        try (ByteArrayOutputStream reqStream = new ByteArrayOutputStream()) {
            HelloRequest.newBuilder().setName("world1").build().writeDelimitedTo(reqStream);
            HelloRequest.newBuilder().setName("world2").build().writeDelimitedTo(reqStream);
            sendAndAssertResponse(post(path, reqStream.toByteArray(), APPLICATION_PROTOBUF_VAR_INT), expectedStatus,
                    APPLICATION_PROTOBUF,
                    equalTo(new String(HelloReply.newBuilder().setMessage("hello world1world2")
                            .build().toByteArray(), UTF_8)), __ -> null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapSingleFail(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapSingleFail("/map-single"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapSingleResponseFail(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMapSingleFail("/map-single-response"));
    }

    private void testPostMapSingleFail(final String path) {
        try (ByteArrayOutputStream reqStream = new ByteArrayOutputStream()) {
            HelloRequest.newBuilder().setName("world1").build().writeDelimitedTo(reqStream);
            HelloRequest.newBuilder().setName("world2").build().writeDelimitedTo(reqStream);
            sendAndAssertNoResponse(post(path + "?fail=true", reqStream.toByteArray(),
                    APPLICATION_PROTOBUF_VAR_INT), INTERNAL_SERVER_ERROR);
        } catch (Throwable e) {
            // The connection may close before the response comes in due to race in error handling.
            assertThat(getRootCause(e), instanceOf(IOException.class));
        }
    }

    static Throwable getRootCause(Throwable prevCause) {
        Throwable cause = prevCause.getCause();
        while (cause != null) {
            prevCause = cause;
            cause = cause.getCause();
        }
        return prevCause;
    }
}
