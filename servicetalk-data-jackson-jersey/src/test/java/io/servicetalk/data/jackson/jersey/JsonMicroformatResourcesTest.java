/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.data.jackson.jersey.resources.JsonMicroformatResources;
import io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.data.jackson.jersey.ServiceTalkJacksonSerializerFeature.ST_JSON_FEATURE;
import static io.servicetalk.data.jackson.jersey.resources.JsonMicroformatResources.APPLICATION_VND_INPUT_JSON;
import static io.servicetalk.data.jackson.jersey.resources.JsonMicroformatResources.APPLICATION_VND_OUTPUT_JSON;
import static io.servicetalk.data.jackson.jersey.resources.JsonMicroformatResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.glassfish.jersey.internal.InternalProperties.JSON_FEATURE;

class JsonMicroformatResourcesTest extends AbstractJerseyStreamingHttpServiceTest {

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return singleton(JsonMicroformatResources.class);
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
    void postJsonMicroformat(RouterApi api) throws Exception {
        setUp(api);
        sendAndAssertNoResponse(post(PATH, "{\"foo\":\"bar\"}", APPLICATION_JSON), UNSUPPORTED_MEDIA_TYPE);

        sendAndAssertResponse(post(PATH, "{\"foo\":\"bar\"}", APPLICATION_VND_INPUT_JSON),
                OK, newAsciiString(APPLICATION_VND_OUTPUT_JSON), jsonEquals("{\"got\":{\"foo\":\"bar\"}}"), __ -> null);
    }
}
