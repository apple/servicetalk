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
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.data.jackson.jersey.resources.PublisherJsonResources;
import io.servicetalk.data.jackson.jersey.resources.SingleJsonResources;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.data.jackson.jersey.ServiceTalkJacksonSerializerFeature.ST_JSON_FEATURE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.glassfish.jersey.internal.InternalProperties.JSON_FEATURE;

public abstract class AbstractStreamingJsonResourcesTest extends AbstractJerseyStreamingHttpServiceTest {

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    SingleJsonResources.class,
                    PublisherJsonResources.class
            ));
        }

        @Override
        public Map<String, Object> getProperties() {
            return singletonMap(JSON_FEATURE, ST_JSON_FEATURE);
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonMap(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonMap("/map", OK));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonMapResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonMap("/map-response", ACCEPTED));
    }

    private void testPostJsonMap(final String path, final HttpResponseStatus expectedStatus) {
        sendAndAssertResponse(post(path, "{\"foo\":\"bar\"}", APPLICATION_JSON),
                expectedStatus, APPLICATION_JSON, jsonEquals("{\"got\":{\"foo\":\"bar\"}}"), __ -> null);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonMapFailure(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonMapFailure("/map"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonMapResponseFailure(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonMapFailure("/map-response"));
    }

    private void testPostJsonMapFailure(final String path) {
        sendAndAssertNoResponse(post(path + "?fail=true", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postBrokenJsonMap(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostBrokenJsonMap("/map"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postBrokenJsonMapResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostBrokenJsonMap("/map-response"));
    }

    private void testPostBrokenJsonMap(final String path) {
        sendAndAssertStatusOnly(post(path, "{key:789}", APPLICATION_JSON), BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonPojo(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonPojo("/pojo", OK));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonPojoResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonPojo("/pojo-response", ACCEPTED));
    }

    private void testPostJsonPojo(final String path, final HttpResponseStatus expectedStatus) {
        sendAndAssertResponse(post(path, "{\"aString\":\"foo\",\"anInt\":123}", APPLICATION_JSON),
                expectedStatus, APPLICATION_JSON, jsonEquals("{\"aString\":\"foox\",\"anInt\":124}"), __ -> null);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonPojoFailure(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonPojoFailure("/pojo"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postJsonPojoResponseFailure(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostJsonPojoFailure("/pojo-response"));
    }

    private void testPostJsonPojoFailure(final String path) {
        sendAndAssertNoResponse(post(path + "?fail=true", "{\"aString\":\"foo\",\"anInt\":123}", APPLICATION_JSON),
                INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postBrokenJsonPojo(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostBrokenJsonPojo("/pojo"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postBrokenJsonPojoResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostBrokenJsonPojo("/pojo-response"));
    }

    private void testPostBrokenJsonPojo(final String path) {
        sendAndAssertStatusOnly(post(path, "{key:789}", APPLICATION_JSON), BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postInvalidJsonPojo(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostInvalidJsonPojo("/pojo"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postInvalidJsonPojoResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPostInvalidJsonPojo("/pojo-response"));
    }

    private void testPostInvalidJsonPojo(final String path) {
        sendAndAssertStatusOnly(post(path, "{\"foo\":\"bar\"}", APPLICATION_JSON), BAD_REQUEST);
    }
}
