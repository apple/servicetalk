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

import io.servicetalk.data.protobuf.jersey.resources.PojoProtobufResources;
import io.servicetalk.data.protobuf.jersey.resources.SingleProtobufResources;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest;
import io.servicetalk.tests.helloworld.HelloReply;
import io.servicetalk.tests.helloworld.HelloRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.data.protobuf.jersey.ServiceTalkProtobufSerializerFeature.PROTOBUF_FEATURE;
import static io.servicetalk.data.protobuf.jersey.ServiceTalkProtobufSerializerFeature.ST_PROTOBUF_FEATURE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_PROTOBUF;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.copyOfRange;
import static java.util.Collections.singletonMap;
import static org.hamcrest.Matchers.equalTo;

abstract class AbstractStreamingProtobufResourcesTest extends AbstractJerseyStreamingHttpServiceTest {
    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(Arrays.asList(
                    PojoProtobufResources.class,
                    SingleProtobufResources.class));
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

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMap(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMap("/map", OK));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostMap("/map-response", ACCEPTED));
    }

    private void testPostMap(final String path, final HttpResponseStatus expectedStatus) {
        sendAndAssertResponse(post(path, HelloRequest.newBuilder().setName("world").build().toByteArray(),
                        APPLICATION_PROTOBUF), expectedStatus, APPLICATION_PROTOBUF,
                equalTo(new String(
                        HelloReply.newBuilder().setMessage("hello world").build().toByteArray(), UTF_8)), __ -> null);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapFailure(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testMapFailure("/map"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postMapResponseFailure(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testMapFailure("/map-response"));
    }

    private void testMapFailure(final String path) {
        sendAndAssertNoResponse(post(path + "?fail=true",
                        HelloRequest.newBuilder().setName("world").build().toByteArray(), APPLICATION_PROTOBUF),
                INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postBrokenMap(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostBrokenMap("/map"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postBrokenMapResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostBrokenMap("/map-response"));
    }

    private void testPostBrokenMap(final String path) {
        byte[] req = HelloRequest.newBuilder().setName("world").build().toByteArray();
        sendAndAssertStatusOnly(post(path, copyOfRange(req, 0, req.length - 1), APPLICATION_PROTOBUF), BAD_REQUEST);
    }
}
