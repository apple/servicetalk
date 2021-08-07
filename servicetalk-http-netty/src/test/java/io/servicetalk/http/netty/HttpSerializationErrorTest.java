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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.http.api.HttpStreamingSerializerDeserializer;
import io.servicetalk.transport.api.ServerContext;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.Map;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpSerializers.jsonSerializer;
import static io.servicetalk.http.api.HttpSerializers.jsonStreamingSerializer;
import static io.servicetalk.http.netty.HttpTestExecutionStrategy.CACHED;
import static io.servicetalk.http.netty.HttpTestExecutionStrategy.NO_OFFLOAD;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Deprecated
class HttpSerializationErrorTest {
    private HttpExecutionStrategy serverExecutionStrategy;

    static Collection<HttpTestExecutionStrategy> executors() {
        return asList(NO_OFFLOAD, CACHED);
    }

    @AfterEach
    void teardown() throws Exception {
        Executor executor = serverExecutionStrategy.executor();
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void serializationMapThrowsPropagatesToClient(HttpTestExecutionStrategy serverStrategy) throws Exception {
        serverExecutionStrategy = serverStrategy.executorSupplier.get();
        TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() { };
        HttpSerializerDeserializer<Map<String, Object>> httpSerializer =
                jsonSerializer(JACKSON.serializerDeserializer(mapType));
        HttpStreamingSerializerDeserializer<Map<String, Object>> httpStreamingSerializer =
                jsonStreamingSerializer(JACKSON.streamingSerializerDeserializer(mapType));
        try (ServerContext srv = HttpServers.forAddress(localAddress(0))
                .executionStrategy(serverExecutionStrategy)
                // We build an aggregated service, but convert to/from the streaming API so that we can easily throw
                // and exception when the entire request is available and follow the control flow that was previously
                // hanging.
                .listenAndAwait((ctx, request, responseFactory) ->
                            responseFactory.ok().toStreamingResponse().payloadBody(
                            request.toStreamingRequest().payloadBody(httpStreamingSerializer).map(result -> {
                                throw DELIBERATE_EXCEPTION;
                            }), httpStreamingSerializer).toResponse());
             BlockingHttpClient clt = HttpClients.forSingleAddress(serverHostAndPort(srv)).buildBlocking()) {

            HttpResponse resp = clt.request(clt.post("/foo").payloadBody(emptyMap(), httpSerializer));
            assertEquals(INTERNAL_SERVER_ERROR, resp.status());
        }
    }
}
