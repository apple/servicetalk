/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.serialization.blocking.streaming;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.examples.http.serialization.PojoResponse;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.HttpServers;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.CREATED;
import static io.servicetalk.http.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;

public final class BlockingPojoStreamingServer {

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        HttpSerializationProvider serializer = jsonSerializer(new JacksonSerializationProvider());
        HttpServers.forPort(8080)
                .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    if (!"/pojos".equals(request.requestTarget())) {
                        response.status(NOT_FOUND)
                                .sendMetaData()
                                .close();
                        return;
                    }
                    if (!POST.equals(request.method())) {
                        response.status(METHOD_NOT_ALLOWED)
                                .addHeader(ALLOW, POST.name())
                                .sendMetaData()
                                .close();
                        return;
                    }

                    BlockingIterable<CreatePojoRequest> values = request
                            .payloadBody(serializer.deserializerFor(CreatePojoRequest.class));

                    response.status(CREATED);
                    try (HttpPayloadWriter<PojoResponse> writer = response.sendMetaData(
                            serializer.serializerFor(PojoResponse.class))) {

                        for (CreatePojoRequest req : values) {
                            writer.write(new PojoResponse(ID_GENERATOR.getAndIncrement(), req.getValue()));
                        }
                    }
                })
                .awaitShutdown();
    }
}
