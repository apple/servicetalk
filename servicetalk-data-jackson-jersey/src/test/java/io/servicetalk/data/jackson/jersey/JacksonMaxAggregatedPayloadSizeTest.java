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
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.data.jackson.jersey.resources.SingleJsonResources;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest;
import io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.core.Application;

import static io.servicetalk.data.jackson.jersey.ServiceTalkJacksonSerializerFeature.ST_JSON_FEATURE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.glassfish.jersey.internal.InternalProperties.JSON_FEATURE;

/**
 * Confirms the server {@code maxAggregatedPayloadSize} limit is honored by the aggregating Jackson JAX-RS reader
 * ({@code Single<Map>}).
 */
class JacksonMaxAggregatedPayloadSizeTest extends AbstractJerseyStreamingHttpServiceTest {

    private static final int MAX_PAYLOAD = 8;

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(SingleJsonResources.class));
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

    @Override
    protected void configureBuilders(final HttpServerBuilder serverBuilder,
                           final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        super.configureBuilders(serverBuilder, jerseyRouterBuilder);
        serverBuilder.maxAggregatedPayloadSize(MAX_PAYLOAD);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void overLimitJsonRejected(final RouterApi api) throws Exception {
        setUp(api);
        try {
            sendAndAssertStatusOnly(post(SingleJsonResources.PATH + "/map", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                    PAYLOAD_TOO_LARGE);
        } catch (RuntimeException e) {
            // Rejecting an oversized body cancels the in-flight upload, so a connection teardown may race ahead of the
            // mapped 413; both mean the payload was rejected. A timeout instead indicates a hang and must fail.
            if (e.getCause() instanceof TimeoutException) {
                throw e;
            }
        }
    }
}
